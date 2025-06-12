package com.tenacy.roadcapture.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

open class ExpandedBottomSheetDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // 키보드 상태와 관계없이 항상 전체 화면 기준으로 동작하도록 설정
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTwoStateBottomSheet()
    }

    private fun setupTwoStateBottomSheet() {
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                R.id.design_bottom_sheet
            )

            bottomSheet?.let {
                it.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), com.tenacy.roadcapture.R.color.background_normal)
                )

                val behavior = BottomSheetBehavior.from(it)

                // 중간 상태 방지 설정
                behavior.skipCollapsed = true
                behavior.isFitToContents = true

                // 상단 여백 설정 (dp를 픽셀로 변환)
    //                if (topOffset > 0) {
    //                    val density = resources.displayMetrics.density
    //                    behavior.expandedOffset = (topOffset * density).toInt()
    //                }

                // 상태 변경 콜백
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        when (newState) {
                            BottomSheetBehavior.STATE_EXPANDED -> {
                            }

                            BottomSheetBehavior.STATE_HALF_EXPANDED,
                            BottomSheetBehavior.STATE_COLLAPSED -> {
                                // calculateSlideOffset을 사용하여 현재 위치 계산
                                val currentOffset = calculateSlideOffset(behavior, bottomSheet)

                                if (currentOffset > 0.5) {
                                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                                } else {
                                    dismiss()
                                }
                            }
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // 필요 시 슬라이드 애니메이션 로직
                    }
                })

                // 시작 시 완전 펼침
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun calculateSlideOffset(behavior: BottomSheetBehavior<*>, bottomSheet: View): Float {
        val parentHeight = (bottomSheet.parent as View).height
        val collapsed = parentHeight - behavior.peekHeight
        val expanded = if (behavior.isFitToContents) {
            parentHeight - bottomSheet.height
        } else {
            parentHeight - behavior.expandedOffset
        }

        val range = collapsed - expanded
        if (range == 0) return 1f

        return (collapsed - bottomSheet.top) / range.toFloat().coerceAtLeast(1f)
    }
}