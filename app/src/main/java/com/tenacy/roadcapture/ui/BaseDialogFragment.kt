package com.tenacy.roadcapture.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment

abstract class BaseDialogFragment : DialogFragment(), View.OnClickListener {

    // 처리할 뷰 ID들을 자식 클래스에서 오버라이드
    protected open val clickableViewIds: Set<Int> = setOf()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setClickListenerRecursively(view)
    }

    private fun setClickListenerRecursively(view: View) {
        if (view.id in clickableViewIds) {
            view.setOnClickListener(this)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setClickListenerRecursively(view.getChildAt(i))
            }
        }
    }
}