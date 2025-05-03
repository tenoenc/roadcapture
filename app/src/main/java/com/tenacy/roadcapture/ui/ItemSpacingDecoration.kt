package com.tenacy.roadcapture.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ItemSpacingDecoration(
    private val spacing: Int,
    private val startPosition: Int = 0,
    private val includeEdge: Boolean = false
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)

        if (position >= startPosition) {
            if (includeEdge) {
                outRect.top = spacing
            }

            outRect.bottom = spacing
        }
    }
}