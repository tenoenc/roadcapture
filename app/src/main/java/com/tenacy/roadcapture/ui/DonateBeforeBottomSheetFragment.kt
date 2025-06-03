package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetDonateBeforeBinding
import kotlinx.parcelize.Parcelize

class DonateBeforeBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetDonateBeforeBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetDonateBeforeBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetDonateBeforeSmall.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_DONATE to ParamsOut.Donate("donation_small"))
            )
            dismiss()
        }
        binding.btnBSheetDonateBeforeMedium.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_DONATE to ParamsOut.Donate("donation_medium"))
            )
            dismiss()
        }
        binding.btnBSheetDonateBeforeLarge.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_DONATE to ParamsOut.Donate("donation_large"))
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
        data class Donate(val type: String): ParamsOut()
    }

    companion object {

        const val TAG = "DonateBeforeBottomSheetFragment"

        const val REQUEST_KEY = "donate_before"
        const val KEY_PARAMS_OUT_DONATE = "params_out_donate"

        fun newInstance(bundle: Bundle? = null): DonateBeforeBottomSheetFragment {
            return DonateBeforeBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}