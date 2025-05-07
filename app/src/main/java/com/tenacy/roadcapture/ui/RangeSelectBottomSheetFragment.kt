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
import com.tenacy.roadcapture.databinding.BSheetRangeSelectBinding
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import kotlinx.parcelize.Parcelize

class RangeSelectBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetRangeSelectBinding? = null
    private val binding get() = _binding!!

    private var items: List<ClusterMarkerItem>? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS)?.let { params ->
            this@RangeSelectBottomSheetFragment.items = params.items
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetRangeSelectBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chipBSheetRangeSelectAround.tag = ViewScope.AROUND.name
        binding.chipBSheetRangeSelectWhole.tag = ViewScope.WHOLE.name
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetRangeSelectPositive.setOnClickListener {
            val checkedChipId = binding.cgBSheetRangeSelect.checkedChipId
            val checkedChip = binding.cgBSheetRangeSelect.findViewById<Chip>(checkedChipId)
            val tag = checkedChip.tag as String
            val viewScope = ViewScope.valueOf(tag)
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_ITEMS to ParamsOut(
                        items = items ?: emptyList(),
                        viewScope = viewScope,
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
    data class ParamsIn(
        val items: List<ClusterMarkerItem>,
    ): Parcelable

    @Parcelize
    data class ParamsOut(
        val items: List<ClusterMarkerItem>,
        val viewScope: ViewScope,
    ): Parcelable

    companion object {

        const val TAG = "RangeSelectBottomSheetFragment"

        const val KEY_PARAMS = "params"

        const val REQUEST_KEY = "range_select"
        const val RESULT_ITEMS = "items"

        fun newInstance(bundle: Bundle? = null): RangeSelectBottomSheetFragment {
            return RangeSelectBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}