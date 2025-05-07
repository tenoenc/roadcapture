package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetMemoryInfoBinding
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import com.tenacy.roadcapture.util.formatWithPattern
import com.tenacy.roadcapture.util.getFormattedDuration
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime

class MemoryInfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BSheetMemoryInfoBinding? = null
    private val binding get() = _binding!!

    private var memory: MemoryViewerArguments.Memory? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<MemoryViewerArguments.Memory>(KEY_MEMORY)?.let { memory ->
            this@MemoryInfoBottomSheetFragment.memory = memory
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
        return memory?.let {
            val placeName = it.placeName
            if (placeName != null) return placeName

            getDurationFormattedText()
        }
    }

    private fun getDescriptionText(): String? {
        return memory?.let {
            val placeName = it.placeName
            val formattedText = it.createdAt.formatWithPattern("yyyy-MM-dd HH:mm:ss")
            if (placeName != null) {
                "${getDurationFormattedText()}\n(${formattedText})"
            } else {
                formattedText
            }
        }
    }

    private fun getDurationFormattedText(): String? {
        return memory?.let {
            val currentTimeStamp = LocalDateTime.now().toTimestamp()
            val (value, unit) = getFormattedDuration(it.createdAt.toTimestamp(), currentTimeStamp)

            "${value}${unit} 전에 남긴 추억이에요"
        }
    }

    private fun setupListeners() {
        binding.btnBSheetMemoryInfoNegative.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        const val TAG = "MemoryInfoBottomSheetFragment"

        const val KEY_MEMORY = "memory"

        const val REQUEST_KEY = "memory_info"
        const val RESULT_EVENT_CLICK_POSITIVE = "event_click_positive"

        fun newInstance(bundle: Bundle? = null): MemoryInfoBottomSheetFragment {
            return MemoryInfoBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}