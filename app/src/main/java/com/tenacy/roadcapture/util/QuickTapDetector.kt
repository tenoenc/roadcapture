package com.tenacy.roadcapture.util

import android.os.SystemClock
import android.view.View
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 특정 뷰를 빠르게 여러번 탭하는 것을 감지하는 유틸리티 클래스
 * @param requiredTaps 필요한 탭 횟수 (기본값: 5)
 * @param timeWindow 탭이 유효한 시간 간격(ms) (기본값: 1초)
 */
class QuickTapDetector(
    private val requiredTaps: Int = 5,
    private val timeWindow: Long = 1000L
) {
    private val tapTimestamps = mutableListOf<Long>()
    private var resetJob: Job? = null

    private val _tapCount = MutableStateFlow(0)
    val tapCount: StateFlow<Int> = _tapCount.asStateFlow()

    /**
     * 탭 이벤트 처리
     * @return true if required taps achieved, false otherwise
     */
    fun onTap(): Boolean {
        val currentTime = SystemClock.elapsedRealtime()

        // 이전 탭들 중 시간 윈도우를 벗어난 것들 제거
        tapTimestamps.removeAll { currentTime - it > timeWindow }

        // 현재 탭 추가
        tapTimestamps.add(currentTime)
        _tapCount.value = tapTimestamps.size

        // 자동 리셋 타이머 재시작
        resetJob?.cancel()
        resetJob = CoroutineScope(Dispatchers.Main).launch {
            delay(timeWindow)
            reset()
        }

        // 필요한 탭 수를 달성했는지 확인
        return tapTimestamps.size >= requiredTaps
    }

    /**
     * 탭 카운트 리셋
     */
    fun reset() {
        tapTimestamps.clear()
        _tapCount.value = 0
        resetJob?.cancel()
    }

    /**
     * 정리 메서드 (Activity/Fragment onDestroy에서 호출)
     */
    fun cleanup() {
        reset()
        resetJob?.cancel()
    }
}

/**
 * View Extension - Quick Tap 리스너 설정
 */
inline fun View.setQuickTapListener(
    tapCount: Int = 5,
    timeWindow: Long = 1000L,
    crossinline onQuickTap: () -> Unit
) {
    val detector = QuickTapDetector(tapCount, timeWindow)

    setOnClickListener {
        if (detector.onTap()) {
            onQuickTap()
            detector.reset()
        }
    }

    // View가 detach될 때 정리
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}

        override fun onViewDetachedFromWindow(v: View) {
            detector.cleanup()
        }
    })
}

/**
 * View Extension - Quick Tap 리스너 설정 (Flow 사용)
 */
fun View.setQuickTapListenerWithFlow(
    scope: CoroutineScope,
    tapCount: Int = 5,
    timeWindow: Long = 1000L,
    onTapCountChange: (Int) -> Unit = {},
    onQuickTap: () -> Unit
) {
    val detector = QuickTapDetector(tapCount, timeWindow)

    // 탭 카운트 변경 관찰
    scope.launch {
        detector.tapCount.collect { count ->
            onTapCountChange(count)
        }
    }

    setOnClickListener {
        if (detector.onTap()) {
            onQuickTap()
            detector.reset()
        }
    }

    // View가 detach될 때 정리
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}

        override fun onViewDetachedFromWindow(v: View) {
            detector.cleanup()
        }
    })
}