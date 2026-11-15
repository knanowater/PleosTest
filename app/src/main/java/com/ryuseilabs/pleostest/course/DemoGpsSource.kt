package com.ryuseilabs.pleostest.course

import java.util.concurrent.CopyOnWriteArrayList

/** 메인 스레드 스케줄러 추상화. 앱에서는 Handler, JVM 테스트에서는 가짜로 교체한다. */
interface DemoGpsScheduler {
    fun post(delayMs: Long, task: Runnable)
    fun cancel(task: Runnable)
}

/**
 * 가짜 GPS 좌표 소스 (순수 Kotlin).
 *
 * 내비의 개발자 메뉴 FakeGPS는 내비 앱 내부 기능이라 다른 앱에서 호출할 수 없다.
 * 대신 우리 앱의 위치 판정([ArrivalJudge])에 좌표를 코드로 주입할 수 있게 한다.
 *  - [moveTo]: 좌표 한 점을 즉시 주입
 *  - [play]  : 경로를 따라 일정 간격으로 연속 주입 (모의주행)
 *  - [stop]  : 진행 중인 모의주행 중단
 * 모든 호출은 메인 스레드에서 한다.
 */
class DemoGpsSource(
    private val scheduler: DemoGpsScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val listeners = CopyOnWriteArrayList<(Fix) -> Unit>()
    private var generation = 0
    private var pending: Runnable? = null

    /** 마지막으로 주입한 좌표 */
    var last: Fix? = null
        private set

    val isPlaying: Boolean get() = pending != null

    fun addListener(listener: (Fix) -> Unit) {
        if (listener !in listeners) listeners.add(listener)
    }

    fun removeListener(listener: (Fix) -> Unit) {
        listeners.remove(listener)
    }

    /** 좌표 한 점을 즉시 주입한다. 진행 중인 [play]에는 영향을 주지 않는다. */
    fun moveTo(lat: Double, lng: Double, timeMs: Long = clock()): Fix {
        val fix = Fix(lat, lng, timeMs)
        emit(fix)
        return fix
    }

    /**
     * [points]를 이은 경로를 따라 [stepMeters] 간격의 좌표를 [emitIntervalMs]마다 하나씩 주입한다.
     * 좌표의 시각은 [fixIntervalMs] 간격(실제 GPS 1초 주기 흉내)으로 찍힌다.
     * 새로 시작하면 이전 [play]는 중단된다. 끝까지 주입하면 [onFinished]를 부른다.
     */
    fun play(
        points: List<CoursePoint>,
        stepMeters: Double = 150.0,
        emitIntervalMs: Long = 50,
        fixIntervalMs: Long = 1000,
        onFinished: (() -> Unit)? = null,
    ) {
        stop()
        val fixes = ReplayPath.sample(points, stepMeters, clock(), fixIntervalMs)
        val run = generation
        var index = 0
        val task = object : Runnable {
            override fun run() {
                if (run != generation) return
                emit(fixes[index++])
                if (run != generation) return // 리스너가 stop()/play()를 부른 경우
                if (index >= fixes.size) {
                    pending = null
                    onFinished?.invoke()
                } else {
                    scheduler.post(emitIntervalMs, this)
                }
            }
        }
        pending = task
        scheduler.post(0, task)
    }

    fun stop() {
        generation++
        pending?.let { scheduler.cancel(it) }
        pending = null
    }

    private fun emit(fix: Fix) {
        last = fix
        listeners.forEach { it(fix) }
    }
}
