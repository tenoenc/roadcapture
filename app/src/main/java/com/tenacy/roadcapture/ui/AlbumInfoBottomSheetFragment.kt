package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.dto.FirebaseAlbum
import com.tenacy.roadcapture.databinding.BSheetAlbumInfoBinding
import com.tenacy.roadcapture.databinding.ItemStopoverBinding
import com.tenacy.roadcapture.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class AlbumInfoBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BSheetAlbumInfoBinding? = null
    private val binding get() = _binding!!

    private var params: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS)?.let { paramsIn ->
            this@AlbumInfoBottomSheetFragment.params = paramsIn
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetAlbumInfoBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupBottomSheet()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        binding.title = getTitleText()
        bindContentRow1()
        binding.contentRow2 = getContentRow2Text()
        setItemsToLayout()
    }

    private fun setupBottomSheet() {
        // 다이얼로그가 보여진 후에 바텀시트를 완전히 펼치기
        dialog?.setOnShowListener { dialogInterface ->
            // BottomSheet의 배경 레이아웃 참조 가져오기
            val bottomSheet =
                (dialogInterface as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                // BottomSheetBehavior 가져오기
                val behavior = BottomSheetBehavior.from(it)

                // 바텀시트를 완전히 펼치도록 상태 설정
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun setupListeners() {
        binding.btnBSheetAlbumInfoNegative.setOnClickListener {
            dismiss()
        }
    }

    private fun bindContentRow1() {
        getContentRow1Elements()?.let { (startDateText, endDateText) ->
            val params = params ?: return@let
            val fullText = "$startDateText ~ $endDateText"
            val activity = (requireParentFragment() as? Fragment)?.mainActivity
            SpannableUtils.setClickableText(
                requireContext(),
                binding.txtBSheetAlbumInfoRow1,
                fullText,
                listOf(
                    SpannableUtils.ClickablePart(
                        text = startDateText,
                        textColor = ContextCompat.getColor(requireContext(), R.color.label_neutral),
                        isUnderlined = true,
                    ) {
                        lifecycleScope.launch(Dispatchers.Default) {
                            activity?.vm?.viewEvent(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        params.album.createdAt.formatToLocalizedDateTime(requireContext()),
                                        ToastMessageType.Info
                                    )
                                )
                            )
                        }
                    },
                    SpannableUtils.ClickablePart(
                        text = endDateText,
                        textColor = ContextCompat.getColor(requireContext(), R.color.label_neutral),
                        isUnderlined = true,
                        startIndex = startDateText.length,
                    ) {
                        lifecycleScope.launch(Dispatchers.Default) {
                            activity?.vm?.viewEvent(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        params.album.endedAt.formatToLocalizedDateTime(requireContext()),
                                        ToastMessageType.Info
                                    )
                                )
                            )
                        }
                    },
                )
            )
        }
    }

    private fun getContentRow1Elements(): Pair<String, String>? {
        return params?.let {
            val startDateText = it.album.createdAt.formatToLocalizedDate(requireContext())
            val endDateText = it.album.endedAt.formatToLocalizedDate(requireContext())
            startDateText to endDateText
        }
    }

    private fun getTitleText(): String? {
        return params?.album?.title
    }

    private fun getContentRow2Text(): String? {
        return params?.let {
            val durationFormattedText =
                getDurationFormattedText(it.album.createdAt.toTimestamp(), it.album.endedAt.toTimestamp())
            "${durationFormattedText} 동안 ${it.totalMemoryCount}개의 추억을 남겼어요"
        }
    }

    private fun setItemsToLayout() {
        val params = params ?: return
        val items = params.album.regionTags

        val linearLayout = binding.llBSheetAlbumInfoLocations
        linearLayout.removeAllViews()

        // 인플레이터 준비
        val inflater = LayoutInflater.from(binding.root.context)

        items.forEachIndexed { index, item ->
            // 항목 뷰바인딩 인플레이트
            val itemBinding = ItemStopoverBinding.inflate(inflater, linearLayout, false)

            // 텍스트 설정
            itemBinding.country = item["country"]!!
            itemBinding.depth1 = item["depth1"]!!
            itemBinding.depth2 = item["depth2"]!!

            // 레이아웃 파라미터 설정
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // 레이아웃에 뷰 추가
            linearLayout.addView(itemBinding.root, layoutParams)

            // 마지막 아이템이 아닌 경우 구분선 이미지 추가
            if (index < items.size - 1) {
                // 이미지뷰 생성
                val dividerImageView = ImageView(binding.root.context).apply {
                    setImageResource(R.drawable.ic_arrow_bottom) // 사용할 drawable 리소스
                    this@apply.layoutParams = LinearLayout.LayoutParams(24.toPx, 24.toPx).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setMargins(0, 12.toPx, 0, 12.toPx) // 위아래 마진 추가
                    }
                }

                linearLayout.addView(dividerImageView)
            }
        }
    }

    @Parcelize
    data class ParamsIn(
        val album: FirebaseAlbum,
        val totalMemoryCount: Int,
    ): Parcelable

    companion object {

        const val TAG = "AlbumInfoBottomSheetFragment"

        const val KEY_PARAMS = "params"

        const val REQUEST_KEY = "album_info"
        const val RESULT_EVENT_CLICK_POSITIVE = "event_click_positive"

        fun newInstance(bundle: Bundle? = null): AlbumInfoBottomSheetFragment {
            return AlbumInfoBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}