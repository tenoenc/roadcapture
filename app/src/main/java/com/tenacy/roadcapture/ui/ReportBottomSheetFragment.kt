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
import com.tenacy.roadcapture.data.ReportReason
import com.tenacy.roadcapture.databinding.BSheetReportBinding
import com.tenacy.roadcapture.ui.LocationBottomSheetFragment.ParamsIn
import kotlinx.parcelize.Parcelize

class ReportBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetReportBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    private var albumId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(
            KEY_PARAMS_IN
        )?.let { params ->
            val albumId = params.albumId
            this@ReportBottomSheetFragment.albumId = albumId
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetReportBinding.inflate(inflater, container, false).apply {
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
        binding.llBSheetReportReasonContainer.setupSelectionContainer(true) { selectedIndices ->
            binding.btnBSheetReportPositive.visibility =
                if (selectedIndices.isNotEmpty()) View.VISIBLE else View.GONE
        }
        binding.btnBSheetReportPositive.setOnClickListener {
            val selectedIndex = binding.llBSheetReportReasonContainer.getSelectedIndices().firstOrNull()

            val reportReason = when (selectedIndex) {
                0 -> ReportReason.InappropriateContent
                1 -> ReportReason.SpamAdvertising
                2 -> ReportReason.PersonalInfoExposure
                3 -> ReportReason.FalseInformation
                else -> ReportReason.Undefined
            }

            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    KEY_PARAMS_OUT_REPORT to ParamsOut.Report(albumId!!, reportReason)
                )
            )

            dismiss()
        }
        binding.btnBSheetReportNegative.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    data class ParamsIn(
        val albumId: String,
    ): Parcelable

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data class Report(
            val albumId: String,
            val reason: ReportReason,
        ): ParamsOut()
    }

    companion object {

        const val TAG = "ReportBottomSheetFragment"

        const val REQUEST_KEY = "report"
        const val KEY_PARAMS_IN = "params_in"
        const val KEY_PARAMS_OUT_REPORT = "params_out_report"

        fun newInstance(bundle: Bundle? = null): ReportBottomSheetFragment {
            return ReportBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}