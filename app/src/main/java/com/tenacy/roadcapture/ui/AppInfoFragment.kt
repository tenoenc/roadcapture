package com.tenacy.roadcapture.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.android.billingclient.api.Purchase
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.databinding.FragmentAppInfoBinding
import com.tenacy.roadcapture.manager.DonationManager
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.manager.SubscriptionManager.SubscriptionPurchaseCallback
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.setQuickTapListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppInfoFragment : BaseFragment(), FragmentVisibilityCallback,
    SubscriptionPurchaseCallback, DonationManager.DonationCallback {

    private var _binding: FragmentAppInfoBinding? = null
    val binding get() = _binding!!

    private val vm: AppInfoViewModel by viewModels()

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    @Inject
    lateinit var donationManager: DonationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppInfoBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onBecameVisible() {
        vm.refreshUserStates()
    }

    override fun onSubscriptionPurchaseCompleted(purchase: Purchase) {
        if (!isAdded()) return

        // 바텀시트 표시
        val bottomSheet = SubscribeAfterBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, SubscribeAfterBottomSheetFragment.TAG)
    }

    override fun onSubscriptionPurchaseFailed(errorCode: Int, errorMessage: String) {
        if (!isAdded) return

        lifecycleScope.launch {
            mainActivity.vm.viewEvent(
                GlobalViewEvent.Toast(
                    ToastModel("구독에 실패했습니다: $errorMessage", ToastMessageType.Warning)
                )
            )
        }
    }

    override fun onDonationCompleted(productId: String, purchase: Purchase) {
        if (!isAdded) return

        val message = when (productId) {
            "donation_small" -> "소중한 후원에 감사드립니다! 더 좋은 서비스로 보답하겠습니다."
            "donation_medium" -> "귀중한 후원에 진심으로 감사드립니다! 더 나은 앱을 만들도록 노력하겠습니다."
            "donation_large" -> "큰 후원에 정말 감사드립니다! 앱 개발과 유지보수에 큰 도움이 됩니다."
            else -> "후원에 감사드립니다! 앱 개선에 큰 도움이 됩니다."
        }

        lifecycleScope.launch {
            mainActivity.vm.viewEvent(
                GlobalViewEvent.Toast(
                    ToastModel(message, ToastMessageType.Success)
                )
            )
        }
    }

    override fun onDonationCancelled() {
        Log.d("Donation", "후원이 취소되었습니다")
    }

    override fun onDonationFailed(errorCode: Int, errorMessage: String) {
        if (!isAdded) return

        lifecycleScope.launch {
            mainActivity.vm.viewEvent(
                GlobalViewEvent.Toast(
                    ToastModel("후원에 실패했습니다: $errorMessage", ToastMessageType.Warning)
                )
            )
        }
    }

    private fun setupListeners() {
        if (BuildConfig.DEBUG) {
            binding.llAppInfoApp.setQuickTapListener(tapCount = 5) {
                findNavController().navigate(MainFragmentDirections.actionMainToDebugSettings())
            }
        }
    }

    private fun setupObservers() {
        observeSubscriptionState()
        observeViewEvents()
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            DonateBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<DonateBeforeBottomSheetFragment.ParamsOut.Donate>(
                DonateBeforeBottomSheetFragment.KEY_PARAMS_OUT_DONATE
            )?.let {
                Log.d("TAG", "Positive Button Clicked!")
                launchDonation(it.type)
            }
        }
    }

    private fun observeSubscriptionState() {
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? AppInfoViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: AppInfoViewEvent) {
        when (event) {
            is AppInfoViewEvent.InquireToDeveloper -> {
                openEmailToContactDeveloper()
            }

            is AppInfoViewEvent.NavigateToHtml -> {
                findNavController().navigate(MainFragmentDirections.actionMainToHtml(event.type))
            }

            is AppInfoViewEvent.Logout -> {
                mainActivity.signOut()
            }

            is AppInfoViewEvent.Donate -> {
                val bottomSheet = DonateBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, DonateBeforeBottomSheetFragment.TAG)
            }

            is AppInfoViewEvent.Subscribe -> {
                // 구독 시작
                subscriptionManager.setPurchaseCallback(this)
                requireActivity().let { activity ->
                    subscriptionManager.subscribe(activity, this)
                }
            }
        }
    }

    // 후원 시작
    private fun launchDonation(donationType: String) {
        donationManager.setDonationCallback(this)
        donationManager.donate(mainActivity, donationType)
    }

    private fun openEmailToContactDeveloper() {
        lifecycleScope.launch {
            try {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("tentenacy@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "[로드캡처] 문의하기")
                    putExtra(
                        Intent.EXTRA_TEXT, """
                    안녕하세요, 로드캡처 개발팀에 문의합니다.
                    
                    앱 버전: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}
                    기기 모델: ${Build.MODEL}
                    안드로이드 버전: ${Build.VERSION.RELEASE}
                    
                    문의 내용:
                    
                """.trimIndent()
                    )
                }
                startActivity(emailIntent)
            } catch (e: Exception) {
                // 오류 처리 및 로깅
                Log.e("EmailError", "이메일을 보낼 수 없습니다: ${e.message}")

                if (isAdded()) {
                    lifecycleScope.launch {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel("이메일을 보낼 수 없습니다: ${e.localizedMessage}", ToastMessageType.Warning)
                            )
                        )
                    }

                    // 대체 방법 제공
                    val clipboardManager =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("개발자 이메일", "tentenacy@gmail.com")
                    clipboardManager.setPrimaryClip(clipData)

                    lifecycleScope.launch {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel("개발자 이메일이 클립보드에 복사되었습니다.", ToastMessageType.Info)
                            )
                        )
                    }
                }
            }
        }
    }
}