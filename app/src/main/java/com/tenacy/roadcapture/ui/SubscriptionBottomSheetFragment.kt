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
import com.tenacy.roadcapture.util.Constants
import kotlinx.parcelize.Parcelize

class SubscriptionBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

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

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        val `0` = Constants.PREMIUM_PRICE_PER_MONTH
        binding.premiumPricePerMonthText = requireContext().getString(R.string.subscription_price, `0`)
        val `1` = Constants.PREMIUM_TODAY_MEMORY_MAX_SIZE
        binding.row2Text = requireContext().getString(R.string.daily_memory_limit, `1`)
    }

    private fun setupListeners() {
        binding.btnBSheetSubscriptionPositive.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_POSITIVE to ParamsOut.Positive)
            )
            dismiss()
        }
        binding.btnBSheetSubscriptionNegative.setSafeClickListener {
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