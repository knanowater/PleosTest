package com.ryuseilabs.pleostest

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.ryuseilabs.pleostest.course.Fix

/**
 * Pleos FusedLocation SDK 래퍼 (샘플 앱 패턴: isSupported → initialize → 콜백 등록 → release).
 * 실패해도 예외를 밖으로 내지 않는다 — 호출 쪽은 NaviHelper 도착 이벤트로 폴백한다.
 */
class FusedLocationClient(context: Context) {

    private val fused = FusedLocation(context.applicationContext)
    private var started = false

    private var onFix: ((Fix) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) return
            onFix?.invoke(Fix(location.latitude, location.longitude, location.timestampMillis))
        }

        override fun onError(e: Exception) {
            Log.w(TAG, "FusedLocation error: $e")
            onError?.invoke(e.message ?: e.javaClass.simpleName)
        }
    }

    val isStarted get() = started

    /** @return 시작했으면 true, 미지원/실패면 false (폴백 사용) */
    fun start(onFix: (Fix) -> Unit, onError: (String) -> Unit): Boolean {
        if (started) return true
        this.onFix = onFix
        this.onError = onError
        return runCatching {
            if (!fused.isSupported()) {
                Log.i(TAG, "FusedLocation 미지원 — NaviHelper 이벤트로 폴백")
                return false
            }
            fused.initialize()
            fused.registerFusedLocationCallback(listener)
            started = true
            Log.i(TAG, "FusedLocation 시작")
            true
        }.getOrElse {
            Log.w(TAG, "FusedLocation 시작 실패: $it")
            false
        }
    }

    fun stop() {
        if (!started) return
        runCatching {
            fused.unregisterFusedLocationCallback(listener)
            fused.release()
        }
        started = false
        onFix = null
        onError = null
    }

    companion object {
        const val TAG = "RoadStampLoc"
    }
}
