package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetMemoryMoreBinding

class MemoryMoreBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetMemoryMoreBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetMemoryMoreBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetMemoryMoreInfo.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_EVENT_CLICK_INFO to System.currentTimeMillis().toString())
            )
            dismiss()
        }
        binding.btnBSheetMemoryMoreDelete.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_EVENT_CLICK_DELETE to System.currentTimeMillis().toString())
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {

        const val TAG = "MemoryMoreBottomSheetFragment"

        const val REQUEST_KEY = "memory_more"
        const val RESULT_EVENT_CLICK_INFO = "event_click_info"
        const val RESULT_EVENT_CLICK_DELETE = "event_click_delete"

        fun newInstance(bundle: Bundle? = null): MemoryMoreBottomSheetFragment {
            return MemoryMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}