package com.ryuseilabs.pleostest.course

import android.content.SharedPreferences

/**
 * 스탬프북 도장 기록. SharedPreferences에 영속화한다.
 * key 구조: stamp.<bookId>.<courseId>.<stampId> = source(REAL|REPLAY):epochMillis
 *           done.<bookId>.<courseId> = epochMillis (전 도장 완주 시각)
 */
class BookStore(private val prefs: SharedPreferences) {

    fun isStampDone(bookId: String, courseId: String, stampId: String): Boolean =
        prefs.contains(stampKey(bookId, courseId, stampId))

    fun stampSource(bookId: String, courseId: String, stampId: String): Source? =
        prefs.getString(stampKey(bookId, courseId, stampId), null)
            ?.substringBefore(':')
            ?.let { runCatching { Source.valueOf(it) }.getOrNull() }

    fun markStamp(bookId: String, courseId: String, stampId: String, source: Source) {
        prefs.edit()
            .putString(stampKey(bookId, courseId, stampId), "${source.name}:${System.currentTimeMillis()}")
            .apply()
    }

    fun stampCount(bookId: String, courseId: String): Int =
        prefs.all.keys.count { it.startsWith("stamp.$bookId.$courseId.") }

    /** 전 도장 완료 시 완주 시각 기록 */
    fun markAllDone(bookId: String, courseId: String) {
        prefs.edit().putString("done.$bookId.$courseId", System.currentTimeMillis().toString()).apply()
    }

    /** 코스 도장 기록과 완주 기록을 모두 지운다 ("다시 도전하기") */
    fun resetCourse(bookId: String, courseId: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("stamp.$bookId.$courseId.") }
            .forEach { editor.remove(it) }
        editor.remove("done.$bookId.$courseId").apply()
    }

    fun isCompleted(bookId: String, courseId: String): Boolean =
        prefs.contains("done.$bookId.$courseId")

    fun completedAt(bookId: String, courseId: String): Long? =
        prefs.getString("done.$bookId.$courseId", null)?.toLongOrNull()

    fun completedSource(bookId: String, courseId: String): Source? = null // 미사용(도장 단위 source 사용)

    /** 첫 미완료 챕터 인덱스 (죽도산 도장이 있으면 2구간부터, 아니면 1구간) */
    fun resumeChapterIndex(bookId: String, course: BookCourse): Int {
        course.chapters.forEachIndexed { i, _ ->
            val destStampDone = course.stamps.any {
                it.source == StampSource.DESTINATION && it.chapterIndex == i &&
                    isStampDone(bookId, course.id, it.id)
            }
            if (!destStampDone) return i
        }
        return course.chapters.lastIndex
    }

    private fun stampKey(b: String, c: String, s: String) = "stamp.$b.$c.$s"
}
