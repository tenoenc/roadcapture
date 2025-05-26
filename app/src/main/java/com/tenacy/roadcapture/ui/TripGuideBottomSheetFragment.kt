package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.databinding.BSheetTripGuideBinding
import com.tenacy.roadcapture.util.SpannableUtils
import com.tenacy.roadcapture.util.SubscriptionValues
import kotlinx.parcelize.Parcelize

class TripGuideBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BSheetTripGuideBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return BSheetTripGuideBinding.inflate(inflater, container, false).apply {
            _binding = this
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupBottomSheet()
        setupListeners()
    }

    private fun setupViews() {
        val spanText1 = "자동으로 삭제"
        val spanFullText1 = "앱을 종료해도 여행은 끝나지 않아요.\n단, 여행 기간이 한 달이 넘어가면 앨범은 ${spanText1}돼요."

        SpannableUtils.setClickableText(
            requireContext(),
            binding.txtBSheetTripGuideSpan1,
            spanFullText1,
            listOf(
                SpannableUtils.ClickablePart(
                    text = spanText1,
                    textColor = ContextCompat.getColor(requireContext(), R.color.warning),
                ),
            )
        )

        val isSubscriptionActive = SubscriptionPref.isSubscriptionActive

        val spanText2_1 = "${SubscriptionValues.memoryMaxSize}개"
        val spanText2_2 = "여기"
        val spanFullText2 = if(isSubscriptionActive) {
            "추억은 ${spanText2_1}까지만 만들 수 있어요."
        } else {
            "무료 플랜에서는 추억을 ${spanText2_1}까지만 만들 수 있어요.\n더 많은 추억을 만들기 원하시면 ${spanText2_2}를 클릭해주세요."
        }

        SpannableUtils.setClickableText(
            requireContext(),
            binding.txtBSheetTripGuideSpan2,
            spanFullText2,
            listOf(
                SpannableUtils.ClickablePart(
                    text = spanText2_1,
                    textColor = ContextCompat.getColor(requireContext(), R.color.warning),
                ),
                SpannableUtils.ClickablePart(
                    text = spanText2_2,
                    textColor = ContextCompat.getColor(requireContext(), R.color.label_assistive),
                    isUnderlined = true,
                ) {
                    setFragmentResult(
                        REQUEST_KEY,
                        bundleOf(KEY_PARAMS_OUT_SHOW_SUBSCRIPTION to ParamsOut.ShowSubscription)
                    )
                    dismiss()
                },
            )
        )
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
        binding.btnBSheetTripGuideCancel.setOnClickListener {
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
        data object ShowSubscription: ParamsOut()
    }

    companion object {

        const val TAG = "TripGuideBottomSheetFragment"

        const val REQUEST_KEY = "trip_guide"
        const val KEY_PARAMS_OUT_SHOW_SUBSCRIPTION = "params_out_show_subscription"

        fun newInstance(bundle: Bundle? = null): TripGuideBottomSheetFragment {
            return TripGuideBottomSheetFragment().apply {
                arguments = bundle
            }
        }
    }
}