package com.ryuseilabs.pleostest

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Main-safe snapshot shared by DriveService and the UI. */
data class DriveSnapshot(
    val active: Boolean = false,
    val bookId: String? = null,
    val courseId: String? = null,
    val chapterIndex: Int = 0,
    val awaitingNext: Boolean = false,
    val finished: Boolean = false,
    val status: String = "준비되었습니다.",
)

/** In-process bridge. The system may recreate the service; persisted state is authoritative. */
object DriveHub {
    @Volatile
    private var snapshot = DriveSnapshot()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(DriveSnapshot) -> Unit>()

    fun get(): DriveSnapshot = snapshot

    fun publish(update: (DriveSnapshot) -> DriveSnapshot): DriveSnapshot {
        val next = update(snapshot)
        snapshot = next
        listeners.forEach { listener -> main.post { listener(next) } }
        return next
    }

    /** Immediate callback on the caller thread (with current state), then live updates on main. */
    fun subscribe(listener: (DriveSnapshot) -> Unit) {
        listeners.add(listener)
        main.post { listener(snapshot) }
    }

    fun unsubscribe(listener: (DriveSnapshot) -> Unit) {
        listeners.remove(listener)
    }
}
