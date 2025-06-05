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
import com.tenacy.roadcapture.databinding.BSheetAlbumMoreBinding
import kotlinx.parcelize.Parcelize

class AlbumMoreBottomSheetFragment: ExpandedBottomSheetDialogFragment() {

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

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumMoreReport.setSafeClickListener {
            val album = params?.albumId ?: return@setSafeClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_REPORT to ParamsOut.Report(album))
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
        val albumId: String,
    ): Parcelable

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data class Report(val albumId: String): ParamsOut()
    }

    companion object {

        const val TAG = "AlbumMoreBottomSheetFragment"

        const val REQUEST_KEY = "album_more"
        const val KEY_PARAMS_IN = "params_in"
        const val KEY_PARAMS_OUT_REPORT = "params_out_report"

        fun newInstance(bundle: Bundle? = null): AlbumMoreBottomSheetFragment {
            return AlbumMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}