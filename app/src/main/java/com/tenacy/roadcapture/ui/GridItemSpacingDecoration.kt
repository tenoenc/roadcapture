package com.tenacy.roadcapture.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridItemSpacingDecoration(
    private val spanCount: Int,
    private val horizontalSpacing: Int,
    private val verticalSpacing: Int,
    private val startPosition: Int = 0  // 간격을 적용할 시작 포지션
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)

        if (position >= startPosition) {  // 시작 포지션 이후부터 간격 적용
            val adjustedPosition = position - startPosition  // 시작 포지션을 고려한 위치 계산
            val column = adjustedPosition % spanCount
            val row = adjustedPosition / spanCount

            // 수평 간격 계산
            outRect.left = column * horizontalSpacing / spanCount
            outRect.right = horizontalSpacing - (column + 1) * horizontalSpacing / spanCount

            // 수직 간격 계산 - 첫 번째 행이 아닌 경우에만 top 간격 적용
            if (row > 0) {
                outRect.top = verticalSpacing
            }
            // bottom은 명시적으로 설정하지 않음 (기본값 0)
        }
    }
}