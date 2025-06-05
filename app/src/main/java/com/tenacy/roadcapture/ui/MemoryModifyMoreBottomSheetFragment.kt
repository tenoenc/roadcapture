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
import com.tenacy.roadcapture.databinding.BSheetMemoryModifyMoreBinding
import kotlinx.parcelize.Parcelize

class MemoryModifyMoreBottomSheetFragment: ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetMemoryModifyMoreBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetMemoryModifyMoreBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.btnBSheetMemoryModifyMoreInfo.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_INFO to ParamsOut.Info)
            )
            dismiss()
        }
        binding.btnBSheetMemoryModifyMoreDelete.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_DELETE to ParamsOut.Delete)
            )
            dismiss()
        }
    }

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data object Info: ParamsOut()
        @Parcelize
        data object Delete: ParamsOut()
    }

    companion object {

        const val TAG = "MemoryModifyMoreBottomSheetFragment"

        const val REQUEST_KEY = "memory_modify_more"
        const val KEY_PARAMS_OUT_INFO = "params_out_info"
        const val KEY_PARAMS_OUT_DELETE = "params_out_delete"

        fun newInstance(bundle: Bundle? = null): MemoryModifyMoreBottomSheetFragment {
            return MemoryModifyMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}