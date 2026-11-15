package com.ryuseilabs.pleostest

import com.ryuseilabs.pleostest.course.ArrivalJudge
import com.ryuseilabs.pleostest.course.CourseEvent
import com.ryuseilabs.pleostest.course.DemoCourses
import com.ryuseilabs.pleostest.course.Fix
import com.ryuseilabs.pleostest.course.ReplayPath
import com.ryuseilabs.pleostest.course.Source
import com.ryuseilabs.pleostest.course.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseLogicTest {
    private val course = DemoCourses.pangyoToGangnam
    private val full get() = listOf(course.start) + course.points

    @Test
    fun replayThroughAllPointsStampsInOrderThenCompletes() {
        val judge = ArrivalJudge(course, Source.REPLAY)
        val events = ReplayPath.sample(full, stepMeters = 100.0).flatMap { judge.onFix(it) }

        val stamps = events.filterIsInstance<CourseEvent.Stamped>()
        assertEquals(listOf(0, 1), stamps.map { it.index })
        val done = events.filterIsInstance<CourseEvent.Completed>().single()
        val straight = full.zipWithNext().sumOf { (a, b) -> distanceMeters(a.lat, a.lng, b.lat, b.lng) }
        assertEquals(straight, done.distanceMeters, straight * 0.01) // within 1%
        assertTrue(judge.finished)
    }

    @Test
    fun skippingAheadDoesNotStampLaterPoint() {
        val judge = ArrivalJudge(course, Source.REAL)
        val dest = course.points.last()
        // Jump straight to the destination without passing waypoint 1.
        val events = judge.onFix(Fix(dest.lat, dest.lng, 0))
        assertTrue(events.isEmpty())
        assertTrue(!judge.finished)
    }

    @Test
    fun outsideRadiusDoesNotStamp() {
        val judge = ArrivalJudge(course, Source.REAL, radiusMeters = 50.0)
        val p = course.points[0]
        // ~110 m north of the waypoint
        assertTrue(judge.onFix(Fix(p.lat + 0.001, p.lng, 0)).isEmpty())
        assertEquals(1, judge.onFix(Fix(p.lat + 0.0003, p.lng, 1000)).size) // ~33 m
    }

    @Test
    fun repeatedFixesAtSamePointStampOnce() {
        val judge = ArrivalJudge(course, Source.REAL)
        val p = course.points[0]
        val first = judge.onFix(Fix(p.lat, p.lng, 0))
        val second = judge.onFix(Fix(p.lat, p.lng, 1000))
        assertEquals(1, first.size)
        assertTrue(second.isEmpty())
    }

    @Test
    fun sampleSpacingIsBoundedByStep() {
        val fixes = ReplayPath.sample(full, stepMeters = 200.0)
        val maxGap = fixes.zipWithNext().maxOf { (a, b) -> distanceMeters(a.lat, a.lng, b.lat, b.lng) }
        assertTrue("max gap $maxGap", maxGap <= 200.0 + 1.0)
        assertEquals(full.first().lat, fixes.first().lat, 1e-9)
        assertEquals(full.last().lat, fixes.last().lat, 1e-9)
    }
}
