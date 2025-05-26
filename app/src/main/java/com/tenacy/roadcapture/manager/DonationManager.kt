package com.tenacy.roadcapture.manager

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DonationManager @Inject constructor(
    private val billingManager: BillingManager
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val donationProductIds = listOf(
        "donation_small",   // 예: 2,000원
        "donation_medium",  // 예: 5,000원
        "donation_large"    // 예: 10,000원
    )
    private val donationProductList = donationProductIds.map {
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(it)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
    }

    private val productDetails = mutableMapOf<String, ProductDetails>()

    // 콜백 변수
    private var donationCallback: DonationCallback? = null

    init {
        setupPurchaseListener()
    }

    // 1. 개선된 초기화 및 사전 로드 메서드
    fun initialize(callback: ((Boolean) -> Unit)? = null) {
        preloadDonationProducts(callback)
    }

    private fun setupPurchaseListener() {
        coroutineScope.launch {
            billingManager.purchaseEvents
                .filter { event ->
                    // purchaseType이 DONATION 또는 null이면서 관련 DONATION 작업 중일 때
                    event.purchaseType == BillingManager.PurchaseType.DONATION ||
                            (event.purchaseType == null && billingManager.getCurrentPurchaseType() == BillingManager.PurchaseType.DONATION)
                }
                .collect { event ->
                    handlePurchaseEvent(event)
                }
        }
    }

    fun preloadDonationProducts(callback: ((Boolean) -> Unit)? = null) {
        billingManager.preloadProductDetails(donationProductList) { success, detailsList ->
            if (success && detailsList.isNotEmpty()) {
                // 상품 정보 캐싱
                for (details in detailsList) {
                    productDetails[details.productId] = details
                }
                Log.d(TAG, "후원 상품 정보 사전 로드 성공: ${detailsList.size}개 상품")
                callback?.invoke(true)
            } else {
                Log.e(TAG, "후원 상품 정보 사전 로드 실패")
                callback?.invoke(false)
            }
        }
    }

    private fun handlePurchaseEvent(event: BillingManager.PurchaseEvent) {
        when (event.type) {
            BillingManager.PurchaseEventType.SUCCESS -> {
                event.purchases?.forEach { purchase ->
                    if (donationProductIds.any { purchase.products.contains(it) }) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingManager.PurchaseEventType.CANCELED -> {
                donationCallback?.onDonationCancelled()
            }
            BillingManager.PurchaseEventType.ERROR -> {
                donationCallback?.onDonationFailed(
                    event.billingResult.responseCode,
                    event.billingResult.debugMessage
                )
            }
        }
    }

    // 콜백 설정 메서드
    fun setDonationCallback(callback: DonationCallback?) {
        this.donationCallback = callback
    }

    // 상품 정보 조회
    private fun queryDonationProducts() {
        billingManager.queryProductDetails(donationProductList) { billingResult, detailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (detailsList.isNotEmpty()) {
                    for (details in detailsList) {
                        productDetails[details.productId] = details
                    }
                    Log.d(TAG, "후원 상품 정보 조회 성공: ${detailsList.size}개 상품")
                } else {
                    Log.e(TAG, "후원 상품 정보 없음")
                }
            } else {
                Log.e(TAG, "후원 상품 정보 조회 실패: ${billingResult.debugMessage}")
            }
        }
    }

    // 후원 구매 처리
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 구매 확인 처리
            if (!purchase.isAcknowledged) {
                billingManager.acknowledgePurchase(purchase.purchaseToken) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "후원 구매 확인 완료")

                        // 소비성 상품으로 처리하여 재구매 가능하게 설정
                        consumePurchase(purchase)
                    } else {
                        Log.e(TAG, "후원 구매 확인 실패: ${billingResult.debugMessage}")
                    }
                }
            } else {
                // 이미 확인된 구매
                consumePurchase(purchase)
            }
        }
    }

    // 소비성 상품 소비 처리
    private fun consumePurchase(purchase: Purchase) {
        billingManager.consumePurchase(purchase.purchaseToken) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "소비 처리 완료: ${purchase.products}")

                // 완료 이벤트 발생
                val productId = purchase.products.firstOrNull() ?: ""
                donationCallback?.onDonationCompleted(productId, purchase)
            } else {
                Log.e(TAG, "소비 처리 실패: ${billingResult.debugMessage}")
            }
        }
    }

    // 후원 시작 - 구매 타입 추가
    fun donate(activity: Activity, donationType: String) {
        // 연결 상태 확인 및 필요시 재연결
        billingManager.reconnectIfNeeded()

        if (productDetails.isEmpty()) {
            // 상품 정보가 없으면 다시 조회
            queryDonationProducts()
            donationCallback?.onDonationFailed(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "후원 상품 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요."
            )
            return
        }

        val details = productDetails[donationType]
        if (details == null) {
            donationCallback?.onDonationFailed(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "후원 상품 정보를 찾을 수 없습니다"
            )
            return
        }

        // 구매 타입을 명시적으로 DONATION으로 설정
        val billingResult = billingManager.launchBillingFlow(
            activity,
            details,
            BillingManager.PurchaseType.DONATION
        )

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            donationCallback?.onDonationFailed(
                billingResult.responseCode,
                billingResult.debugMessage
            )
        }
    }

    // 콜백 인터페이스
    interface DonationCallback {
        fun onDonationCompleted(productId: String, purchase: Purchase)
        fun onDonationCancelled()
        fun onDonationFailed(errorCode: Int, errorMessage: String)
    }

    companion object {
        private const val TAG = "DonationManager"
    }
}