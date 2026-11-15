package com.ryuseilabs.pleostest

import com.ryuseilabs.pleostest.course.ArrivalJudge
import com.ryuseilabs.pleostest.course.CourseEvent
import com.ryuseilabs.pleostest.course.DemoCourses
import com.ryuseilabs.pleostest.course.DemoGpsScheduler
import com.ryuseilabs.pleostest.course.DemoGpsSource
import com.ryuseilabs.pleostest.course.Fix
import com.ryuseilabs.pleostest.course.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시간 대신 큐를 직접 돌리는 가짜 스케줄러 */
private class ManualScheduler : DemoGpsScheduler {
    private val queue = ArrayDeque<Runnable>()
    override fun post(delayMs: Long, task: Runnable) { queue.addLast(task) }
    override fun cancel(task: Runnable) { queue.remove(task) }
    fun runAll(limit: Int = 100_000) {
        var n = 0
        while (queue.isNotEmpty()) {
            check(n++ < limit) { "scheduler did not drain" }
            queue.removeFirst().run()
        }
    }
    fun runOne() { queue.removeFirstOrNull()?.run() }
}

class DemoGpsSourceTest {
    private val course = DemoCourses.pangyoToGangnam
    private val path get() = listOf(course.start) + course.points

    private fun newSource(): Pair<DemoGpsSource, ManualScheduler> {
        val sch = ManualScheduler()
        return DemoGpsSource(sch) { 1_000L } to sch
    }

    @Test
    fun moveToEmitsOneFixImmediatelyAndRemembersIt() {
        val (gps, _) = newSource()
        val got = mutableListOf<Fix>()
        gps.addListener { got += it }

        assertNull(gps.last)
        val fix = gps.moveTo(37.4, 127.1)

        assertEquals(listOf(fix), got)
        assertEquals(fix, gps.last)
    }

    @Test
    fun playEmitsFixesInOrderAndCallsOnFinishedOnce() {
        val (gps, sch) = newSource()
        val got = mutableListOf<Fix>()
        var finished = 0
        gps.addListener { got += it }

        gps.play(path, stepMeters = 500.0) { finished++ }
        assertTrue(gps.isPlaying)
        assertTrue(got.isEmpty()) // 스케줄러가 돌기 전에는 아무것도 주입하지 않는다

        sch.runAll()

        assertTrue(got.size > 2)
        assertEquals(course.start.lat, got.first().lat, 1e-9)
        assertEquals(course.points.last().lat, got.last().lat, 1e-9)
        assertEquals(got.map { it.timeMs }, got.map { it.timeMs }.sorted()) // 시각 오름차순
        assertEquals(1, finished)
        assertFalse(gps.isPlaying)
    }

    @Test
    fun stopHaltsPlayAndSkipsOnFinished() {
        val (gps, sch) = newSource()
        var count = 0
        var finished = 0
        gps.addListener { count++ }

        gps.play(path, stepMeters = 500.0) { finished++ }
        sch.runOne(); sch.runOne()
        gps.stop()
        sch.runAll()

        assertEquals(2, count)
        assertEquals(0, finished)
        assertFalse(gps.isPlaying)
    }

    @Test
    fun listenerMayStopPlayFromInsideTheCallback() {
        val (gps, sch) = newSource()
        var count = 0
        var finished = 0
        gps.addListener { count++; if (count == 3) gps.stop() }

        gps.play(path, stepMeters = 500.0) { finished++ }
        sch.runAll()

        assertEquals(3, count)
        assertEquals(0, finished)
    }

    @Test
    fun startingANewPlayReplacesThePreviousOne() {
        val (gps, sch) = newSource()
        val got = mutableListOf<Fix>()
        gps.addListener { got += it }

        gps.play(path, stepMeters = 500.0)
        sch.runOne()
        gps.play(listOf(course.points[0], course.points[1]), stepMeters = 500.0)
        sch.runAll()

        // 첫 play는 한 점만 나가고, 이후는 두 번째 경로(첫 점 = 백화점)만 나간다
        assertEquals(course.start.lat, got.first().lat, 1e-9)
        assertEquals(course.points[0].lat, got[1].lat, 1e-9)
        assertEquals(course.points[1].lat, got.last().lat, 1e-9)
    }

    @Test
    fun removedListenerNoLongerReceivesFixes() {
        val (gps, _) = newSource()
        var count = 0
        val l: (Fix) -> Unit = { count++ }
        gps.addListener(l)
        gps.moveTo(1.0, 2.0)
        gps.removeListener(l)
        gps.moveTo(3.0, 4.0)
        assertEquals(1, count)
    }

    @Test
    fun injectedFixesDriveTheArrivalJudgeToStampTheWholeCourse() {
        val (gps, sch) = newSource()
        val judge = ArrivalJudge(course, Source.REPLAY)
        val events = mutableListOf<CourseEvent>()
        gps.addListener { events += judge.onFix(it) }

        gps.play(path, stepMeters = 150.0)
        sch.runAll()

        assertEquals(listOf(0, 1), events.filterIsInstance<CourseEvent.Stamped>().map { it.index })
        assertEquals(1, events.filterIsInstance<CourseEvent.Completed>().size)
    }
}
