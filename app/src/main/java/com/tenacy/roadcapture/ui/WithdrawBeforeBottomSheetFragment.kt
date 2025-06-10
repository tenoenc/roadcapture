package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetWithdrawBeforeBinding
import com.tenacy.roadcapture.util.SpannableUtils
import kotlinx.parcelize.Parcelize

class WithdrawBeforeBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetWithdrawBeforeBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetWithdrawBeforeBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        val spanText = requireContext().getString(R.string.all_data_deleted)
        val `0` = spanText
        val fullText = requireContext().getString(R.string.withdrawal_confirmation, `0`)
        SpannableUtils.setClickableText(
            requireContext(),
            binding.txtBSheetWithdrawBeforeDescription,
            fullText,
            listOf(
                SpannableUtils.ClickablePart(
                    text = spanText,
                    textColor = ContextCompat.getColor(requireContext(), R.color.warning),
                )
            )
        )
    }

    private fun setupListeners() {
        binding.btnBSheetWithdrawBeforePositive.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_POSITIVE to ParamsOut.Positive)
            )
            dismiss()
        }
        binding.btnBSheetWithdrawBeforeNegative.setSafeClickListener {
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

        const val TAG = "WithdrawBeforeBottomSheetFragment"

        const val REQUEST_KEY = "withdraw"
        const val KEY_PARAMS_OUT_POSITIVE = "params_out_positive"

        fun newInstance(bundle: Bundle? = null): WithdrawBeforeBottomSheetFragment {
            return WithdrawBeforeBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}