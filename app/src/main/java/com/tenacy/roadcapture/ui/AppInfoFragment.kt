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
import com.android.billingclient.api.*
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.databinding.FragmentAppInfoBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import kotlinx.coroutines.launch

class AppInfoFragment: BaseFragment(), FragmentVisibilityCallback {

    private var _binding: FragmentAppInfoBinding? = null
    val binding get() = _binding!!

    private val vm: AppInfoViewModel by viewModels()

    // Billing Client 선언
    private lateinit var billingClient: BillingClient
    private val skuList = listOf(
        "donation_small",   // 예: 2,000원
        "donation_medium",  // 예: 5,000원
        "donation_large"    // 예: 10,000원
    )
    private val skuDetails = mutableMapOf<String, ProductDetails>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm

        // Billing Client 초기화
        setupBillingClient()
        setupFragmentResultListeners()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppInfoBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        // 앱이 포그라운드로 돌아올 때 결제 상품 정보 조회
        if (::billingClient.isInitialized && billingClient.isReady) {
            queryProductDetails()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }

    override fun onBecameVisible() {
        vm.refreshStates()
    }

    private fun setupObservers() {
        observeViewEvents()
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            DonateBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<DonateBeforeBottomSheetFragment.ParamsOut.Donate>(DonateBeforeBottomSheetFragment.KEY_PARAMS_OUT_DONATE)
                ?.let {
                    Log.d("TAG", "Positive Button Clicked!")
                    launchDonation(it.type)
                }
        }
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
            // 새로운 이벤트 타입 추가
            is AppInfoViewEvent.Donate -> {
                val bottomSheet = DonateBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, DonateBeforeBottomSheetFragment.TAG)
            }
        }
    }

    // BillingClient 설정
    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(requireContext())
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // 연결 성공 시 상품 정보 조회
                    queryProductDetails()
                } else {
                    Log.e("Billing", "Billing 클라이언트 설정 실패: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // 연결이 끊어진 경우 재연결 시도
                Log.e("Billing", "Billing 서비스 연결 끊김")
            }
        })
    }

    // 상품 정보 조회
    private fun queryProductDetails() {
        val productList = skuList.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (productDetailsList.isNotEmpty()) {
                    for (productDetails in productDetailsList) {
                        skuDetails[productDetails.productId] = productDetails
                    }
                    Log.d("Billing", "상품 정보 조회 성공: ${productDetailsList.size}개 상품")
                } else {
                    Log.e("Billing", "조회된 상품 정보 없음")
                }
            } else {
                Log.e("Billing", "상품 정보 조회 실패: ${billingResult.debugMessage}")
            }
        }
    }

    // 후원 시작
    private fun launchDonation(donationType: String) {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                    ToastModel("결제 서비스를 초기화 중이에요\n잠시 후 다시 시도해주세요", ToastMessageType.Warning)
                ))
            }
            setupBillingClient()
            return
        }

        val productDetails = skuDetails[donationType]
        if (productDetails == null) {
            lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                    ToastModel("후원 상품 정보를 가져올 수 없어요", ToastMessageType.Warning)
                ))
            }
            queryProductDetails()
            return
        }

        // 구매 플로우 시작
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(requireActivity(), flowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                    ToastModel("후원을 시작할 수 없어요: ${billingResult.debugMessage}", ToastMessageType.Warning)
                ))
            }
        }
    }

    // 구매 결과 리스너
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // 구매 성공
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // 사용자가 취소
            Log.d("Billing", "구매 취소됨")
        } else {
            // 구매 실패
            Log.e("Billing", "구매 실패: ${billingResult.debugMessage}")
            lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                    ToastModel("후원이 완료되지 않았어요", ToastMessageType.Warning)
                ))
            }
        }
    }

    // 구매 처리
    private fun handlePurchase(purchase: Purchase) {
        // 구매 상태 확인
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 구매 확인 처리
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // 구매 확인 완료
                        showThankYouMessage(purchase)
                    }
                }
            } else {
                // 이미 확인된 구매
                showThankYouMessage(purchase)
            }

            // 소비성 상품으로 처리하여 재구매 가능하게 설정
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.consumeAsync(consumeParams) { billingResult, _ ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "소비 처리 완료: ${purchase.products}")
                }
            }
        }
    }

    // 감사 메시지 표시
    private fun showThankYouMessage(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        val message = when (productId) {
            "donation_small" -> "소중한 후원에 감사드립니다! 더 좋은 서비스로 보답하겠습니다."
            "donation_medium" -> "귀중한 후원에 진심으로 감사드립니다! 더 나은 앱을 만들도록 노력하겠습니다."
            "donation_large" -> "큰 후원에 정말 감사드립니다! 앱 개발과 유지보수에 큰 도움이 됩니다."
            else -> "후원에 감사드립니다! 앱 개선에 큰 도움이 됩니다."
        }

        lifecycleScope.launch {
            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                ToastModel(message, ToastMessageType.Success)
            ))
        }
    }

    private fun openEmailToContactDeveloper() {
        lifecycleScope.launch {
            try {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("tentenacy@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "[로드캡처] 문의하기")
                    putExtra(Intent.EXTRA_TEXT, """
                    안녕하세요, 로드캡처 개발팀에 문의합니다.
                    
                    앱 버전: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}
                    기기 모델: ${Build.MODEL}
                    안드로이드 버전: ${Build.VERSION.RELEASE}
                    
                    문의 내용:
                    
                """.trimIndent())
                }
                startActivity(emailIntent)
            } catch (e: Exception) {
                // 오류 처리 및 로깅
                Log.e("EmailError", "이메일을 보낼 수 없습니다: ${e.message}")
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("이메일을 보낼 수 없습니다: ${e.localizedMessage}", ToastMessageType.Warning)))

                // 대체 방법 제공
                val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("개발자 이메일", "tentenacy@gmail.com")
                clipboardManager.setPrimaryClip(clipData)
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("개발자 이메일이 클립보드에 복사되었습니다.", ToastMessageType.Info)))
            }
        }
    }
}