package com.ryuseilabs.pleostest

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ryuseilabs.pleostest.course.ArrivalJudge
import com.ryuseilabs.pleostest.course.BookCourse
import com.ryuseilabs.pleostest.course.Books
import com.ryuseilabs.pleostest.course.BookStore
import com.ryuseilabs.pleostest.course.Chapter
import com.ryuseilabs.pleostest.course.CourseEvent
import com.ryuseilabs.pleostest.course.Fix
import com.ryuseilabs.pleostest.course.Source
import com.ryuseilabs.pleostest.course.StampSource
import com.ryuseilabs.pleostest.R
import com.ryuseilabs.pleostest.ui.ButtonH
import com.ryuseilabs.pleostest.ui.badge
import com.ryuseilabs.pleostest.ui.icon
import com.ryuseilabs.pleostest.ui.PaperDrawable
import com.ryuseilabs.pleostest.ui.Palette
import com.ryuseilabs.pleostest.ui.SealView
import com.ryuseilabs.pleostest.ui.Type
import com.ryuseilabs.pleostest.ui.applyType
import com.ryuseilabs.pleostest.ui.circle
import com.ryuseilabs.pleostest.ui.isWide
import com.ryuseilabs.pleostest.ui.pill
import com.ryuseilabs.pleostest.ui.rounded
import com.ryuseilabs.pleostest.ui.card
import com.ryuseilabs.pleostest.ui.dp
import com.ryuseilabs.pleostest.ui.insetParams
import com.ryuseilabs.pleostest.ui.primaryButton
import com.ryuseilabs.pleostest.ui.secondaryButton
import com.ryuseilabs.pleostest.ui.topBar
import com.ryuseilabs.pleostest.ui.uiText

/**
 * 주행 화면 — 완주 흐름을 보여주고 실제 주행 세션을 제어한다.
 * 실제 주행의 내비 연결과 도착 처리는 DriveService가 담당하므로, 화면을 닫아도 도장이 저장된다.
 */
class DriveActivity : AppCompatActivity() {

    private lateinit var bookId: String
    private lateinit var course: BookCourse
    private lateinit var store: BookStore
    private lateinit var status: TextView
    private lateinit var primary: android.widget.Button
    private lateinit var secondary: android.widget.Button
    private lateinit var stopDrive: TextView
    private lateinit var chapterRows: LinearLayout
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private val demoGpsListener: (Fix) -> Unit = { feed(it) }
    private var judge: ArrivalJudge? = null
    private var chapterIndex = 0
    private var awaitingNext = false
    private var activeDrive = false
    private var finished = false

