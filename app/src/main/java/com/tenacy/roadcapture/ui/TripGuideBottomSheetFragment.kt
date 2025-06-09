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
        val spanText1 = getString(R.string.cannot_create_album)
        val `0` = spanText1
        val spanFullText1 = getString(R.string.travel_continuation_notice, `0`)

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

        val `1` = SubscriptionValues.todayMemoryMaxSize
        val spanText2_1 = getString(R.string.daily_limit, `1`)
        val spanText2_2 = getString(R.string.click_here)
        val `1-0` = spanText2_1
        val `1-1` = spanText2_2
        val spanFullText2 = if(isSubscriptionActive) {
            getString(R.string.daily_memory_limit_notice, `1-0`)
        } else {
            getString(R.string.free_plan_memory_limit, `1-0`, `1-1`)
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

        val spanText3 = getString(R.string.no_editing_possible)
        val `2` = spanText3
        val spanFullText3 = getString(R.string.delete_only_notice, `2`)

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

        val spanText4_1 = getString(R.string.share_link_creation_unavailable)
        val spanText4_2 = getString(R.string.click_here)
        val `3-0` = spanText4_1
        val `3-1` = spanText4_2
        val spanFullText4 = if(isSubscriptionActive) {
            getString(R.string.public_album_share_available)
        } else {
            getString(R.string.free_plan_feature_limit, `3-0`,`3-1`)
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