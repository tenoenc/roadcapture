package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetRangeSelectingBinding
import kotlinx.parcelize.Parcelize

class RangeSelectingBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetRangeSelectingBinding? = null
    private val binding get() = _binding!!

    private var items: List<ClusterMarkerItem>? = null
    private var selectedMemoryId: Long? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<Params>(KEY_PARAMS)?.let { params ->
            this@RangeSelectingBottomSheetFragment.items = params.items
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetRangeSelectingBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chipBSheetRangeSelectingAround.tag = ViewRange.AROUND.name
        binding.chipBSheetRangeSelectingWhole.tag = ViewRange.WHOLE.name
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetRangeSelectingPositive.setOnClickListener {
            val checkedChipId = binding.cgBSheetRangeSelecting.checkedChipId
            val checkedChip = binding.cgBSheetRangeSelecting.findViewById<Chip>(checkedChipId)
            val tag = checkedChip.tag as String
            val viewRange = ViewRange.valueOf(tag)
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULTS to TripFragment.ClusterMarkerItems(
                        selectedMemoryId = selectedMemoryId,
                        items = items,
                        viewRange = viewRange,
                    )
                )
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    data class Params(
        val selectedMemoryId: Long? = null,
        val items: List<ClusterMarkerItem>? = null,
    ): Parcelable

    companion object {

        const val TAG = "RangeSelectingBottomSheetFragment"

        const val KEY_PARAMS = "params"

        const val REQUEST_KEY = "range_selecting"
        const val RESULTS = "results"

        fun newInstance(bundle: Bundle? = null): RangeSelectingBottomSheetFragment {
            return RangeSelectingBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}