package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.tenacy.roadcapture.util.mainActivity

abstract class BaseFragment : Fragment(), View.OnClickListener {

    protected open val clickableViewIds: Set<Int> = setOf()
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                remove()
                mainActivity.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setClickListenerRecursively(view)
    }

    private fun setClickListenerRecursively(view: View) {
//        if(view.id == R.id.ibtn_a_bar_back) view.setOnClickListener(this)

        if (view.id in clickableViewIds) {
            view.setOnClickListener(this)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setClickListenerRecursively(view.getChildAt(i))
            }
        }
    }

    override fun onClick(v: View?) {
        when(v?.id) {
//            R.id.ibtn_a_bar_back -> {
//                findNavController().navigateUp()
//            }
        }
    }
}