    private val driveStateListener: (DriveSnapshot) -> Unit = { snapshot ->
        if (snapshot.bookId == bookId && snapshot.courseId == course.id) {
            applySnapshot(snapshot)
        } else if (!snapshot.active) {
            activeDrive = false
            awaitingNext = false
            finished = snapshot.finished
            renderRows()
            updatePrimary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.init(this)
        bookId = intent.getStringExtra("bookId")!!
        course = Books.course(bookId, intent.getStringExtra("courseId"))
            ?: run { finish(); return }
        store = BookStore(getSharedPreferences("stampbook", MODE_PRIVATE))
        chapterIndex = store.resumeChapterIndex(bookId, course)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
        }

        // 상단바 (뒤로가기 — 실제 주행은 계속된다)
        root.addView(topBar("드라이브", course.title) { finish() })

        // 구간 진행 카드
        val info = card()
        val infoPaper = PaperDrawable(this)           // 여정 노트 페이지 (점 격자 + 쌓인 종이)
        info.background = infoPaper
        info.setPadding(dp(24f), dp(22f), dp(24f) + dp(infoPaper.thickness), dp(22f) + dp(infoPaper.thickness))
        info.addView(pill("ROUTE", Palette.mintText, Palette.mintSoft),
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10f) })
        info.addView(uiText(course.title, Type.TitleS, Palette.text, bold = true))
        chapterRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(chapterRows, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })

        // 상태 카드
        val statusCard = card()
        val statusPaper = PaperDrawable(this, ruled = false)
        statusCard.background = statusPaper
        statusCard.setPadding(dp(24f), dp(22f), dp(24f) + dp(statusPaper.thickness), dp(22f) + dp(statusPaper.thickness))
        statusCard.addView(pill("STATUS", Palette.blue, Palette.blueSoft),
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(12f) })
        status = uiText("준비되었습니다.", Type.BodyL, Palette.text)
        statusCard.addView(status)

        // 컨트롤 영역
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        primary = primaryButton("출발") { onPrimary() }
        bottom.addView(primary, LinearLayout.LayoutParams(-1, dp(ButtonH.Large)))
        secondary = secondaryButton("데모 리플레이 (시연용)") { startReplay() }
        bottom.addView(secondary, LinearLayout.LayoutParams(-1, dp(ButtonH.Medium)).apply { topMargin = dp(12f) })
        stopDrive = uiText("주행 기록 중지", Type.LabelM, Palette.textSub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, dp(4f))
            setOnClickListener { confirmStopTracking() }
            visibility = View.GONE
        }
        bottom.addView(stopDrive)

        if (isWide()) {
            val cols = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val leftScroll = ScrollView(this).apply { addView(info) }
            cols.addView(leftScroll, LinearLayout.LayoutParams(0, -1, 5f).apply {
                leftMargin = dp(24f); rightMargin = dp(10f); topMargin = dp(6f); bottomMargin = dp(18f)
            })
            val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            right.addView(statusCard, LinearLayout.LayoutParams(-1, -2))
            right.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
            cols.addView(right, LinearLayout.LayoutParams(0, -1, 5f).apply {
                leftMargin = dp(10f); rightMargin = dp(24f); topMargin = dp(6f); bottomMargin = dp(18f)
            })
            root.addView(cols, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val scroll = ScrollView(this).apply { isFillViewport = true }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            inner.addView(info, insetParams(top = 6f))
            inner.addView(statusCard, insetParams(top = 14f))
            scroll.addView(inner)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            bottom.setPadding(dp(24f), dp(12f), dp(24f), dp(16f))
            bottom.setBackgroundColor(Palette.surface)
            root.addView(bottom)
        }

        setContentView(root)
        DriveHub.subscribe(driveStateListener)
        DemoGps.addListener(demoGpsListener)
        DriveService.sync(this)
        renderRows()
        updatePrimary()
        setStatus("준비되었습니다. '출발'을 누르면 ${chapter(chapterIndex).title}이 안내됩니다.")
    }

    override fun onResume() {
        super.onResume()
        DriveService.sync(this)
        renderRows()
        updatePrimary()
    }

    override fun onDestroy() {
        DemoGps.stop()
        DemoGps.removeListener(demoGpsListener)
        DriveHub.unsubscribe(driveStateListener)
        super.onDestroy()
    }

    private fun chapter(i: Int): Chapter = course.chapters[i]

    private fun applySnapshot(snapshot: DriveSnapshot) {
        activeDrive = snapshot.active
        awaitingNext = snapshot.awaitingNext
        finished = snapshot.finished
        chapterIndex = snapshot.chapterIndex.coerceIn(0, course.chapters.lastIndex)
        status.text = snapshot.status
        stopDrive.visibility = if (activeDrive) View.VISIBLE else View.GONE
        renderRows()
        updatePrimary()
    }

    private fun onPrimary() {
        if (finished) return
        if (activeDrive && !awaitingNext) {
            setStatus("이미 주행 기록 중입니다. 도착을 기다리고 있습니다.")
            return
        }
        withLocationPermission {
            if (awaitingNext) {
                DriveService.next(this)
            } else {
                DriveService.start(this, bookId, course.id)
            }
        }
    }

    // FusedLocation 권한 (pleos.car.permission.FUSED_LOCATION). 거부돼도 내비 도착 이벤트로 계속 동작한다.
    private var afterLocationPermission: (() -> Unit)? = null
    private val locationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "위치 권한이 없어 내비 도착 이벤트로 기록합니다.", Toast.LENGTH_SHORT).show()
        }
        afterLocationPermission?.invoke()
        afterLocationPermission = null
    }

    private fun withLocationPermission(action: () -> Unit) {
        val perm = "pleos.car.permission.FUSED_LOCATION"
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, perm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
            return
        }
        afterLocationPermission = action
        runCatching { locationPermission.launch(perm) }.onFailure {
            afterLocationPermission = null
            action()
        }
    }

    private fun confirmStopTracking() {
        if (!activeDrive) return
        AlertDialog.Builder(this)
            .setTitle("주행 기록 중지")
            .setMessage("도착 감지와 도장 기록만 중지합니다. 이미 찍힌 도장은 삭제되지 않습니다.")
            .setPositiveButton("중지") { _, _ ->
                DriveService.stop(this)
                activeDrive = false
                awaitingNext = false
                setStatus("주행 기록을 중지했습니다.")
                updatePrimary()
                stopDrive.visibility = View.GONE
            }
            .setNegativeButton("유지", null)
            .show()
    }

    private fun renderRows() {
        chapterRows.removeAllViews()
        course.chapters.forEachIndexed { index, ch ->
            val destinationStamp = course.stamps.firstOrNull {
                it.source == StampSource.DESTINATION && it.chapterIndex == index
            }
            val done = destinationStamp != null && store.isStampDone(bookId, course.id, destinationStamp.id)
            val current = index == chapterIndex && !finished && (activeDrive || !done)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(11f), 0, dp(11f))
            }
            row.addView(SealView(this).apply {
                this.done = done; number = index + 1; ink = Palette.mint
                emptyColor = if (current) Palette.blue else Palette.textDim
                tilt = if (index % 2 == 0) -8f else 7f
            }, LinearLayout.LayoutParams(dp(60f), dp(60f)))
            row.addView(TextView(this).apply {
                text = ch.title
                applyType(Type.BodyM)
                setTextColor(if (done) Palette.mintText else if (current) Palette.text else Palette.textDim)
                setTypeface(typeface, if (current) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setPadding(dp(16f), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            chapterRows.addView(row)
        }
    }

    private fun updatePrimary() {
        if (finished) {
            primary.text = "COMPLETE 증빙 완료"
            primary.isEnabled = false
            primary.alpha = 0.5f
            secondary.isEnabled = false
            secondary.alpha = 0.4f
            return
        }
        val nextTitle = chapter(chapterIndex).title
        when {
            activeDrive && awaitingNext -> {
                primary.text = "다음 구간 출발 — $nextTitle"
                primary.isEnabled = true
                primary.alpha = 1f
                secondary.isEnabled = false
                secondary.alpha = 0.4f
            }
            activeDrive -> {
                primary.text = "주행 기록 중 — 도착 대기"
                primary.isEnabled = false
                primary.alpha = 0.7f
                secondary.isEnabled = false
                secondary.alpha = 0.4f
            }
            else -> {
                primary.text = "출발 — $nextTitle"
                primary.isEnabled = true
                primary.alpha = 1f
                secondary.isEnabled = true
                secondary.alpha = 1f
            }
        }
    }

    private fun setStatus(message: String) {
        status.text = message
    }

    // --- 데모 리플레이 (표시 화면이 있는 시연용; 실제 주행 세션과 분리) ---

    private fun startReplay() {
        if (activeDrive) {
            Toast.makeText(this, "실제 주행 기록이 진행 중입니다. 먼저 중지하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (finished) return
        stopReplay()
        val ch = chapter(chapterIndex)
        val replayCourse = ch.toCourse()
        judge = ArrivalJudge(replayCourse, Source.REPLAY)
        // 구간 경로(출발점 → 도착점)를 150m 간격 좌표로 50ms마다 주입한다 (DemoGps)
        DemoGps.play(listOf(replayCourse.start) + replayCourse.points)
        setStatus("데모 리플레이 — ${ch.title}")
    }

    private fun feed(fix: Fix) {
        if (finished) return
        val currentJudge = judge ?: return
        currentJudge.onFix(fix).forEach { event ->
            when (event) {
                is CourseEvent.Stamped -> {
                    val pointIndex = event.index
                    val ch = chapter(chapterIndex)
                    if (pointIndex < ch.waypoints.size) {
                        markReplayStamps(StampSource.WAYPOINT, Source.REPLAY, waypointIndex = pointIndex)
                    } else {
                        markReplayStamps(StampSource.DESTINATION, Source.REPLAY, waypointIndex = -1)
                    }
                }
                is CourseEvent.Completed -> replayChapterCompleted()
            }
        }
    }

    private fun markReplayStamps(stampSource: StampSource, source: Source, waypointIndex: Int) {
        course.stamps
            .filter {
                it.source == stampSource &&
                    it.chapterIndex == chapterIndex &&
                    (stampSource != StampSource.WAYPOINT || it.waypointIndex == waypointIndex)
            }
            .forEach { stamp ->
                if (!store.isStampDone(bookId, course.id, stamp.id)) {
                    store.markStamp(bookId, course.id, stamp.id, source)
                }
            }
        renderRows()
    }

    private fun replayChapterCompleted() {
        markReplayStamps(StampSource.DESTINATION, Source.REPLAY, waypointIndex = -1)
        val last = chapterIndex >= course.chapters.lastIndex
        if (last) {
            finishReplay()
            return
        }
        chapterIndex += 1
        awaitingNext = true
        setStatus("${chapter(chapterIndex - 1).title} 완료 — 도장 획득! 2초 후 다음 구간 리플레이를 시작합니다.")
        renderRows()
        ui.postDelayed({ if (!finished) startReplay() }, 2000)
    }

    private fun finishReplay() {
        finished = true
        stopReplay()
        store.markAllDone(bookId, course.id)
        val intent = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("celebrate_course", course.id)
        startActivity(intent)
        finish()
    }

    private fun stopReplay() {
        DemoGps.stop()
        judge = null
    }
}
