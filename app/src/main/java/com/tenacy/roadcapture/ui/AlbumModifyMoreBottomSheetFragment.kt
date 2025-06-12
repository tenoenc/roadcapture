package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.databinding.BSheetAlbumModifyMoreBinding
import com.tenacy.roadcapture.ui.dto.Album
import kotlinx.parcelize.Parcelize

class AlbumModifyMoreBottomSheetFragment: ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetAlbumModifyMoreBinding? = null
    private val binding get() = _binding!!

    private var params: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { params ->
            this@AlbumModifyMoreBottomSheetFragment.params = params
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetAlbumModifyMoreBinding.inflate(inflater, container, false).apply {
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
            binding.toggleText = if (it.album.isPublic) requireContext().getString(R.string.switch_to_private) else requireContext().getString(R.string.switch_to_public)
            binding.shareVisible = it.album.shareId.isNullOrBlank() && it.album.isPublic && SubscriptionPref.isSubscriptionActive
        }
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumModifyMorePublic.setSafeClickListener {
            val album = params?.album ?: return@setSafeClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_TOGGLE_PUBLIC to ParamsOut.TogglePublic(album))
            )

            dismiss()
        }
        binding.btnBSheetAlbumModifyMoreShare.setSafeClickListener {
            val album = params?.album ?: return@setSafeClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_SHARE to ParamsOut.Share(album))
            )
            dismiss()
        }
        binding.btnBSheetAlbumModifyMoreDelete.setSafeClickListener {
            val album = params?.album ?: return@setSafeClickListener
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
        data class Locked(val album: Album): ParamsOut()
        @Parcelize
        data class Share(val album: Album): ParamsOut()
        @Parcelize
        data class Delete(val album: Album): ParamsOut()
    }

    companion object {

        const val TAG = "AlbumModifyMoreBottomSheetFragment"

        const val REQUEST_KEY = "album_more"
        const val KEY_PARAMS_IN = "params_in"
        const val KEY_PARAMS_OUT_TOGGLE_PUBLIC = "toggle_public"
        const val KEY_PARAMS_OUT_SHARE = "share"
        const val KEY_PARAMS_OUT_DELETE = "delete"

        fun newInstance(bundle: Bundle? = null): AlbumModifyMoreBottomSheetFragment {
            return AlbumModifyMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}