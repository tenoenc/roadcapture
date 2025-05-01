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

class AlbumCompeteBeforeBottomSheetFragment: BottomSheetDialogFragment() {

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
        binding.btnBSheetAlbumCompleteBeforePositive.setOnClickListener {
            val isPublic = binding.chipBSheetAlbumCompleteBeforePublic.isChecked
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_PUBLIC to isPublic
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

        const val TAG = "AlbumCompeteBeforeBottomSheetFragment"

        const val REQUEST_KEY = "album_complete_before"
        const val RESULT_PUBLIC = "public"

        fun newInstance(bundle: Bundle? = null): AlbumCompeteBeforeBottomSheetFragment {
            return AlbumCompeteBeforeBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}