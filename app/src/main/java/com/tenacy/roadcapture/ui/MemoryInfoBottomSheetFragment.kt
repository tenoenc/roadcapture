package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetMemoryInfoBinding
import com.tenacy.roadcapture.ui.dto.Memory
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import com.tenacy.roadcapture.util.*
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

class MemoryInfoBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetMemoryInfoBinding? = null
    private val binding get() = _binding!!

    private var paramsIn: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { params ->
            this@MemoryInfoBottomSheetFragment.paramsIn = params
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetMemoryInfoBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.title = getTitleText()
        binding.description = getDescriptionText()
    }

    private fun getTitleText(): String? {
        return paramsIn?.let {
            val placeName = it.placeName
            if (placeName != null) return placeName

            getDurationFormattedText()
        }
    }

    private fun getDescriptionText(): String? {
        return paramsIn?.let {
            val placeName = it.placeName
            val formattedText = it.createdAt.toDate().toLocalizedDateTimeString(requireContext(), includeTime = true, includeSeconds = true)
            if (placeName != null) {
                "${getDurationFormattedText()}\n(${formattedText})"
            } else {
                formattedText
            }
        }
    }

    private fun getDurationFormattedText(): String? {
        return paramsIn?.let {
            val localizedTimeAgoText = it.createdAt.toUtcTimestamp().toLocalizedTimeAgo(binding.root.context)

            val `0` = localizedTimeAgoText
            getString(R.string.memory_creation_time, `0`)
        }
    }

    private fun setupListeners() {
        binding.btnBSheetMemoryInfoNegative.setSafeClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    data class ParamsIn(
        val id: String = "",
        val placeName: String? = null,
        val createdAt: LocalDateTime,
    ): Parcelable {

        companion object {
            fun of(dto: MemoryViewerArguments.Memory) = ParamsIn(
                id = dto.id,
                placeName = dto.placeName,
                createdAt = dto.createdAt,
            )
            fun of(dto: Memory) = ParamsIn(
                id = dto.id,
                placeName = dto.placeName,
                createdAt = dto.createdAt,
            )
        }
    }

    companion object {

        const val TAG = "MemoryInfoBottomSheetFragment"

        const val REQUEST_KEY = "memory_info"
        const val KEY_PARAMS_IN = "params_in"

        fun newInstance(bundle: Bundle? = null): MemoryInfoBottomSheetFragment {
            return MemoryInfoBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}