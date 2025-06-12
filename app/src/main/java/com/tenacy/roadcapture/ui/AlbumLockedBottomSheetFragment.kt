package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetAlbumLockedBinding
import com.tenacy.roadcapture.ui.MemoryInfoBottomSheetFragment.ParamsIn
import com.tenacy.roadcapture.util.toDate
import com.tenacy.roadcapture.util.toLocalizedDateTimeString
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

class AlbumLockedBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetAlbumLockedBinding? = null
    private val binding get() = _binding!!

    private var paramsIn: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { params ->
            this.paramsIn = params
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetAlbumLockedBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        binding.description = getDescriptionText()
        binding.reason = paramsIn?.lockReason ?: ""
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumLockedNegative.setSafeClickListener {
            dismiss()
        }
    }

    private fun getDescriptionText(): String? {
        return paramsIn?.let {
            val formattedText = it.lockedAt.toDate().toLocalizedDateTimeString(requireContext(), includeTime = true, includeSeconds = true)
            "(${formattedText})\n\n${getString(R.string.album_visibility_restriction)}"
//            formattedText
        }
    }

    @Parcelize
    data class ParamsIn(
        val lockReason: String,
        val lockedAt: LocalDateTime,
    ): Parcelable

    companion object {

        const val TAG = "AlbumLockedBottomSheetFragment"

        const val REQUEST_KEY = "album_locked"
        const val KEY_PARAMS_IN = "params_in"

        fun newInstance(bundle: Bundle? = null): AlbumLockedBottomSheetFragment {
            return AlbumLockedBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}