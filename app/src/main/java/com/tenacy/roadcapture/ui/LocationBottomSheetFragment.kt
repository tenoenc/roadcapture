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
import com.tenacy.roadcapture.databinding.BSheetLocationBinding
import kotlinx.parcelize.Parcelize

class LocationBottomSheetFragment: BottomSheetDialogFragment() {

    private var _binding: BSheetLocationBinding? = null
    private val binding get() = _binding!!

    private var address: String? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { params ->
            val address = params.address
            this@LocationBottomSheetFragment.address = address
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetLocationBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.address = address
    }

    private fun setupListeners() {
        binding.btnBSheetLocationPositive.setOnClickListener {
            val address = address ?: return@setOnClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_POSITIVE to ParamsOut.Positive(address))
            )
            dismiss()
        }
        binding.btnBSheetLocationNegative.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    data class ParamsIn(val address: String) : Parcelable

    @Parcelize
    sealed class ParamsOut: Parcelable {
        @Parcelize
        data class Positive(val address: String) : ParamsOut()
    }

    companion object {

        const val TAG = "LocationBottomSheetFragment"

        const val REQUEST_KEY = "location"
        const val KEY_PARAMS_IN = "params_in"
        const val KEY_PARAMS_OUT_POSITIVE = "params_out_positive"

        fun newInstance(bundle: Bundle? = null): LocationBottomSheetFragment {
            return LocationBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}