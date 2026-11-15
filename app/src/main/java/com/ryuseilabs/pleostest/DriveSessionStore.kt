package com.ryuseilabs.pleostest

import android.content.Context

/** Persistent in-progress drive session. Survives process/UI recreation by the head unit. */
data class DriveSession(
    val active: Boolean = false,
    val bookId: String? = null,
    val courseId: String? = null,
    val chapterIndex: Int = 0,
    val awaitingNext: Boolean = false,
    val finished: Boolean = false,
)

class DriveSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("drive_session", Context.MODE_PRIVATE)

    fun load(): DriveSession = DriveSession(
        active = prefs.getBoolean(K_ACTIVE, false),
        bookId = prefs.getString(K_BOOK, null),
        courseId = prefs.getString(K_COURSE, null),
        chapterIndex = prefs.getInt(K_CHAPTER, 0).coerceAtLeast(0),
        awaitingNext = prefs.getBoolean(K_AWAITING, false),
        finished = prefs.getBoolean(K_FINISHED, false),
    )

    fun start(bookId: String, courseId: String, chapterIndex: Int): DriveSession =
        save(DriveSession(true, bookId, courseId, chapterIndex, false, false))

    fun save(session: DriveSession): DriveSession {
        prefs.edit()
            .putBoolean(K_ACTIVE, session.active)
            .putString(K_BOOK, session.bookId)
            .putString(K_COURSE, session.courseId)
            .putInt(K_CHAPTER, session.chapterIndex)
            .putBoolean(K_AWAITING, session.awaitingNext)
            .putBoolean(K_FINISHED, session.finished)
            .apply()
        return session
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val K_ACTIVE = "active"
        const val K_BOOK = "bookId"
        const val K_COURSE = "courseId"
        const val K_CHAPTER = "chapterIndex"
        const val K_AWAITING = "awaitingNext"
        const val K_FINISHED = "finished"
    }
}
