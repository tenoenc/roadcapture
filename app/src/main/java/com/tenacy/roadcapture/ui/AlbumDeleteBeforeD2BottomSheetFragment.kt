package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetAlbumDeleteBeforeD2Binding
import com.tenacy.roadcapture.ui.dto.Album
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class AlbumDeleteBeforeD2BottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetAlbumDeleteBeforeD2Binding? = null
    private val binding get() = _binding!!

    private var params: ParamsIn? = null

    private val inputText = MutableStateFlow("")

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { params ->
            this.params = params
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BSheetAlbumDeleteBeforeD2Binding.inflate(inflater, container, false)
        binding.inputText = inputText
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupListeners()
        setupObservers()
    }

    private fun setupViews() {
        // 바깥 영역 터치로 닫히지 않도록 설정
        dialog?.setCanceledOnTouchOutside(false)

        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED

                // 한 번만 설정 - 키보드와 behavior 모두 처리
                binding.etBSheetAlbumDeleteBeforeD2Input.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        // 포커스 받을 때 - 키보드 올라옴
                        bottomSheetDialog.window?.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                        )
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        behavior.isDraggable = false
                    } else {
                        // 포커스 잃을 때 - 키보드 내려감
                        behavior.isDraggable = true
                        // 약간의 딜레이 후 원래 위치로
                        Handler(Looper.getMainLooper()).postDelayed({
                            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }, 100)
                    }
                }
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            inputText.collect {
                binding.btnBSheetAlbumDeleteBeforeD2Positive.isEnabled = it == TARGET_TEXT
            }
        }
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumDeleteBeforeD2Positive.setSafeClickListener {
            val album = params?.album ?: return@setSafeClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_PARAMS_OUT_DELETE to ParamsOut.Delete(album))
            )
            dismiss()
        }
        binding.btnBSheetAlbumDeleteBeforeD2Negative.setSafeClickListener {
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
        data class Delete(val album: Album): ParamsOut()
    }

    companion object {

        const val TAG = "AlbumDeleteBeforeD2BottomSheetFragment"

        const val TARGET_TEXT = "DELETE"

        const val REQUEST_KEY = "album_delete_before_d2"
        const val KEY_PARAMS_IN = "params_in"
        const val KEY_PARAMS_OUT_DELETE = "params_out_delete"

        fun newInstance(bundle: Bundle? = null): AlbumDeleteBeforeD2BottomSheetFragment {
            return AlbumDeleteBeforeD2BottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}