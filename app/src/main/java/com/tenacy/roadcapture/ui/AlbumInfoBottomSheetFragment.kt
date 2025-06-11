package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.BSheetAlbumInfoBinding
import com.tenacy.roadcapture.databinding.ItemStopoverBinding
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class AlbumInfoBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

    private var _binding: BSheetAlbumInfoBinding? = null
    private val binding get() = _binding!!

    private var params: ParamsIn? = null

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getParcelable<ParamsIn>(KEY_PARAMS_IN)?.let { paramsIn ->
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

    private fun setupListeners() {
        binding.btnBSheetAlbumInfoNegative.setSafeClickListener {
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
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                            activity?.vm?.viewEvent(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        params.album.createdAt.toDate().toLocalizedDateTimeString(requireContext(), includeTime = true, includeSeconds = true),
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
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                            activity?.vm?.viewEvent(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        params.album.endedAt.toDate().toLocalizedDateTimeString(requireContext(), includeTime = true, includeSeconds = true),
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
            val startDateText = it.album.createdAt.toDate().toLocalizedDateString(requireContext())
            val endDateText = it.album.endedAt.toDate().toLocalizedDateString(requireContext())
            startDateText to endDateText
        }
    }

    private fun getTitleText(): String? {
        return params?.album?.title
    }

    private fun getContentRow2Text(): String? {
        return params?.let {
            val (days, hours, minutes) =
                getDurationFormattedText(it.album.createdAt.toTimestamp(), it.album.endedAt.toTimestamp())

            val sb = StringBuilder()
            val sb2 = StringBuilder()

            if(days > 0) {
                val `0` = days.toInt()
                sb2.append(requireContext().getString(R.string.time_days, `0`))
                sb2.append(" ")
            }

            if(hours > 0) {
                val `0` = hours.toInt()
                sb2.append(requireContext().getString(R.string.time_hours, `0`))
                sb2.append(" ")
            }

            if(minutes > 0) {
                val `0` = minutes.toInt()
                sb2.append(requireContext().getString(R.string.time_minutes, `0`))
            }

            val `0` = sb2.toString()
            sb.append(if(minutes == 0L) "" else requireContext().getString(R.string.duration_format, `0`))
            sb.append(" ")
            val `1` = it.totalMemoryCount
            sb.append(requireContext().getString(R.string.memory_count_created, `1`))
            return sb.toString()
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
        val album: Album,
        val totalMemoryCount: Int,
    ): Parcelable

    companion object {

        const val TAG = "AlbumInfoBottomSheetFragment"

        const val REQUEST_KEY = "album_info"
        const val KEY_PARAMS_IN = "params_in"

        fun newInstance(bundle: Bundle? = null): AlbumInfoBottomSheetFragment {
            return AlbumInfoBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}