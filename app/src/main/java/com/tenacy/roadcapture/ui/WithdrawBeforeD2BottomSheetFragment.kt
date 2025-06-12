package com.tenacy.roadcapture.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetWithdrawBeforeD2Binding
import com.tenacy.roadcapture.util.SpannableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class WithdrawBeforeD2BottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetWithdrawBeforeD2Binding? = null
    private val binding get() = _binding!!

    private val inputText = MutableStateFlow("")

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BSheetWithdrawBeforeD2Binding.inflate(inflater, container, false)
        binding.inputText = inputText
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
        setupObservers()
    }

    private fun setupViews() {
        // 바깥 영역 터치로 닫히지 않도록 설정
        dialog?.setCanceledOnTouchOutside(false)

        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED

                // 한 번만 설정 - 키보드와 behavior 모두 처리
                binding.etBSheetWithdrawBeforeD2Input.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        // 포커스 받을 때 - 키보드 올라옴
                        bottomSheetDialog.window?.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        )
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        behavior.isDraggable = false
                    } else {
                        // 포커스 잃을 때 - 키보드 내려감
                        behavior.isDraggable = true
                        // 약간의 딜레이 후 원래 위치로
                        Handler(Looper.getMainLooper()).postDelayed({
                            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }, 100)
                    }
                }
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            inputText.collect {
                binding.btnBSheetWithdrawBeforeD2Positive.isEnabled = it == TARGET_TEXT
            }
        }
    }

    private fun setupListeners() {
        binding.btnBSheetWithdrawBeforeD2Positive.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_POSITIVE to ParamsOut.Positive)
            )
            dismiss()
        }
        binding.btnBSheetWithdrawBeforeD2Negative.setSafeClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data object Positive: ParamsOut()
    }

    companion object {

        const val TAG = "WithdrawBeforeD2BottomSheetFragment"

        const val TARGET_TEXT = "ROADCAPTURE"

        const val REQUEST_KEY = "withdraw_before_d2"
        const val KEY_PARAMS_OUT_POSITIVE = "params_out_positive"

        fun newInstance(bundle: Bundle? = null): WithdrawBeforeD2BottomSheetFragment {
            return WithdrawBeforeD2BottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}