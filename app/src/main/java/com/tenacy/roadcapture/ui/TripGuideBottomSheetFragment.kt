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
import com.tenacy.roadcapture.databinding.BSheetTripGuideBinding
import com.tenacy.roadcapture.util.SpannableUtils
import com.tenacy.roadcapture.util.SubscriptionValues
import kotlinx.parcelize.Parcelize

class TripGuideBottomSheetFragment : ExpandedBottomSheetDialogFragment() {

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
        setupListeners()
    }

    private fun setupViews() {
        val spanText1 = "앨범을 생성할 수 없어요."
        val spanFullText1 = "앱을 종료해도 여행은 끝나지 않아요.\n단, 여행 기간이 한 달이 넘어가면 ${spanText1}"

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

        val spanText3 = "수정은 불가능해요."
        val spanFullText3 = "삭제가 가능하지만, $spanText3"

        SpannableUtils.setClickableText(
            requireContext(),
            binding.txtBSheetTripGuideSpan3,
            spanFullText3,
            listOf(
                SpannableUtils.ClickablePart(
                    text = spanText3,
                    textColor = ContextCompat.getColor(requireContext(), R.color.warning),
                ),
            )
        )

        val spanText4_1 = "공유 링크 생성이 불가능해요."
        val spanText4_2 = "여기"
        val spanFullText4 = if(isSubscriptionActive) {
            "공개 앨범은 공유 링크 생성이 가능해요."
        } else {
            "무료 플랜에서는 ${spanText4_1} 생성을 원하시면 ${spanText4_2}를 클릭해주세요."
        }

        SpannableUtils.setClickableText(
            requireContext(),
            binding.txtBSheetTripGuideSpan4,
            spanFullText4,
            listOf(
                SpannableUtils.ClickablePart(
                    text = spanText4_1,
                    textColor = ContextCompat.getColor(requireContext(), R.color.warning),
                ),
                SpannableUtils.ClickablePart(
                    text = spanText4_2,
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

    private fun setupListeners() {
        binding.btnBSheetTripGuideCancel.setSafeClickListener {
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