package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetAlbumDeletingBeforeBinding
import com.tenacy.roadcapture.util.SpannableUtils

class TripStopBeforeBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetAlbumDeletingBeforeBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetAlbumDeletingBeforeBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        val spanText = "복구할 수 없어요"
        val fullText = "앨범을 삭제하면 추억들을 ${spanText}\n정말 삭제하시겠어요?"
        SpannableUtils.setClickableText(
            requireContext(),
            binding.txtBSheetTripStopBeforeDescription,
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
        binding.btnBSheetTripStopBeforePositive.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_EVENT_CLICK_POSITIVE to System.currentTimeMillis().toString())
            )
            dismiss()
        }
        binding.btnBSheetTripStopBeforeNegative.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        const val TAG = "TripStopBeforeBottomSheetFragment"

        const val REQUEST_KEY = "delete_before"
        const val RESULT_EVENT_CLICK_POSITIVE = "event_click_positive"

        fun newInstance(bundle: Bundle? = null): TripStopBeforeBottomSheetFragment {
            return TripStopBeforeBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}