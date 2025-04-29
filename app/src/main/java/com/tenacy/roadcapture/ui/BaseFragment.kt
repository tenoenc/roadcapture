package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.util.mainActivity

abstract class BaseFragment : Fragment(), View.OnClickListener {

    private lateinit var onBackPressedCallback: OnBackPressedCallback
    protected open val onBackPressed: (() -> Unit)? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                remove()
                onBackPressed?.invoke() ?: mainActivity.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setClickListenerRecursively(view)
    }

    private fun setClickListenerRecursively(view: View) {
        if(view.id == R.id.ibtn_a_bar_back && !view.hasOnClickListeners()) {
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
            R.id.ibtn_a_bar_back -> mainActivity.onBackPressed()
        }
    }
}