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
import com.tenacy.roadcapture.databinding.BSheetMyAlbumMoreBinding
import com.tenacy.roadcapture.ui.dto.Album
import kotlinx.parcelize.Parcelize

class MyAlbumMoreBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetMyAlbumMoreBinding? = null
    private val binding get() = _binding!!

    private var params: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(PARAMS)?.let { params ->
            this@MyAlbumMoreBottomSheetFragment.params = params
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetMyAlbumMoreBinding.inflate(inflater, container, false).apply {
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
        binding.btnBSheetMyAlbumMoreModify.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_EVENT_CLICK_TOGGLE_PUBLIC to params?.album)
            )
            dismiss()
        }
        binding.btnBSheetMyAlbumMoreDelete.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_EVENT_CLICK_DELETE to params?.album)
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

    companion object {

        const val TAG = "MyAlbumMoreBottomSheetFragment"

        const val PARAMS = "params"

        const val REQUEST_KEY = "my_album_more"
        const val RESULT_EVENT_CLICK_TOGGLE_PUBLIC = "event_click_toggle_public"
        const val RESULT_EVENT_CLICK_DELETE = "event_click_delete"

        fun newInstance(bundle: Bundle? = null): MyAlbumMoreBottomSheetFragment {
            return MyAlbumMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}