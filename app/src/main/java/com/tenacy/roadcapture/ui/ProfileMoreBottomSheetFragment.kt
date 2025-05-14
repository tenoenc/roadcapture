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
import com.tenacy.roadcapture.databinding.BSheetProfileMoreBinding
import kotlinx.parcelize.Parcelize

class ProfileMoreBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetProfileMoreBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetProfileMoreBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBSheetProfileMoreModifyPhoto.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_MODIFY_PHOTO to ParamsOut.ModifyPhoto)
            )
            dismiss()
        }
        binding.btnBSheetProfileMoreModifyName.setOnClickListener {
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_MODIFY_NAME to ParamsOut.ModifyName)
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data object ModifyPhoto: ParamsOut()
        @Parcelize
        data object ModifyName: ParamsOut()
    }

    companion object {

        const val TAG = "ProfileMoreBottomSheetFragment"

        const val REQUEST_KEY = "profile_more"
        const val KEY_PARAMS_OUT_MODIFY_PHOTO = "params_out_modify_photo"
        const val KEY_PARAMS_OUT_MODIFY_NAME = "params_out_modify_name"

        fun newInstance(bundle: Bundle? = null): ProfileMoreBottomSheetFragment {
            return ProfileMoreBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}