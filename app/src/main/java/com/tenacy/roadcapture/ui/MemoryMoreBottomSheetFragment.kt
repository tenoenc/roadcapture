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
import com.tenacy.roadcapture.databinding.BSheetMemoryMoreBinding
import kotlinx.parcelize.Parcelize

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

        setupBottomSheet()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.btnBSheetMemoryMoreInfo.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_INFO to ParamsOut.Info)
            )
            dismiss()
        }
        binding.btnBSheetMemoryMoreAlbum.setSafeClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_ALBUM to ParamsOut.Album)
            )
            dismiss()
        }
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

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data object Info: ParamsOut()
        @Parcelize
        data object Album: ParamsOut()
    }

    companion object {

        const val TAG = "MemoryMoreBottomSheetFragment"

        const val REQUEST_KEY = "memory_more"
        const val KEY_PARAMS_OUT_INFO = "params_out_info"
        const val KEY_PARAMS_OUT_ALBUM = "params_out_album"

        fun newInstance(bundle: Bundle? = null): MemoryMoreBottomSheetFragment {
            return MemoryMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}