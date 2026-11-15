package com.ryuseilabs.pleostest.course

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure Kotlin (no Android deps) so it can be unit-tested on the JVM. */

data class Fix(val lat: Double, val lng: Double, val timeMs: Long)

data class CoursePoint(val name: String, val lat: Double, val lng: Double)

/** A 3-point course: [0]=waypoint 1, [1]=waypoint 2 (optional), last=destination. */
data class Course(val name: String, val start: CoursePoint, val points: List<CoursePoint>)

fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/** Where a fix came from. Real = FusedLocation, Replay = demo playback. */
enum class Source { REAL, REPLAY }

sealed class CourseEvent {
    data class Stamped(val index: Int, val point: CoursePoint, val fix: Fix, val source: Source) : CourseEvent()
    data class Completed(val distanceMeters: Double, val elapsedMs: Long, val source: Source) : CourseEvent()
}

/**
 * Judges stamps from a stream of fixes. Points must be reached in order
 * (a later point is never stamped before the earlier ones). Also records the
 * actual path, so the same code serves real driving and demo replay.
 */
class ArrivalJudge(
    private val course: Course,
    private val source: Source,
    private val radiusMeters: Double = 50.0,
) {
    private var next = 0
    private var firstFix: Fix? = null
    private var last: Fix? = null
    private var traveled = 0.0
    val path = mutableListOf<Fix>()
    val finished get() = next >= course.points.size

    fun onFix(fix: Fix): List<CourseEvent> {
        if (finished) return emptyList()
        if (firstFix == null) firstFix = fix
        last?.let { traveled += distanceMeters(it.lat, it.lng, fix.lat, fix.lng) }
        last = fix
        path += fix

        val events = mutableListOf<CourseEvent>()
        val target = course.points[next]
        if (distanceMeters(fix.lat, fix.lng, target.lat, target.lng) <= radiusMeters) {
            events += CourseEvent.Stamped(next, target, fix, source)
            next++
            if (finished) {
                events += CourseEvent.Completed(traveled, fix.timeMs - firstFix!!.timeMs, source)
            }
        }
        return events
    }
}

/** Turns a polyline into evenly spaced fixes (every stepMeters). */
object ReplayPath {
    fun sample(waypoints: List<CoursePoint>, stepMeters: Double, startTimeMs: Long = 0, intervalMs: Long = 1000): List<Fix> {
        require(waypoints.size >= 2) { "need at least 2 points" }
        require(stepMeters > 0)
        val out = mutableListOf<Fix>()
        var t = startTimeMs
        out += Fix(waypoints[0].lat, waypoints[0].lng, t)
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val len = distanceMeters(a.lat, a.lng, b.lat, b.lng)
            val steps = maxOf(1, Math.ceil(len / stepMeters).toInt())
            for (s in 1..steps) {
                val f = s.toDouble() / steps
                t += intervalMs
                out += Fix(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f, t)
            }
        }
        return out
    }
}

/**
 * Placeholder demo course. Straight lines between the three points; replace with a
 * real road polyline (GPX) before the demo. Coordinates are the Pleos docs example POIs
 * plus a start point near Pangyo station.
 */
object DemoCourses {
    val pangyoToGangnam = Course(
        name = "판교 → 판교현대백화점 → 42dot (placeholder, straight lines)",
        start = CoursePoint("출발(판교역 부근)", 37.3948, 127.1112),
        points = listOf(
            CoursePoint("판교 현대백화점", 37.41275833, 127.09481944),
            CoursePoint("42dot", 37.48544722, 127.03636666),
        ),
    )
}
