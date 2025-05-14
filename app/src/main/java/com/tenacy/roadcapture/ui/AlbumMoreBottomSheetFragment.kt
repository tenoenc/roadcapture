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
import com.tenacy.roadcapture.databinding.BSheetAlbumMoreBinding
import com.tenacy.roadcapture.ui.dto.Album
import kotlinx.parcelize.Parcelize

class AlbumMoreBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetAlbumMoreBinding? = null
    private val binding get() = _binding!!

    private var params: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { params ->
            this@AlbumMoreBottomSheetFragment.params = params
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetAlbumMoreBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        params?.let {
            binding.toggleText = if (it.album.isPublic) "비공개로 전환하기" else "공개로 전환하기"
        }
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumMoreModify.setOnClickListener {
            val album = params?.album ?: return@setOnClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_TOGGLE_PUBLIC to ParamsOut.TogglePublic(album))
            )
            dismiss()
        }
        binding.btnBSheetAlbumMoreDelete.setOnClickListener {
            val album = params?.album ?: return@setOnClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_DELETE to ParamsOut.Delete(album))
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
        val album: Album,
    ): Parcelable

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data class TogglePublic(val album: Album): ParamsOut()
        @Parcelize
        data class Delete(val album: Album): ParamsOut()
    }

    companion object {

        const val TAG = "AlbumMoreBottomSheetFragment"

        const val REQUEST_KEY = "album_more"
        const val KEY_PARAMS_IN = "params_in"
        const val KEY_PARAMS_OUT_TOGGLE_PUBLIC = "toggle_public"
        const val KEY_PARAMS_OUT_DELETE = "delete"

        fun newInstance(bundle: Bundle? = null): AlbumMoreBottomSheetFragment {
            return AlbumMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}