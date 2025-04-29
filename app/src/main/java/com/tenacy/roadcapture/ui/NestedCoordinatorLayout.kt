package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper

class NestedCoordinatorLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout(context, attrs), NestedScrollingChild3 {

    private val helper = NestedScrollingChildHelper(this)

    init {
        isNestedScrollingEnabled = true
    }

    override fun isNestedScrollingEnabled(): Boolean = helper.isNestedScrollingEnabled

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        helper.isNestedScrollingEnabled = enabled
    }

    override fun hasNestedScrollingParent(type: Int): Boolean =
        helper.hasNestedScrollingParent(type)

    override fun hasNestedScrollingParent(): Boolean = helper.hasNestedScrollingParent()

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        val superResult = super.onStartNestedScroll(child, target, axes, type)
        return startNestedScroll(axes, type) || superResult
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int): Boolean {
        val superResult = super.onStartNestedScroll(child, target, axes)
        return startNestedScroll(axes) || superResult
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        // 자식이 먼저 소비 - 즉, 자체 레이아웃(super)에서 처리
        val superConsumed = intArrayOf(0, 0)
        super.onNestedPreScroll(target, dx, dy, superConsumed, type)

        // 남은 스크롤만 부모에게 전달
        val remainingDx = dx - superConsumed[0]
        val remainingDy = dy - superConsumed[1]

        // 자식이 모두 소비하지 않은 경우에만 부모에게 전달
        val parentConsumed = intArrayOf(0, 0)
        if (remainingDx != 0 || remainingDy != 0) {
            dispatchNestedPreScroll(remainingDx, remainingDy, parentConsumed, null, type)
        }

        // 총 소비량 계산
        consumed[0] = superConsumed[0] + parentConsumed[0]
        consumed[1] = superConsumed[1] + parentConsumed[1]
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // 자식이 먼저 소비
        val superConsumed = intArrayOf(0, 0)
        super.onNestedPreScroll(target, dx, dy, superConsumed)

        // 남은 스크롤만 부모에게 전달
        val remainingDx = dx - superConsumed[0]
        val remainingDy = dy - superConsumed[1]

        // 자식이 모두 소비하지 않은 경우에만 부모에게 전달
        val parentConsumed = intArrayOf(0, 0)
        if (remainingDx != 0 || remainingDy != 0) {
            dispatchNestedPreScroll(remainingDx, remainingDy, parentConsumed, null)
        }

        // 총 소비량 계산
        consumed[0] = superConsumed[0] + parentConsumed[0]
        consumed[1] = superConsumed[1] + parentConsumed[1]
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        // 이 메소드에서 중요한 부분: 자식 → 부모 전파의 결과를 어떻게 처리할 것인가

        // 1. 먼저 부모에게 스크롤 전달 (CollapsingToolbarLayout과의 상호작용 때문)
        val parentConsumed = intArrayOf(0, 0)
        if (dxUnconsumed != 0 || dyUnconsumed != 0) {
            dispatchNestedScroll(
                dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed,
                null, type, parentConsumed
            )
        }

        // 2. 부모가 소비하고 남은 것만 super에 전달
        val remainingDxUnconsumed = dxUnconsumed - parentConsumed[0]
        val remainingDyUnconsumed = dyUnconsumed - parentConsumed[1]

        val superConsumed = intArrayOf(0, 0)
        if (remainingDxUnconsumed != 0 || remainingDyUnconsumed != 0) {
            super.onNestedScroll(
                target,
                dxConsumed,
                dyConsumed,
                remainingDxUnconsumed,
                remainingDyUnconsumed,
                type,
                superConsumed
            )
        }

        // 3. 총 소비량 업데이트
        consumed[0] = parentConsumed[0] + superConsumed[0]
        consumed[1] = parentConsumed[1] + superConsumed[1]
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, type: Int
    ) {
        // 1. 먼저 부모에게 스크롤 전달
        val parentConsumed = intArrayOf(0, 0)
        if (dxUnconsumed != 0 || dyUnconsumed != 0) {
            dispatchNestedScroll(
                dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed,
                null, type, parentConsumed
            )
        }

        // 2. 부모가 소비하고 남은 것만 super에 전달
        val remainingDxUnconsumed = dxUnconsumed - parentConsumed[0]
        val remainingDyUnconsumed = dyUnconsumed - parentConsumed[1]

        if (remainingDxUnconsumed != 0 || remainingDyUnconsumed != 0) {
            super.onNestedScroll(
                target,
                dxConsumed,
                dyConsumed,
                remainingDxUnconsumed,
                remainingDyUnconsumed,
                type
            )
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        // 1. 먼저 부모에게 스크롤 전달
        val parentConsumed = intArrayOf(0, 0)
        if (dxUnconsumed != 0 || dyUnconsumed != 0) {
            dispatchNestedScroll(
                dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed,
                null, 0, parentConsumed
            )
        }

        // 2. 부모가 소비하고 남은 것만 super에 전달
        val remainingDxUnconsumed = dxUnconsumed - parentConsumed[0]
        val remainingDyUnconsumed = dyUnconsumed - parentConsumed[1]

        if (remainingDxUnconsumed != 0 || remainingDyUnconsumed != 0) {
            super.onNestedScroll(
                target,
                dxConsumed,
                dyConsumed,
                remainingDxUnconsumed,
                remainingDyUnconsumed
            )
        }
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        super.onStopNestedScroll(target, type)
        stopNestedScroll(type)
    }

    override fun onStopNestedScroll(target: View) {
        super.onStopNestedScroll(target)
        stopNestedScroll()
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        val superResult = super.onNestedPreFling(target, velocityX, velocityY)
        return dispatchNestedPreFling(velocityX, velocityY) || superResult
    }

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        val superResult = super.onNestedFling(target, velocityX, velocityY, consumed)
        return dispatchNestedFling(velocityX, velocityY, consumed) || superResult
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean =
        helper.startNestedScroll(axes, type)

    override fun startNestedScroll(axes: Int): Boolean = helper.startNestedScroll(axes)

    override fun stopNestedScroll(type: Int) {
        helper.stopNestedScroll(type)
    }

    override fun stopNestedScroll() {
        helper.stopNestedScroll()
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        helper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
            consumed
        )
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int
    ): Boolean = helper.dispatchNestedScroll(
        dxConsumed,
        dyConsumed,
        dxUnconsumed,
        dyUnconsumed,
        offsetInWindow,
        type
    )

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
        dyUnconsumed: Int, offsetInWindow: IntArray?
    ): Boolean = helper.dispatchNestedScroll(
        dxConsumed,
        dyConsumed,
        dxUnconsumed,
        dyUnconsumed,
        offsetInWindow
    )

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?,
        offsetInWindow: IntArray?, type: Int
    ): Boolean = helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean =
        helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        helper.dispatchNestedPreFling(velocityX, velocityY)

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean =
        helper.dispatchNestedFling(velocityX, velocityY, consumed)

}