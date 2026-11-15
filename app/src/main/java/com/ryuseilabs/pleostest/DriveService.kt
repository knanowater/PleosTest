package com.ryuseilabs.pleostest

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ryuseilabs.pleostest.course.ArrivalJudge
import com.ryuseilabs.pleostest.course.BookCourse
import com.ryuseilabs.pleostest.course.CourseEvent
import com.ryuseilabs.pleostest.course.Fix
import com.ryuseilabs.pleostest.course.BookStore
import com.ryuseilabs.pleostest.course.Books
import com.ryuseilabs.pleostest.course.Chapter
import com.ryuseilabs.pleostest.course.Source
import com.ryuseilabs.pleostest.course.StampSource

/**
 * Owns the active real drive. The in-car UI may be hidden, so stamps must be saved without it.
 */
class DriveService : Service() {

    private val sessionStore by lazy { DriveSessionStore(this) }
    private val bookStore by lazy { BookStore(getSharedPreferences("stampbook", MODE_PRIVATE)) }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private var navi: NaviHelper? = null
    private var session = DriveSession()
    private var course: BookCourse? = null

    // FusedLocation 좌표 기반 도착 판정(주 경로). NaviHelper 도착 이벤트는 교차 검증·폴백.
    private val location by lazy { FusedLocationClient(applicationContext) }
    private var judge: ArrivalJudge? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            setStatus("내비 안내 시작 — 안전 운전하세요.")
        }

        override fun onRoutingCompleted() = Unit

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            val waypointIndex = when (info.index) {
                WaypointIndex.FIRST -> 0
                WaypointIndex.SECOND -> 1
                else -> 0
            }
            main.post {
                Log.i("RoadStampDrive", "waypoint $waypointIndex ${info.name}")
                markStamps(null, waypointIndex, Source.REAL)
            }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            main.post {
                Log.i("RoadStampDrive", "destination ${info.name}")
                handleDestinationArrived()
            }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = Unit

        override fun onError(code: NaviErrorCode) {
            setStatus("내비 오류: $code")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        restoreSession()
        enterForeground(ongoingNotification(session.statusOr("주행 기록 중 — 도착을 기다리고 있습니다")))

        if (intent == null) {
            // System-created restart: reconnect the listener without re-requesting a route.
            if (session.active) {
                startLocation()
                ensureNaviConnected { publishSession() }
            } else {
                publishSession()
            }
            return START_STICKY
        }

        when (intent.action ?: ACTION_SYNC) {
            ACTION_START -> startOrResume(intent)
            ACTION_NEXT -> nextChapter()
            ACTION_STOP -> stopTracking()
            ACTION_SYNC -> publishSession()
            else -> publishSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseNavi()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun restoreSession() {
        session = sessionStore.load()
        course = resolveCourse(session)
    }

    private fun startOrResume(intent: Intent) {
        val bookId = intent.getStringExtra("bookId")
        val courseId = intent.getStringExtra("courseId")
        if (bookId.isNullOrBlank() || courseId.isNullOrBlank()) {
            publishSession()
            return
        }

        val requestedCourse = Books.course(bookId, courseId)
        if (requestedCourse == null || !requestedCourse.available) {
            setStatus("지정 코스를 찾을 수 없거나 준비 중입니다.")
            return
        }

        val existing = sessionStore.load()
        if (existing.active && existing.bookId == bookId && existing.courseId == courseId && !existing.finished) {
            session = existing
            course = requestedCourse
            startLocation()
            setStatus(
                if (session.awaitingNext) {
                    "다음 구간 출발을 누르세요."
                } else {
                    "주행 기록 중 — 도착을 기다리고 있습니다."
                }
            )
            return
        }

        course = requestedCourse
        val chapter = bookStore.resumeChapterIndex(bookId, requestedCourse)
        session = sessionStore.start(bookId, courseId, chapter)
        startChapterRoute(chapter)
    }

    private fun nextChapter() {
        restoreSession()
        if (!session.active || !session.awaitingNext || session.finished) {
            publishSession()
            return
        }
        startChapterRoute(session.chapterIndex)
    }

    private fun startChapterRoute(index: Int) {
        val currentCourse = course
        if (currentCourse == null) {
            setStatus("코스 정보를 복원하지 못했습니다.")
            return
        }
        if (index !in currentCourse.chapters.indices) {
            finishCourse()
            return
        }

        val chapter = currentCourse.chapters[index]
        session = session.copy(active = true, chapterIndex = index, awaitingNext = false, finished = false)
        sessionStore.save(session)
        setStatus("${chapter.title} 경로를 설정하고 있습니다.")
        judge = ArrivalJudge(chapter.toCourse(), Source.REAL)
        startLocation()
        ensureNaviConnected {
            requestRoute(chapter)
            setStatus("${chapter.title} 목적지: ${chapter.destination.poiName}\n주행 중에는 조작하지 마세요.")
        }
    }

    private fun startLocation() {
        main.post {
            val ok = location.start(
                onFix = { fix -> main.post { onLocationFix(fix) } },
                onError = { msg -> Log.w("RoadStampDrive", "위치 오류: $msg (내비 도착 이벤트로 계속)") },
            )
            if (!ok) Log.i("RoadStampDrive", "FusedLocation 사용 불가 — 내비 도착 이벤트만 사용")
        }
    }

    /** FusedLocation 좌표 → ArrivalJudge(REAL). 지점/목적지 도착 시 기존 도장 로직으로 연결. */
    private fun onLocationFix(fix: Fix) {
        if (!session.active || session.awaitingNext || session.finished) return
        val currentCourse = course ?: return
        val chapter = currentCourse.chapters.getOrNull(session.chapterIndex) ?: return
        val j = judge ?: ArrivalJudge(chapter.toCourse(), Source.REAL).also { judge = it }
        j.onFix(fix).forEach { event ->
            if (event !is CourseEvent.Stamped) return@forEach
            Log.i("RoadStampDrive", "FusedLocation 도착 판정: ${event.point.name}")
            if (event.index < chapter.waypoints.size) {
                markStamps(null, event.index, Source.REAL)
            } else {
                handleDestinationArrived()
            }
        }
    }

    private fun requestRoute(chapter: Chapter) {
        val destination = chapter.destination
        val request = RouteWithWaypointsRequest(
            destination = RouteInfo(
                latitude = destination.lat,
                longitude = destination.lng,
                poiName = destination.poiName,
                poiId = destination.poiId,
                address = destination.address,
                poiSubId = destination.poiSubId,
            ),
            waypoints = chapter.waypoints.mapIndexed { index, waypoint ->
                RequestWaypointInfo(
                    latitude = waypoint.lat,
                    longitude = waypoint.lng,
                    poiName = waypoint.poiName,
                    waypointIndex = if (index == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                    poiId = waypoint.poiId,
                    address = waypoint.address,
                    poiSubId = waypoint.poiSubId,
                )
            },
        )
        navi?.requestRouteWithWaypoints(request)
    }

    private fun ensureNaviConnected(onReady: () -> Unit) {
        navi?.let { onReady(); return }
        setStatus("내비 초기화 중…")
        val helper = NaviHelper(applicationContext)
        navi = helper
        helper.initialize(
            {
                Log.i("RoadStampDrive", "내비 연결됨 (service)")
                helper.addListener(listener)
                setStatus("내비 연결됨")
                onReady()
            },
            { error ->
                navi = null
                setStatus("내비 연결 실패: $error")
            },
        )
    }

    private fun handleDestinationArrived() {
        // FusedLocation 판정과 내비 이벤트가 둘 다 올 수 있다 — 먼저 온 쪽만 처리 (멱등)
        if (!session.active || session.awaitingNext || session.finished) return
        judge = null
        val currentCourse = course ?: run {
            setStatus("도착했지만 코스 정보를 복원하지 못했습니다.")
            return
        }
        markStamps(session.chapterIndex, -1, Source.REAL)

        if (session.chapterIndex >= currentCourse.chapters.lastIndex) {
            finishCourse()
            return
        }

        val finishedIndex = session.chapterIndex
        val nextIndex = finishedIndex + 1
        session = session.copy(chapterIndex = nextIndex, awaitingNext = true)
        sessionStore.save(session)
        val finishedTitle = currentCourse.chapters[finishedIndex].title
        val nextChapter = currentCourse.chapters[nextIndex]
        setStatus(
            "$finishedTitle 완료 — 도장 획득!\n\n" +
                "다음: ${nextChapter.title}\n" +
                "목적지: ${nextChapter.destination.poiName}\n\n" +
                "다음 구간 출발을 누르세요."
        )
    }

    private fun markStamps(stampIndex: Int?, waypointIndex: Int, source: Source) {
        val currentCourse = course ?: return
        val targetChapter = stampIndex ?: session.chapterIndex
        val stampSource = if (stampIndex == null && waypointIndex >= 0) {
            StampSource.WAYPOINT
        } else {
            StampSource.DESTINATION
        }
        currentCourse.stamps
            .filter { stamp ->
                stamp.source == stampSource &&
                    stamp.chapterIndex == targetChapter &&
                    (stampSource != StampSource.WAYPOINT || stamp.waypointIndex == waypointIndex)
            }
            .forEach { stamp ->
                val bookId = session.bookId ?: return@forEach
                if (!bookStore.isStampDone(bookId, currentCourse.id, stamp.id)) {
                    bookStore.markStamp(bookId, currentCourse.id, stamp.id, source)
                    Log.i("RoadStampDrive", "stamp ${stamp.id} ($stampSource) source=$source")
                }
            }
        publishSession()
    }

    private fun finishCourse() {
        val currentCourse = course ?: return
        val bookId = session.bookId ?: return
        bookStore.markAllDone(bookId, currentCourse.id)
        Log.i("RoadStampDrive", "완주 — 전 구간 완료 (REAL)")
        sessionStore.clear()
        session = DriveSession()
        DriveHub.publish {
            DriveSnapshot(
                active = false,
                bookId = bookId,
                courseId = currentCourse.id,
                chapterIndex = currentCourse.chapters.lastIndex,
                awaitingNext = false,
                finished = true,
                status = "전 구간 완료 — COMPLETE 도장을 받았습니다.",
            )
        }
        releaseNavi()
        postCompletionNotification(currentCourse)
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTracking() {
        releaseNavi()
        sessionStore.clear()
        session = DriveSession()
        course = null
        setStatus("주행 기록을 중지했습니다.")
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseNavi() {
        location.stop()
        judge = null
        runCatching {
            navi?.removeListener(listener)
            navi?.release()
        }
        navi = null
    }

    private fun resolveCourse(s: DriveSession): BookCourse? =
        s.bookId?.let { bookId -> s.courseId?.let { courseId -> Books.course(bookId, courseId) } }

    private fun setStatus(message: String) {
        Log.i("RoadStampDrive", message.lineSequence().first())
        DriveHub.publish {
            DriveSnapshot(
                active = session.active,
                bookId = session.bookId,
                courseId = session.courseId,
                chapterIndex = session.chapterIndex,
                awaitingNext = session.awaitingNext,
                finished = session.finished,
                status = message,
            )
        }
        updateNotification(message.lineSequence().first().take(80))
    }

    private fun publishSession() {
        DriveHub.publish {
            DriveSnapshot(
                active = session.active,
                bookId = session.bookId,
                courseId = session.courseId,
                chapterIndex = session.chapterIndex,
                awaitingNext = session.awaitingNext,
                finished = session.finished,
                status = session.statusOr("준비되었습니다."),
            )
        }
        updateNotification(session.statusOr("주행 기록 중").lineSequence().first().take(80))
    }

    private fun DriveSession.statusOr(defaultValue: String): String = defaultValue

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_LOW).apply {
                description = "앱을 열지 않아도 도착 이벤트를 스탬프북에 기록합니다."
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, ongoingNotification(text))
    }

    private fun ongoingNotification(text: String): Notification {
        val intent = Intent(this, DriveActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("로드스탬프 — 주행 기록 중")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun postCompletionNotification(completedCourse: BookCourse) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("celebrate_course", completedCourse.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("로드스탬프 — 완주")
            .setContentText("'${completedCourse.title}' 전 구간 완료")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(COMPLETE_NOTIF_ID, notification)
    }

    companion object {
        private const val CHANNEL = "drive_recording"
        private const val NOTIF_ID = 1001
        private const val COMPLETE_NOTIF_ID = 1002
        private const val ACTION_SYNC = "com.ryuseilabs.pleostest.drive.SYNC"
        private const val ACTION_START = "com.ryuseilabs.pleostest.drive.START"
        private const val ACTION_NEXT = "com.ryuseilabs.pleostest.drive.NEXT"
        private const val ACTION_STOP = "com.ryuseilabs.pleostest.drive.STOP"

        fun start(context: Context, bookId: String, courseId: String) {
            val intent = Intent(context, DriveService::class.java).apply {
                action = ACTION_START
                putExtra("bookId", bookId)
                putExtra("courseId", courseId)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun next(context: Context) {
            val intent = Intent(context, DriveService::class.java).setAction(ACTION_NEXT)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriveService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun sync(context: Context) {
            val intent = Intent(context, DriveService::class.java).setAction(ACTION_SYNC)
            context.startService(intent)
        }
    }
}
