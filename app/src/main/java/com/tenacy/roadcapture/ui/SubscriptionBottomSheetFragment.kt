package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetSubscriptionBinding
import kotlinx.parcelize.Parcelize

class SubscriptionBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BSheetSubscriptionBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetSubscriptionBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBottomSheet()
        setupListeners()
    }

    private fun setupBottomSheet() {
        // 다이얼로그가 보여진 후에 바텀시트를 완전히 펼치기
        dialog?.setOnShowListener { dialogInterface ->
            // BottomSheet의 배경 레이아웃 참조 가져오기
            val bottomSheet =
                (dialogInterface as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                // BottomSheetBehavior 가져오기
                val behavior = BottomSheetBehavior.from(it)

                // 바텀시트를 완전히 펼치도록 상태 설정
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun setupListeners() {
        binding.btnBSheetSubscriptionPositive.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_POSITIVE to ParamsOut.Positive)
            )
            dismiss()
        }
        binding.btnBSheetSubscriptionNegative.setOnClickListener {
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

        const val TAG = "SubscriptionBottomSheetFragment"

        const val REQUEST_KEY = "subscription"
        const val KEY_PARAMS_OUT_POSITIVE = "params_out_positive"

        fun newInstance(bundle: Bundle? = null): SubscriptionBottomSheetFragment {
            return SubscriptionBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}