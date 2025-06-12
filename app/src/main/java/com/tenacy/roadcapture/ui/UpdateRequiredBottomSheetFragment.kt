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
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetUpdateRequiredBinding
import com.tenacy.roadcapture.ui.TripBeforeBottomSheetFragment.ParamsOut
import kotlinx.parcelize.Parcelize

class UpdateRequiredBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetUpdateRequiredBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetUpdateRequiredBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 바깥 영역 터치로 닫히지 않도록 설정
        dialog?.setCanceledOnTouchOutside(false)

        // 뒤로가기 버튼으로 닫히지 않도록 설정
        dialog?.setCancelable(false)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetUpdateRequiredPositive.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_POSITIVE to ParamsOut.Positive)
            )
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

        const val TAG = "UpdateRequiredBottomSheetFragment"

        const val REQUEST_KEY = "update_required"
        const val KEY_PARAMS_OUT_POSITIVE = "params_out_positive"

        fun newInstance(bundle: Bundle? = null): UpdateRequiredBottomSheetFragment {
            return UpdateRequiredBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}