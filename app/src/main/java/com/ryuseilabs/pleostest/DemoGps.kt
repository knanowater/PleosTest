package com.ryuseilabs.pleostest

import android.os.Handler
import android.os.Looper
import com.ryuseilabs.pleostest.course.CoursePoint
import com.ryuseilabs.pleostest.course.DemoGpsScheduler
import com.ryuseilabs.pleostest.course.DemoGpsSource
import com.ryuseilabs.pleostest.course.Fix

/**
 * 앱 어디서나 쓰는 가짜 GPS. 개발자 메뉴를 열지 않고 코드로 위치를 바꾼다.
 *
 *     DemoGps.moveTo(37.4127, 127.0947)                    // 한 점으로 이동
 *     DemoGps.play(listOf(start, a, b)) { /* 도착 */ }       // 경로를 따라 모의주행
 *     DemoGps.stop()
 *
 * 좌표는 앱의 위치 판정(ArrivalJudge)에 주입된다. 내비 앱의 지도/위치는 움직이지 않는다.
 * (내비 FakeGPS는 내비 앱 내부 기능이라 외부 앱에서 호출할 공개 API가 없다. docs/fake-gps.md 참고)
 */
object DemoGps {
    private val main = Handler(Looper.getMainLooper())

    private val source = DemoGpsSource(object : DemoGpsScheduler {
        override fun post(delayMs: Long, task: Runnable) {
            if (delayMs <= 0) main.post(task) else main.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) = main.removeCallbacks(task)
    })

    val last: Fix? get() = source.last
    val isPlaying: Boolean get() = source.isPlaying

    fun addListener(listener: (Fix) -> Unit) = source.addListener(listener)
    fun removeListener(listener: (Fix) -> Unit) = source.removeListener(listener)

    fun moveTo(lat: Double, lng: Double): Fix = source.moveTo(lat, lng)

    fun play(
        points: List<CoursePoint>,
        stepMeters: Double = 150.0,
        emitIntervalMs: Long = 50,
        onFinished: (() -> Unit)? = null,
    ) = source.play(points, stepMeters, emitIntervalMs, onFinished = onFinished)

    fun stop() = source.stop()
}
