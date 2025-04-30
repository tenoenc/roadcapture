package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetTripBeforeBinding

class TripBeforeBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetTripBeforeBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetTripBeforeBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetTripBeforePositive.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_EVENT_CLICK_POSITIVE to System.currentTimeMillis().toString())
            )
            dismiss()
        }
        binding.btnBSheetTripBeforeNegative.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        const val TAG = "TripBeforeBottomSheetFragment"

        const val REQUEST_KEY = "trip_before"
        const val RESULT_EVENT_CLICK_POSITIVE = "event_click_positive"

        fun newInstance(bundle: Bundle? = null): TripBeforeBottomSheetFragment {
            return TripBeforeBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}