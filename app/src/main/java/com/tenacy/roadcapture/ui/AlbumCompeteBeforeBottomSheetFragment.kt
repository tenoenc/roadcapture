package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetAlbumCompleteBeforeBinding
import kotlinx.parcelize.Parcelize

class AlbumCompeteBeforeBottomSheetFragment: ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetAlbumCompleteBeforeBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetAlbumCompleteBeforeBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumCompleteBeforePositive.setSafeClickListener {
            val isPublic = binding.chipBSheetAlbumCompleteBeforePublic.isChecked
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    KEY_PARAMS_OUT_PUBLIC to ParamsOut.Public(isPublic)
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
        val selectedMemoryId: Long? = null,
        val items: List<ClusterMarkerItem>? = null,
    ): Parcelable

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data class Public(
            val isPublic: Boolean,
        ): ParamsOut()
    }

    companion object {

        const val TAG = "AlbumCompeteBeforeBottomSheetFragment"

        const val REQUEST_KEY = "album_complete_before"
        const val KEY_PARAMS_OUT_PUBLIC = "params_out_public"

        fun newInstance(bundle: Bundle? = null): AlbumCompeteBeforeBottomSheetFragment {
            return AlbumCompeteBeforeBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}