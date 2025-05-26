package com.tenacy.roadcapture.manager

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.util.auth
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.toFirebaseTimestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 구독 관리 클래스
 *
 * Google Play Billing 및 Firestore와 연계하여 구독을 관리합니다.
 * - 구독 시작 및 확인
 * - 구독 상태 관리
 * - 여러 앱 계정에서의 구독 공유 방지
 */
@Singleton
class SubscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager,
) {
    // 상수 정의
    companion object {
        private const val TAG = "SubscriptionManager"

        // 구독 관련 상수
        private const val SUBSCRIPTION_PRODUCT_ID = "subscription_premium"
        private const val SUBSCRIPTION_TYPE = "premium"

        // 테스트 모드 시간 설정 (5분)
        private const val TEST_SUBSCRIPTION_EXTENSION_MS = 5 * 60 * 1000L

        // 프로덕션 환경 구독 기간 (1개월)
        private const val PRODUCTION_SUBSCRIPTION_MONTHS = 1

        // Firestore 컬렉션/필드명
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_SUBSCRIPTIONS = "subscriptions"
        private const val FIELD_IS_SUBSCRIPTION_ACTIVE = "isSubscriptionActive"
        private const val FIELD_SUBSCRIPTION_AUTO_RENEWING = "subscriptionAutoRenewing"
        private const val FIELD_SUBSCRIPTION_EXPIRED_AT = "subscriptionExpiredAt"
        private const val FIELD_PURCHASE_TOKEN = "purchaseToken"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_GOOGLE_ACCOUNT_ID = "googleAccountId"
        private const val FIELD_EXPIRY_TIME = "expiryTime"
        private const val FIELD_AUTO_RENEWING = "autoRenewing"
        private const val FIELD_SUBSCRIPTION_HISTORY = "subscriptionHistory"

        // 구독 액션 타입
        private const val ACTION_PURCHASED = "PURCHASED"
        private const val ACTION_RENEWED = "RENEWED"
        private const val ACTION_EXPIRED = "EXPIRED"
        private const val ACTION_CANCELED = "CANCELED"
    }

    // 코루틴 스코프
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 구독 상태 Flow
    private val _subscriptionState = MutableStateFlow(SubscriptionState())
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    // 구독 상품 설정
    private val subscriptionProductIds = listOf(SUBSCRIPTION_PRODUCT_ID)
    private val subscriptionProductList = subscriptionProductIds.map {
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId(it)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
    }

    // 구독 상품 정보 캐시
    private var productDetails: MutableMap<String, ProductDetails> = mutableMapOf()
    private var subscriptionOfferToken: String? = null

    // 리스너 설정 상태 및 콜백
    private var listenerSetup = false
    private var purchaseCallback: SubscriptionPurchaseCallback? = null

    // 로깅용 날짜 포맷
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        setupPurchaseListener()
    }

    /**
     * 초기화 및 사전 로드 메서드
     *
     * 구독 관리자를 초기화하고 상품 정보를 사전에 로드합니다.
     * @param callback 초기화 완료 후 호출될 콜백
     */
    fun initialize(callback: ((Boolean) -> Unit)? = null) {
        if (!listenerSetup) {
            setupPurchaseListener()
        }
        preloadSubscriptionProducts(callback)
    }

    /**
     * 구독 이벤트 리스너 설정
     *
     * BillingManager의 구독 이벤트를 수신하여 처리합니다.
     */
    private fun setupPurchaseListener() {
        if (listenerSetup) {
            Log.d(TAG, "리스너가 이미 설정되어 있음")
            return
        }

        coroutineScope.launch {
            billingManager.purchaseEvents
                .filter { event ->
                    event.purchaseType == BillingManager.PurchaseType.SUBSCRIPTION ||
                            (event.purchaseType == null && billingManager.getCurrentPurchaseType() == BillingManager.PurchaseType.SUBSCRIPTION)
                }
                .collect { event ->
                    Log.d(TAG, "구독 이벤트 수신: 타입=${event.type}")
                    handlePurchaseEvent(event)
                }
        }

        listenerSetup = true
        Log.d(TAG, "구독 이벤트 리스너 설정 완료")
    }

    /**
     * 구독 상품 정보 사전 로드
     *
     * Google Play에서 구독 상품 정보를 미리 로드하여 캐싱합니다.
     * @param callback 로드 완료 후 호출될 콜백
     */
    private fun preloadSubscriptionProducts(callback: ((Boolean) -> Unit)? = null) {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "네트워크 연결 없음")
            callback?.invoke(false)
            return
        }

        billingManager.preloadProductDetails(subscriptionProductList) { success, detailsList ->
            if (success && detailsList.isNotEmpty()) {
                // 상품 정보 캐싱
                for (details in detailsList) {
                    productDetails[details.productId] = details

                    // 구독 상품의 경우 제공 토큰 저장
                    if (details.productType == BillingClient.ProductType.SUBS) {
                        val offers = details.subscriptionOfferDetails
                        if (!offers.isNullOrEmpty()) {
                            subscriptionOfferToken = offers[0].offerToken
                        }
                    }
                }
                Log.d(TAG, "구독 상품 정보 사전 로드 성공: ${detailsList.size}개 상품")
                callback?.invoke(true)
            } else {
                Log.e(TAG, "구독 상품 정보 사전 로드 실패")
                callback?.invoke(false)
            }
        }
    }

    /**
     * 구독 이벤트 처리
     *
     * BillingManager로부터 받은 구독 이벤트를 처리합니다.
     * @param event 처리할 구독 이벤트
     */
    private suspend fun handlePurchaseEvent(event: BillingManager.PurchaseEvent) {
        when (event.type) {
            BillingManager.PurchaseEventType.SUCCESS -> {
                event.purchases?.forEach { purchase ->
                    if (subscriptionProductIds.any { purchase.products.contains(it) }) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingManager.PurchaseEventType.ERROR -> {
                purchaseCallback?.onSubscriptionPurchaseFailed(
                    event.billingResult.responseCode,
                    event.billingResult.debugMessage
                )
            }
            BillingManager.PurchaseEventType.CANCELED -> {
                Log.d(TAG, "구독이 취소되었습니다")
            }
        }
    }

    /**
     * 구독 구매 콜백 설정
     *
     * @param callback 구독 구매 결과를 수신할 콜백
     */
    fun setPurchaseCallback(callback: SubscriptionPurchaseCallback?) {
        this.purchaseCallback = callback
    }

    /**
     * 구독 상태 확인
     *
     * 로컬, Google Play 및 Firestore에서 현재 구독 상태를 확인합니다.
     * 또한 현재 Google 계정의 구독이 다른 앱 계정에 연결되어 있는지 확인합니다.
     */
    fun checkSubscriptionStatus() {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "구독 상태 확인 시작")

        // 네트워크 연결 확인
        if (!isNetworkAvailable()) {
            Log.d(TAG, "네트워크 연결 없음, 오프라인 모드로 확인")
            handleOfflineSubscriptionCheck(currentTime)
            return
        }

        // 현재 앱 계정 검증
        val currentUserId = UserPref.id
        if (currentUserId.isEmpty()) {
            Log.d(TAG, "사용자 ID가 없음, 구독 비활성화")
            updateLocalSubscription(false, null, 0)
            _subscriptionState.value = SubscriptionState(isActive = false)
            return
        }

        // Google Play에서 구독 정보 조회
        Log.d(TAG, "Google Play에서 구독 정보 조회 중")
        billingManager.queryPurchases(BillingClient.ProductType.SUBS) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Google Play 구독 정보 조회 성공: ${purchases.size}개 구독 발견")

                coroutineScope.launch {
                    try {
                        // 해시된 Google 계정 ID 가져오기 (구독 연결 확인용)
                        val googleAccountId = getGoogleAccountId()
                        Log.d(TAG, "Google 계정 ID(해시): ${googleAccountId.take(8)}...")

                        // Google Play 구독 처리
                        handleQueryPurchasesResult(purchases, currentTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "구독 확인 중 오류 발생", e)
                        checkFirestoreSubscriptionStatus()
                    }
                }
            } else {
                Log.d(TAG, "Google Play 구독 조회 실패: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                // Google Play 조회 실패 시 Firestore 확인
                checkFirestoreSubscriptionStatus()
            }
        }
    }

    suspend fun checkSubscriptionStatusSuspend(): SubscriptionState {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "구독 상태 확인 시작")

        // 네트워크 연결 확인
        if (!isNetworkAvailable()) {
            Log.d(TAG, "네트워크 연결 없음, 오프라인 모드로 확인")
            return handleOfflineSubscriptionCheck(currentTime)
        }

        // 현재 앱 계정 검증
        val currentUserId = UserPref.id
        if (currentUserId.isEmpty()) {
            Log.d(TAG, "사용자 ID가 없음, 구독 비활성화")
            updateLocalSubscription(false, null, 0)
            return SubscriptionState(isActive = false).also { _subscriptionState.value = it }
        }

        // Google Play에서 구독 정보 조회
        Log.d(TAG, "Google Play에서 구독 정보 조회 중")

        return try {
            suspendCancellableCoroutine<SubscriptionState> { cont ->
                billingManager.queryPurchases(BillingClient.ProductType.SUBS) { billingResult, purchases ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Google Play 구독 정보 조회 성공: ${purchases.size}개 구독 발견")

                        CoroutineScope(cont.context).launch {
                            try {
                                // 해시된 Google 계정 ID 가져오기 (구독 연결 확인용)
                                val googleAccountId = getGoogleAccountId()
                                Log.d(TAG, "Google 계정 ID(해시): ${googleAccountId.take(8)}...")

                                // Google Play 구독 처리
                                handleQueryPurchasesResult(purchases, currentTime, cont)
                            } catch (e: Exception) {
                                Log.e(TAG, "구독 확인 중 오류 발생", e)
                                checkFirestoreSubscriptionStatus(cont)
                            }
                        }
                    } else {
                        Log.d(TAG, "Google Play 구독 조회 실패: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                        // Google Play 조회 실패 시 Firestore 확인
                        checkFirestoreSubscriptionStatus(cont)
                    }
                }
            }
        } catch (ignored: Exception) {
            getDefaultSubscriptionState()
        }
    }

    /**
     * Google Play 구독 조회 결과 처리 ( V )
     *
     * 1. 활성 구독이 있는지 확인
     * 2. 해당 구독이 현재 앱 계정에 연결되어 있는지 확인
     * 3. 다른 앱 계정에 연결된 경우 적절히 처리
     * 4. 정상적인 경우 구독 상태 업데이트
     */
    private suspend fun handleQueryPurchasesResult(
        purchases: List<Purchase>,
        currentTime: Long,
        cont: CancellableContinuation<SubscriptionState>? = null,
    ) {
        try {
            // 활성 구독 찾기
            val activeSubscription = purchases.find {
                it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAcknowledged
            }

            if (activeSubscription != null) {
                Log.d(TAG, "활성 구독 발견: 토큰=${activeSubscription.purchaseToken.take(8)}...")

                val googleAccountId = getGoogleAccountId()

                // 구독 해지 상태 확인 - 자동 갱신 변경 감지
                checkSubscriptionCancellation(activeSubscription)

                // 이 구독이 다른 앱 계정에 연결되어 있는지 확인
                val subscriptionRef = db.collection(COLLECTION_SUBSCRIPTIONS)
                    .document(activeSubscription.purchaseToken)

                val subscriptionDoc = subscriptionRef.get().await()

                if (subscriptionDoc.exists()) {
                    Log.d(TAG, "구독 정보가 Firestore에 존재함")
                    val linkedUserId = subscriptionDoc.getString(FIELD_USER_ID)
                    val currentUserId = UserPref.id

                    if (linkedUserId != null && linkedUserId != currentUserId) {
                        Log.d(TAG, "이 구독은 다른 앱 계정($linkedUserId)에 연결되어 있음")
                        // 이미 다른 앱 계정에 연결된 구독
                        handleSubscriptionLinkedToOtherAccount(linkedUserId, cont)
                        return
                    } else {
                        Log.d(TAG, "구독이 현재 계정에 올바르게 연결되어 있음")
                    }
                } else {
                    Log.d(TAG, "구독 정보가 Firestore에 존재하지 않음, 새로 생성 예정")
                }

                // 구독 처리 진행
                processSubscription(activeSubscription, googleAccountId, currentTime, cont)
            } else {
                Log.d(TAG, "활성 구독 찾을 수 없음")
                handleNoActiveSubscription(cont)
            }
        } catch (e: Exception) {
            Log.e(TAG, "구독 확인 중 오류 발생", e)
            checkFirestoreSubscriptionStatus(cont)
        }
    }

    /**
     * 구독 해지 상태 확인 및 처리
     * 사용자가 구독을 해지했을 때 이력 추가 및 상태 업데이트
     */
    private suspend fun checkSubscriptionCancellation(purchase: Purchase) {
        val userId = UserPref.id
        if (userId.isEmpty()) return

        try {
            // 1. 구독 문서 조회
            val subscriptionRef = db.collection(COLLECTION_SUBSCRIPTIONS)
                .document(purchase.purchaseToken)

            val subscriptionDoc = subscriptionRef.get().await()

            if (subscriptionDoc.exists()) {
                val linkedUserId = subscriptionDoc.getString(FIELD_USER_ID)

                // 현재 사용자 구독만 처리
                if (linkedUserId == userId) {
                    val wasAutoRenewing = subscriptionDoc.getBoolean(FIELD_AUTO_RENEWING) ?: false
                    val isAutoRenewing = purchase.isAutoRenewing

                    // 자동 갱신이 해제된 경우 (취소됨)
                    if (wasAutoRenewing && !isAutoRenewing) {
                        Log.d(TAG, "구독 해지 감지: 자동 갱신 상태 변경됨 (true → false)")

                        // 해지 이력 추가
                        addSubscriptionHistory(userId, ACTION_CANCELED, purchase)

                        // 구독 문서 업데이트
                        subscriptionRef.update(mapOf(
                            FIELD_AUTO_RENEWING to false
                        )).await()

                        // 사용자 문서 업데이트
                        db.collection(COLLECTION_USERS).document(userId)
                            .update(FIELD_SUBSCRIPTION_AUTO_RENEWING, false)
                            .await()

                        Log.d(TAG, "구독 해지 처리 완료")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "구독 해지 확인 중 오류 발생", e)
        }
    }

    /**
     * 다른 계정에 연결된 구독 처리 ( V )
     *
     * 이 메서드는 현재 Google 계정의 구독이 다른 앱 계정에 연결된 경우를 처리합니다.
     * 1. 로컬 구독 상태를 비활성화
     * 2. 구독 상태 Flow 업데이트 (UI에서 특별한 메시지 표시용)
     */
    private fun handleSubscriptionLinkedToOtherAccount(linkedUserId: String, cont: CancellableContinuation<SubscriptionState>? = null) {
        Log.d(TAG, "이 Google 계정의 구독은 다른 앱 계정($linkedUserId)에 연결되어 있습니다")

        // 현재 사용자의 구독 상태를 비활성화
        updateLocalSubscription(false, null, 0)

        // 구독 상태 업데이트 - 다른 계정에 연결됨 표시
        val subscriptionState = SubscriptionState(
            isActive = false,
            isLinkedToOtherAccount = true,
            linkedAccountId = linkedUserId
        ).also { _subscriptionState.value = it }

        // 마스킹된 계정 ID 생성 (처음 3자리 + "*****" + 마지막 2자리)
        val maskedId = if (linkedUserId.length > 5) {
            "${linkedUserId.take(3)}*****${linkedUserId.takeLast(2)}"
        } else {
            "다른 계정"
        }

        Log.d(TAG, "사용자 알림: 이 Google 계정의 구독은 계정 $maskedId 에 연결되어 있습니다. " +
                "현재 계정으로 혜택을 받으려면 Play 스토어에서 기존 구독을 해지하고 새로 구독해주세요.")

        cont?.resume(subscriptionState)
    }

    /**
     * 구독 처리 ( V )
     */
    private suspend fun processSubscription(
        purchase: Purchase,
        googleAccountId: String,
        currentTime: Long,
        cont: CancellableContinuation<SubscriptionState>? = null
    ) {
        // 테스트 환경에서 자동 갱신 처리
        if (purchase.isAutoRenewing && BuildConfig.DEBUG) {
            handleTestModeAutoRenewing(purchase, googleAccountId)
            cont?.resume(getDefaultSubscriptionState())
            return
        }

        // 만료 시간 계산
        val expiryTime = calculateExpiryTime(purchase.purchaseTime)

        // 만료 여부 확인
        if (expiryTime <= currentTime) {
            handleExpiredSubscription(purchase, cont)
            return
        }

        // 현재 상태 확인
        val currentUserId = UserPref.id
        val previousToken = SubscriptionPref.purchaseToken
        val previousExpiryTime = SubscriptionPref.subscriptionExpiryTime

        val isSameToken = previousToken == purchase.purchaseToken
        val isAlreadyActive = SubscriptionPref.isSubscriptionActive

        // 갱신 감지
        val isRenewal = isSameToken && isAlreadyActive &&
                previousExpiryTime > 0 && expiryTime > previousExpiryTime

        when {
            isRenewal -> {
                handleSubscriptionRenewal(purchase, googleAccountId, previousExpiryTime, expiryTime)
                cont?.resume(getDefaultSubscriptionState())
            }
            isSameToken && isAlreadyActive -> {
                updateLocalSubscription(true, purchase, expiryTime)
                updateSubscriptionStateWithCancelInfo(true, purchase, expiryTime, cont)
            }
            else -> {
                handleNewSubscription(purchase, googleAccountId, expiryTime)
                cont?.resume(getDefaultSubscriptionState())
            }
        }
    }

    /**
     * 테스트 모드 자동 갱신 처리
     */
    private suspend fun handleTestModeAutoRenewing(purchase: Purchase, googleAccountId: String) {
        // 테스트 모드에서는 현재 시간 + 5분으로 만료 시간 설정
        val newExpiryTime = System.currentTimeMillis() + TEST_SUBSCRIPTION_EXTENSION_MS

        // 로컬 상태 업데이트
        updateLocalSubscription(true, purchase, newExpiryTime)

        // 구독 상태 업데이트
        updateSubscriptionStateWithCancelInfo(true, purchase, newExpiryTime)

        // Firestore 업데이트
        try {
            updateSubscriptionInFirestore(purchase, googleAccountId, newExpiryTime)
        } catch (e: Exception) {
            Log.e(TAG, "테스트 모드 구독 정보 저장 실패", e)
        }
    }

    /**
     * 만료된 구독 처리 ( V )
     */
    private suspend fun handleExpiredSubscription(purchase: Purchase, cont: CancellableContinuation<SubscriptionState>? = null) {
        if (SubscriptionPref.isSubscriptionActive) {
            // 로컬 상태 업데이트
            updateLocalSubscription(false, null, 0)

            // 구독 상태 업데이트
            val subscriptionState = SubscriptionState(isActive = false).also { _subscriptionState.value = it }

            // Firestore 업데이트
            val userId = UserPref.id
            if (userId.isNotEmpty()) {
                try {
                    // 만료 이력 추가
                    addSubscriptionHistory(userId, ACTION_EXPIRED, purchase)
                } catch (e: Exception) {
                    Log.e(TAG, "구독 만료 이력 추가 실패", e)
                }
            }
            cont?.resume(subscriptionState)
        } else {
            // 이미 비활성 상태
            val subscriptionState = SubscriptionState(isActive = false).also { _subscriptionState.value = it }
            cont?.resume(subscriptionState)
        }
    }

    /**
     * 구독 갱신 처리
     */
    private suspend fun handleSubscriptionRenewal(
        purchase: Purchase,
        googleAccountId: String,
        previousExpiryTime: Long,
        newExpiryTime: Long
    ) {
        // 로컬 상태 업데이트
        updateLocalSubscription(true, purchase, newExpiryTime)

        // 구독 상태 업데이트
        updateSubscriptionStateWithCancelInfo(true, purchase, newExpiryTime)

        // Firestore 업데이트
        val userId = UserPref.id
        if (userId.isNotEmpty()) {
            try {
                // 구독 정보 업데이트
                updateSubscriptionInFirestore(purchase, googleAccountId, newExpiryTime)

                // 별도로 갱신 이력 추가 (구매 이력과 다른 타입)
                addSubscriptionHistory(
                    userId,
                    ACTION_RENEWED,
                    purchase,
                    previousExpiryTime,
                    newExpiryTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "구독 갱신 정보 저장 실패", e)
            }
        }
    }

    /**
     * 새 구독 처리
     */
    private suspend fun handleNewSubscription(
        purchase: Purchase,
        googleAccountId: String,
        expiryTime: Long
    ) {
        // 로컬 상태 업데이트
        updateLocalSubscription(true, purchase, expiryTime)

        // 구독 상태 업데이트
        updateSubscriptionStateWithCancelInfo(true, purchase, expiryTime)

        // Firestore 업데이트
        val userId = UserPref.id
        if (userId.isNotEmpty()) {
            try {
                // 구독 정보 업데이트 - 이력 추가는 이 메서드 내에서 처리됨
                updateSubscriptionInFirestore(purchase, googleAccountId, expiryTime)
            } catch (e: Exception) {
                Log.e(TAG, "새 구독 정보 저장 실패", e)
            }
        }
    }

    /**
     * 활성 구독이 없는 경우 처리 ( V )
     */
    private suspend fun handleNoActiveSubscription(cont: CancellableContinuation<SubscriptionState>? = null) {
        if (SubscriptionPref.isSubscriptionActive) {
            // 로컬 상태 업데이트
            updateLocalSubscription(false, null, 0)

            // 구독 상태 업데이트
            val subscriptionState = SubscriptionState(isActive = false).also { _subscriptionState.value = it }

            // Firestore 업데이트
            val userId = UserPref.id
            if (userId.isNotEmpty()) {
                try {
                    // 만료 이력 추가 (구매 정보 없음)
                    addSubscriptionHistory(userId, ACTION_EXPIRED)
                } catch (e: Exception) {
                    Log.e(TAG, "구독 만료 이력 추가 실패", e)
                    cont?.resumeWithException(e)
                    return
                }
            }
            cont?.resume(subscriptionState)
        } else {
            // 이미 비활성 상태
            val subscriptionState = SubscriptionState(isActive = false).also { _subscriptionState.value = it }
            cont?.resume(subscriptionState)
        }
    }

    /**
     * 오프라인 모드에서 구독 상태 확인
     */
    private fun handleOfflineSubscriptionCheck(currentTime: Long): SubscriptionState {
        val isActive = SubscriptionPref.isSubscriptionActive
        val expiryTime = SubscriptionPref.subscriptionExpiryTime
        val isCanceled = SubscriptionPref.isSubscriptionCancelled

        return if (isActive && expiryTime > currentTime) {
            // 로컬 상태가 유효함
            SubscriptionState(
                isActive = true,
                isCanceled = isCanceled,
                expiryDate = expiryTime
            ).also { _subscriptionState.value = it }
        } else if (isActive) {
            // 로컬 만료 시간이 지남
            updateLocalSubscription(false, null, 0)
            SubscriptionState(isActive = false).also { _subscriptionState.value = it }
        } else {
            // 이미 비활성 상태
            SubscriptionState(isActive = false).also { _subscriptionState.value = it }
        }
    }

    /**
     * Firestore에서 구독 상태 확인 ( V )
     */
    private fun checkFirestoreSubscriptionStatus(cont: CancellableContinuation<SubscriptionState>? = null) {
        val userId = UserPref.id
        if (userId.isEmpty()) {
            updateLocalSubscription(false, null, 0)
            val subscriptionState = SubscriptionState(isActive = false).also { _subscriptionState.value = it }
            cont?.resume(subscriptionState)
            return
        }

        db.collection(COLLECTION_USERS).document(userId)
            .get()
            .addOnSuccessListener { document ->
                val subscriptionState = if (document.exists()) {
                    val isActive = document.getBoolean(FIELD_IS_SUBSCRIPTION_ACTIVE) ?: false
                    val expiryTimestamp = document.getTimestamp(FIELD_SUBSCRIPTION_EXPIRED_AT)
                    val purchaseToken = document.getString(FIELD_PURCHASE_TOKEN)
                    val isAutoRenewing = document.getBoolean(FIELD_SUBSCRIPTION_AUTO_RENEWING) ?: false

                    if (isActive && expiryTimestamp != null) {
                        val expiryTime = expiryTimestamp.toDate().time
                        val currentTime = System.currentTimeMillis()

                        if (expiryTime > currentTime) {
                            // 구독이 유효함 - 로컬에 토큰만 업데이트
                            SubscriptionPref.apply {
                                this.isSubscriptionActive = true
                                this.subscriptionExpiryTime = expiryTime
                                this.purchaseToken = purchaseToken ?: ""
                                this.isSubscriptionCancelled = !isAutoRenewing
                                this.lastSubscriptionCheckTime = System.currentTimeMillis()
                            }

                            SubscriptionState(
                                isActive = true,
                                isCanceled = !isAutoRenewing,
                                expiryDate = expiryTime
                            ).also { _subscriptionState.value = it }
                        } else {
                            // 구독이 만료됨
                            updateLocalSubscription(false, null, 0)
                            SubscriptionState(isActive = false).also { _subscriptionState.value = it }
                        }
                    } else {
                        // 구독 정보가 없거나 비활성
                        updateLocalSubscription(false, null, 0)
                        SubscriptionState(isActive = false).also { _subscriptionState.value = it }
                    }
                } else {
                    // 사용자 문서가 없음
                    updateLocalSubscription(false, null, 0)
                    SubscriptionState(isActive = false).also { _subscriptionState.value = it }
                }
                cont?.resume(subscriptionState)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore 구독 상태 확인 실패", e)

                val subscriptionState = getDefaultSubscriptionState().also { _subscriptionState.value = it }
                cont?.resume(subscriptionState)
            }
    }

    private fun getDefaultSubscriptionState(): SubscriptionState {
        // 현재 로컬 상태 유지
        val isActive = SubscriptionPref.isSubscriptionActive
        val expiryTime = SubscriptionPref.subscriptionExpiryTime
        val isCanceled = SubscriptionPref.isSubscriptionCancelled
        val currentTime = System.currentTimeMillis()

        return if (isActive && expiryTime > currentTime) {
            SubscriptionState(
                isActive = true,
                isCanceled = isCanceled,
                expiryDate = expiryTime
            )
        } else {
            updateLocalSubscription(false, null, 0)
            SubscriptionState(isActive = false)
        }
    }

    /**
     * 구독 처리
     *
     * 구매된 구독을 처리하고 적절한 후속 조치를 취합니다.
     * @param purchase 처리할 구독 구매 정보
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // 만료 시간 계산
            val expiryTime = calculateExpiryTime(purchase.purchaseTime)

            // 구매 확인 처리
            if (!purchase.isAcknowledged) {
                try {
                    acknowledgePurchase(purchase, expiryTime)
                } catch (ignored: Exception) {}
            } else {
                try {
                    val googleAccountId = getGoogleAccountId()
                    processSubscription(purchase, googleAccountId, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e(TAG, "구독 처리 중 오류 발생", e)
                }
            }
        }
    }

    /**
     * 구매 확인 처리
     */
    private suspend fun acknowledgePurchase(purchase: Purchase, expiryTime: Long) {
        Log.d(TAG, "구매 확인 시작: 토큰=${purchase.purchaseToken.take(8)}, 만료 시간=${dateFormat.format(Date(expiryTime))}")

        suspendCancellableCoroutine { cont ->
            billingManager.acknowledgePurchase(purchase.purchaseToken) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "구매 확인 성공 (acknowledgePurchase)")

                    CoroutineScope(cont.context).launch {
                        try {
                            // 사용자 ID와 Google 계정 ID 로그
                            val userId = UserPref.id
                            val googleAccountId = getGoogleAccountId()
                            Log.d(TAG, "구독 저장 정보: 사용자 ID=$userId, Google 계정=${googleAccountId.take(8)}...")

                            if (userId.isEmpty()) {
                                Log.e(TAG, "사용자 ID가 없음, Firestore 업데이트 불가")
                                return@launch
                            }

                            // 로컬 업데이트
                            updateLocalSubscription(true, purchase, expiryTime)
                            Log.d(TAG, "로컬 구독 상태 업데이트 완료")

                            // 구독 상태 업데이트
                            val subscriptionState =
                                updateSubscriptionStateWithCancelInfo(true, purchase, expiryTime)
                            Log.d(TAG, "구독 상태 Flow 업데이트 완료")

                            // Firestore 업데이트
                            Log.d(TAG, "Firestore 업데이트 시작")
                            updateSubscriptionInFirestore(purchase, googleAccountId, expiryTime)
                            Log.d(TAG, "Firestore 업데이트 종료")

                            // 구독 완료 콜백 호출
                            Log.d(TAG, "구독 완료 콜백 호출")
                            purchaseCallback?.onSubscriptionPurchaseCompleted(purchase)

                            cont.resume(subscriptionState)
                        } catch (e: Exception) {
                            Log.e(TAG, "구독 확인 후 처리 중 오류 발생", e)
                            cont.resumeWithException(e)
                        }
                    }
                } else {
                    val errorMessage = "구매 확인 실패: 코드=${billingResult.responseCode}, 메시지=${billingResult.debugMessage}"
                    Log.e(TAG, errorMessage)
                    cont.resumeWithException(RuntimeException(errorMessage))
                }
            }
        }
    }

    /**
     * 만료 시간 계산
     *
     * 구매 시간을 기준으로 만료 시간을 계산합니다.
     * @param purchaseTime 구매 시간 (밀리초)
     * @return 만료 시간 (밀리초)
     */
    private fun calculateExpiryTime(purchaseTime: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = purchaseTime

        if (BuildConfig.DEBUG) {
            // 테스트 모드: 5분 후 만료
            calendar.add(Calendar.MINUTE, 5)
        } else {
            // 프로덕션 모드: 한 달 후 만료
            calendar.add(Calendar.MONTH, PRODUCTION_SUBSCRIPTION_MONTHS)
        }

        return calendar.timeInMillis
    }

    /**
     * 로컬 구독 정보 업데이트
     *
     * 구독 상태를 로컬 환경설정에 저장합니다.
     * purchase가 null이면서 isActive가 true인 경우는 Firestore에서 구독 정보를
     * 가져왔지만 실제 Purchase 객체는 없는 상황을 처리하기 위한 것입니다.
     */
    private fun updateLocalSubscription(isActive: Boolean, purchase: Purchase?, expiryTime: Long) {
        SubscriptionPref.apply {
            this.isSubscriptionActive = isActive
            this.subscriptionExpiryTime = expiryTime

            if (isActive && purchase != null) {
                this.isSubscriptionCancelled = !purchase.isAutoRenewing
                this.purchaseToken = purchase.purchaseToken
                this.subscriptionPurchaseTime = purchase.purchaseTime
                this.subscriptionType = SUBSCRIPTION_TYPE
            } else if (isActive) {
                // Firestore에서 구독 정보를 복원한 경우 (Purchase 객체 없이)
                // 기존 토큰은 유지하고 타입만 설정
                this.isSubscriptionCancelled = false
                this.subscriptionType = SUBSCRIPTION_TYPE
                // 토큰이 없는 경우는 이미 호출 전에 처리되어야 함
            }

            if (!isActive) {
                this.isSubscriptionActive = false
                this.subscriptionExpiryTime = 0
                this.isSubscriptionCancelled = true
                this.purchaseToken = ""
                this.subscriptionPurchaseTime = 0
                this.subscriptionType = ""
            }

            this.lastSubscriptionCheckTime = System.currentTimeMillis()
        }
    }

    /**
     * 구독 상태 업데이트 메서드에서 해지 상태 반영 ( V )
     */
    private fun updateSubscriptionStateWithCancelInfo(
        isActive: Boolean,
        purchase: Purchase?,
        expiryTime: Long,
        cont: CancellableContinuation<SubscriptionState>? = null,
    ): SubscriptionState {
        // 해지 여부는 구독이 활성 상태이면서 자동 갱신이 꺼진 경우
        val isCanceled = isActive && purchase != null && !purchase.isAutoRenewing

        val subscriptionState = SubscriptionState(
            isActive = isActive,
            isLinkedToOtherAccount = false,
            linkedAccountId = "",
            isCanceled = isCanceled,
            expiryDate = if (isActive) expiryTime else 0
        ).also { _subscriptionState.value = it }
        cont?.resume(subscriptionState)

        return subscriptionState
    }

    /**
     * Firestore에 구독 정보 업데이트
     */
    private suspend fun updateSubscriptionInFirestore(
        purchase: Purchase,
        googleAccountId: String,
        expiryTime: Long
    ) {
        val userId = UserPref.id
        if (userId.isEmpty()) {
            Log.e(TAG, "Firestore 업데이트 실패: 사용자 ID가 비어 있음")
            return
        }

        Log.d(TAG, "Firestore 구독 정보 업데이트 시작: 사용자=$userId, 토큰=${purchase.purchaseToken.take(8)}...")

        try {
            // 1. 먼저 사용자 문서가 존재하는지 확인
            val userDoc = db.collection(COLLECTION_USERS).document(userId).get().await()
            if (!userDoc.exists()) {
                Log.e(TAG, "Firestore 사용자 문서가 존재하지 않음: $userId")
                return
            }

            // 2. 트랜잭션 수정 - 모든 읽기 작업을 먼저 수행
            db.runTransaction { transaction ->
                // 먼저 필요한 모든 문서 읽기
                val subscriptionRef = db.collection(COLLECTION_SUBSCRIPTIONS)
                    .document(purchase.purchaseToken)
                val subscriptionDoc = transaction.get(subscriptionRef)

                // 다른 사용자 ID가 있는 경우에 대비한 읽기
                var existingUserRef: com.google.firebase.firestore.DocumentReference? = null

                if (subscriptionDoc.exists()) {
                    val existingUserId = subscriptionDoc.getString(FIELD_USER_ID)
                    if (existingUserId != null && existingUserId != userId) {
                        existingUserRef = db.collection(COLLECTION_USERS).document(existingUserId)
                        // 기존 사용자 문서 읽기 (트랜잭션 내)
                        transaction.get(existingUserRef)
                    }
                }

                // 이제 모든 읽기가 완료되었으므로 쓰기 작업 시작

                // 사용자 문서 업데이트
                val userRef = db.collection(COLLECTION_USERS).document(userId)
                transaction.update(userRef, mapOf(
                    FIELD_IS_SUBSCRIPTION_ACTIVE to true,
                    FIELD_SUBSCRIPTION_AUTO_RENEWING to purchase.isAutoRenewing,
                    FIELD_SUBSCRIPTION_EXPIRED_AT to expiryTime.toFirebaseTimestamp(),
                    FIELD_PURCHASE_TOKEN to purchase.purchaseToken
                ))

                // 기존 사용자 처리 (필요한 경우)
                if (existingUserRef != null) {
                    Log.d(TAG, "다른 사용자에게 연결된 구독을 현재 사용자로 이전")
                    transaction.update(existingUserRef, mapOf(
                        FIELD_IS_SUBSCRIPTION_ACTIVE to false,
                        FIELD_PURCHASE_TOKEN to ""
                    ))
                }

                // 구독 문서 업데이트 또는 생성
                if (subscriptionDoc.exists()) {
                    transaction.update(subscriptionRef, mapOf(
                        FIELD_USER_ID to userId,
                        FIELD_GOOGLE_ACCOUNT_ID to googleAccountId,
                        FIELD_EXPIRY_TIME to expiryTime.toFirebaseTimestamp(),
                        FIELD_AUTO_RENEWING to purchase.isAutoRenewing
                    ))
                } else {
                    transaction.set(subscriptionRef, mapOf(
                        FIELD_USER_ID to userId,
                        FIELD_GOOGLE_ACCOUNT_ID to googleAccountId,
                        FIELD_EXPIRY_TIME to expiryTime.toFirebaseTimestamp(),
                        FIELD_AUTO_RENEWING to purchase.isAutoRenewing
                    ))
                }
            }.await()

            Log.d(TAG, "Firestore 구독 정보 업데이트 성공")

            // 구독 이력 추가 - 트랜잭션과 분리하여 중앙화된 이력 관리 메서드 호출
            // (참고: 트랜잭션 성공 후에만 이력 추가)
            addSubscriptionHistory(userId, ACTION_PURCHASED, purchase)

        } catch (e: Exception) {
            Log.e(TAG, "Firestore 구독 정보 업데이트 실패", e)

            // 트랜잭션 실패 시 대체 방법으로 개별 문서 업데이트 시도
            try {
                Log.d(TAG, "트랜잭션 실패 - 대체 방법으로 개별 문서 업데이트 시도")
                updateSubscriptionWithoutTransaction(purchase, googleAccountId, expiryTime)
            } catch (fallbackE: Exception) {
                Log.e(TAG, "대체 방법도 실패", fallbackE)
                throw e  // 원래 예외를 다시 throw
            }
        }
    }

    /**
     * 트랜잭션 없이 구독 정보 업데이트 (fallback 메서드)
     */
    private suspend fun updateSubscriptionWithoutTransaction(
        purchase: Purchase,
        googleAccountId: String,
        expiryTime: Long
    ) {
        val userId = UserPref.id
        Log.d(TAG, "트랜잭션 없이 구독 정보 업데이트 시작")

        // 1. 먼저 구독 문서 확인
        val subscriptionRef = db.collection(COLLECTION_SUBSCRIPTIONS)
            .document(purchase.purchaseToken)

        val subscriptionDoc = subscriptionRef.get().await()

        // 2. 다른 사용자에게 연결된 구독인 경우 처리
        if (subscriptionDoc.exists()) {
            val existingUserId = subscriptionDoc.getString(FIELD_USER_ID)

            if (existingUserId != null && existingUserId != userId) {
                Log.d(TAG, "기존 구독이 다른 사용자($existingUserId)에 연결되어 있음")

                // 기존 사용자의 구독 비활성화
                db.collection(COLLECTION_USERS)
                    .document(existingUserId)
                    .update(mapOf(
                        FIELD_IS_SUBSCRIPTION_ACTIVE to false,
                        FIELD_PURCHASE_TOKEN to ""
                    ))
                    .await()

                Log.d(TAG, "이전 사용자 구독 비활성화 완료")
            }
        }

        // 3. 현재 사용자 문서 업데이트
        db.collection(COLLECTION_USERS)
            .document(userId)
            .update(mapOf(
                FIELD_IS_SUBSCRIPTION_ACTIVE to true,
                FIELD_SUBSCRIPTION_AUTO_RENEWING to purchase.isAutoRenewing,
                FIELD_SUBSCRIPTION_EXPIRED_AT to expiryTime.toFirebaseTimestamp(),
                FIELD_PURCHASE_TOKEN to purchase.purchaseToken
            ))
            .await()

        Log.d(TAG, "사용자 문서 업데이트 완료")

        // 4. 구독 문서 업데이트 또는 생성
        if (subscriptionDoc.exists()) {
            subscriptionRef.update(mapOf(
                FIELD_USER_ID to userId,
                FIELD_GOOGLE_ACCOUNT_ID to googleAccountId,
                FIELD_EXPIRY_TIME to expiryTime.toFirebaseTimestamp(),
                FIELD_AUTO_RENEWING to purchase.isAutoRenewing
            )).await()
        } else {
            subscriptionRef.set(mapOf(
                FIELD_USER_ID to userId,
                FIELD_GOOGLE_ACCOUNT_ID to googleAccountId,
                FIELD_EXPIRY_TIME to expiryTime.toFirebaseTimestamp(),
                FIELD_AUTO_RENEWING to purchase.isAutoRenewing
            )).await()
        }

        Log.d(TAG, "구독 문서 업데이트 완료")

        // 5. 구독 이력 추가 - 중앙화된 이력 관리 메서드 호출
        addSubscriptionHistory(userId, ACTION_PURCHASED, purchase)

        Log.d(TAG, "트랜잭션 없이 구독 정보 업데이트 완료")
    }

    /**
     * 구독 이력 추가를 위한 통합 진입점
     *
     * 이 메서드는 다른 모든 구독 이력 추가 호출을 중앙에서 관리하여 중복을 방지합니다.
     * 직접 이력을 추가하지 말고 이 메서드를 통해서만 추가하세요.
     */
    private suspend fun addSubscriptionHistory(
        userId: String,
        action: String,
        purchase: Purchase? = null,
        previousExpiryTime: Long = 0,
        newExpiryTime: Long = 0
    ) {
        // 사용자 ID 확인
        if (userId.isEmpty()) {
            Log.e(TAG, "구독 이력 추가 실패: 사용자 ID가 비어 있음")
            return
        }

        try {
            // 1. 현재 이력 확인 (중복 체크용)
            val userDoc = db.collection(COLLECTION_USERS).document(userId).get().await()

            if (!userDoc.exists()) {
                Log.e(TAG, "구독 이력 추가 실패: 사용자 문서가 존재하지 않음")
                return
            }

            // 기존 이력 추출
            @Suppress("UNCHECKED_CAST")
            val existingHistory = userDoc.get(FIELD_SUBSCRIPTION_HISTORY) as? List<Map<String, Any>> ?: listOf()

            // 새 이력 항목
            val historyEntry = mutableMapOf<String, Any>(
                "action" to action,
                "timestamp" to Timestamp.now()
            )

            // 액션 타입에 따라 추가 필드 설정
            when (action) {
                ACTION_PURCHASED, ACTION_EXPIRED -> {
                    // 구매/만료 이력인 경우 구매 정보 추가
                    if (purchase != null) {
                        purchase.orderId?.let { historyEntry["orderId"] = it }
                        purchase.products.firstOrNull()?.let { historyEntry["productId"] = it }
                    }
                }
                ACTION_RENEWED -> {
                    // 갱신 이력인 경우 만료 시간 정보 추가
                    if (purchase != null) {
                        purchase.orderId?.let { historyEntry["orderId"] = it }
                        purchase.products.firstOrNull()?.let { historyEntry["productId"] = it }
                    }
                    if (previousExpiryTime > 0) {
                        historyEntry["previousExpiryDate"] = previousExpiryTime.toFirebaseTimestamp()
                    }
                    if (newExpiryTime > 0) {
                        historyEntry["newExpiryDate"] = newExpiryTime.toFirebaseTimestamp()
                    }
                }
                ACTION_CANCELED -> {
                    // 해지 이력인 경우 구매 정보와 해지 시간 추가
                    if (purchase != null) {
                        purchase.orderId?.let { historyEntry["orderId"] = it }
                        purchase.products.firstOrNull()?.let { historyEntry["productId"] = it }
                    }
                    historyEntry["canceledAt"] = Timestamp.now()
                }
            }

            // 2. 중복 체크
            var isDuplicate = false
            val orderId = purchase?.orderId

            if (orderId != null && orderId.isNotEmpty()) {
                isDuplicate = existingHistory.any { entry ->
                    entry["action"] == action && entry["orderId"] == orderId
                }
            } else if (action == ACTION_EXPIRED) {
                // 만료 이력의 경우 최근 5분 이내에 동일 액션이 있으면 중복으로 간주
                val now = System.currentTimeMillis()
                isDuplicate = existingHistory.any { entry ->
                    val entryAction = entry["action"] as? String
                    val entryTimestamp = (entry["timestamp"] as? Timestamp)?.toDate()?.time

                    entryAction == ACTION_EXPIRED &&
                            entryTimestamp != null &&
                            (now - entryTimestamp) < 5 * 60 * 1000 // 5분 이내
                }
            }

            // 중복이 아닌 경우에만 추가
            if (!isDuplicate) {
                Log.d(TAG, "구독 이력 추가: action=$action")

                // 이력 필드가 있는지 확인 후 업데이트 또는 생성
                if (userDoc.contains(FIELD_SUBSCRIPTION_HISTORY)) {
                    db.collection(COLLECTION_USERS).document(userId)
                        .update(FIELD_SUBSCRIPTION_HISTORY, FieldValue.arrayUnion(historyEntry))
                        .await()
                } else {
                    // 이력 필드가 없는 경우 새로 생성
                    db.collection(COLLECTION_USERS).document(userId)
                        .update(mapOf(FIELD_SUBSCRIPTION_HISTORY to listOf(historyEntry)))
                        .await()
                }

                Log.d(TAG, "구독 이력 추가 성공: $action")
            } else {
                Log.d(TAG, "중복된 구독 이력 감지됨 - 추가 무시: action=$action, orderId=$orderId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "구독 이력 추가 실패", e)
        }
    }

    /**
     * Google 계정 ID 해시 가져오기
     *
     * 현재 로그인된 Google 계정의 고유 식별자를 생성합니다.
     * 개인정보 보호를 위해 이메일을 직접 저장하지 않고 해시값을 사용합니다.
     */
    private fun getGoogleAccountId(): String {
        val email = auth.currentUser?.email ?: ""

        return if (email.isNotEmpty()) {
            // 이메일을 SHA-256으로 해시
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(email.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } else {
            // 로그인된 사용자가 없거나 이메일이 없는 경우 임시 ID 사용
            "unknown_${auth.currentUser?.uid ?: UUID.randomUUID().toString()}"
        }
    }

    /**
     * 구독 시작
     *
     * 사용자에게 구독 구매 화면을 표시합니다.
     * @param activity 활동 컨텍스트
     * @param callback 구매 결과 콜백
     */
    fun subscribe(activity: Activity, callback: SubscriptionPurchaseCallback? = null) {
        // 임시 콜백 설정 (필요한 경우)
        if (callback != null) {
            this.purchaseCallback = callback
        }

        // 연결 상태 확인 및 필요시 재연결
        billingManager.reconnectIfNeeded()

        coroutineScope.launch {
            try {
                // 현재 구글 계정의 구독이 다른 앱 계정에 연결되어 있는지 확인
                val googleAccountId = getGoogleAccountId()
                val subscriptionsQuery = db.collection(COLLECTION_SUBSCRIPTIONS)
                    .whereEqualTo(FIELD_GOOGLE_ACCOUNT_ID, googleAccountId)
                    .get()
                    .await()

                if (!subscriptionsQuery.isEmpty) {
                    // 이 Google 계정에 연결된 구독이 있음
                    val subscription = subscriptionsQuery.documents.first()
                    val linkedUserId = subscription.getString(FIELD_USER_ID)
                    val currentUserId = UserPref.id

                    if (linkedUserId != null && linkedUserId != currentUserId) {
                        // 다른 앱 계정에 연결된 구독
                        purchaseCallback?.onSubscriptionPurchaseFailed(
                            -1,
                            "이 Google 계정의 구독은 이미 다른 앱 계정에 연결되어 있습니다. " +
                                    "현재 계정으로 혜택을 받으려면 Play 스토어에서 기존 구독을 해지하고 새로 구독해주세요."
                        )
                        return@launch
                    }
                }

                // 구독 상품 확인
                if (productDetails.isEmpty()) {
                    preloadSubscriptionProducts { success ->
                        if (success) {
                            launchBillingFlow(activity)
                        } else {
                            purchaseCallback?.onSubscriptionPurchaseFailed(
                                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                                "구독 상품 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요."
                            )
                        }
                    }
                } else {
                    launchBillingFlow(activity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "구독 시작 중 오류 발생", e)
                purchaseCallback?.onSubscriptionPurchaseFailed(
                    -1,
                    "구독 확인 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * 구매 흐름 시작
     */
    private fun launchBillingFlow(activity: Activity) {
        val details = productDetails[SUBSCRIPTION_PRODUCT_ID]
        if (details == null) {
            purchaseCallback?.onSubscriptionPurchaseFailed(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "구독 상품 정보를 찾을 수 없습니다"
            )
            return
        }

        val billingResult = billingManager.launchBillingFlow(
            activity,
            details,
            BillingManager.PurchaseType.SUBSCRIPTION,
            subscriptionOfferToken
        )

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseCallback?.onSubscriptionPurchaseFailed(
                billingResult.responseCode,
                billingResult.debugMessage
            )
        }
    }

    /**
     * 구독 상태 확인 (외부 호출용)
     *
     * @return 현재 구독 활성화 상태
     */
    fun isSubscriptionActive(): Boolean {
        return SubscriptionPref.isSubscriptionActive
    }

    /**
     * 네트워크 연결 확인
     *
     * @return 네트워크 연결 가능 여부
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 구독 상태 진단 메서드
     *
     * 구독 상태 확인 및 디버깅에 사용합니다.
     * UI에서 이 메서드를 호출하여 현재 구독 상태를 로깅하고 진단할 수 있습니다.
     */
    fun debugSubscriptionStatus() {
        coroutineScope.launch {
            try {
                // 1. 로컬 구독 상태 확인
                val isActiveLocal = SubscriptionPref.isSubscriptionActive
                val expiryTimeLocal = SubscriptionPref.subscriptionExpiryTime
                val purchaseTokenLocal = SubscriptionPref.purchaseToken
                val isCanceledLocal = SubscriptionPref.isSubscriptionCancelled

                Log.d(TAG, "===== 구독 상태 진단 =====")
                Log.d(TAG, "로컬 구독 상태:")
                Log.d(TAG, "- 활성 상태: $isActiveLocal")
                Log.d(TAG, "- 해지 상태: $isCanceledLocal")
                Log.d(TAG, "- 만료 시간: ${if (expiryTimeLocal > 0) dateFormat.format(Date(expiryTimeLocal)) else "없음"}")
                Log.d(TAG, "- 토큰: ${if (purchaseTokenLocal.isNotEmpty()) purchaseTokenLocal.take(8) + "..." else "없음"}")

                // 2. 사용자 정보 확인
                val userId = UserPref.id
                Log.d(TAG, "사용자 정보:")
                Log.d(TAG, "- 사용자 ID: $userId")

                if (userId.isNotEmpty()) {
                    val googleId = getGoogleAccountId()
                    Log.d(TAG, "- Google 계정 ID: ${googleId.take(8)}...")

                    // 3. Firestore 구독 상태 확인
                    try {
                        val userDoc = db.collection(COLLECTION_USERS).document(userId).get().await()
                        if (userDoc.exists()) {
                            val isActiveFirestore = userDoc.getBoolean(FIELD_IS_SUBSCRIPTION_ACTIVE) ?: false
                            val expiryTimestamp = userDoc.getTimestamp(FIELD_SUBSCRIPTION_EXPIRED_AT)
                            val purchaseTokenFirestore = userDoc.getString(FIELD_PURCHASE_TOKEN) ?: ""

                            Log.d(TAG, "Firestore 사용자 구독 상태:")
                            Log.d(TAG, "- 활성 상태: $isActiveFirestore")
                            Log.d(TAG, "- 만료 시간: ${expiryTimestamp?.toDate()?.let { dateFormat.format(it) } ?: "없음"}")
                            Log.d(TAG, "- 토큰: ${if (purchaseTokenFirestore.isNotEmpty()) purchaseTokenFirestore.take(8) + "..." else "없음"}")

                            // 구독 히스토리 확인
                            @Suppress("UNCHECKED_CAST")
                            val historyList = userDoc.get(FIELD_SUBSCRIPTION_HISTORY) as? List<Map<String, Any>>
                            if (!historyList.isNullOrEmpty()) {
                                Log.d(TAG, "- 구독 이력: ${historyList.size}개 항목")
                                historyList.takeLast(3).forEachIndexed { index, entry ->
                                    Log.d(TAG, "  이력 ${historyList.size - index}: 액션=${entry["action"]}, 시간=${entry["timestamp"]}")
                                }
                            } else {
                                Log.d(TAG, "- 구독 이력: 없음")
                            }

                            // 구독 문서 확인
                            if (purchaseTokenFirestore.isNotEmpty()) {
                                val subscriptionDoc = db.collection(COLLECTION_SUBSCRIPTIONS)
                                    .document(purchaseTokenFirestore).get().await()

                                if (subscriptionDoc.exists()) {
                                    val linkedUserId = subscriptionDoc.getString(FIELD_USER_ID)
                                    val linkedGoogleId = subscriptionDoc.getString(FIELD_GOOGLE_ACCOUNT_ID)
                                    val expiryTime = subscriptionDoc.getTimestamp(FIELD_EXPIRY_TIME)

                                    Log.d(TAG, "Firestore 구독 문서:")
                                    Log.d(TAG, "- 연결된 사용자 ID: $linkedUserId")
                                    Log.d(TAG, "- 연결된 Google ID: ${linkedGoogleId?.take(8)}...")
                                    Log.d(TAG, "- 만료 시간: ${expiryTime?.toDate()?.let { dateFormat.format(it) } ?: "없음"}")

                                    // 일관성 검사
                                    if (linkedUserId != userId) {
                                        Log.e(TAG, "!! 불일치 - 구독 문서의 사용자 ID가 현재 사용자와 다름 !!")
                                    }
                                } else {
                                    Log.d(TAG, "Firestore 구독 문서 없음")
                                }
                            }
                        } else {
                            Log.d(TAG, "Firestore 사용자 문서 없음")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Firestore 구독 상태 확인 중 오류 발생", e)
                    }
                }

                // 4. Google Play 구독 상태 확인
                try {
                    val purchases = queryPurchasesSync(BillingClient.ProductType.SUBS)
                    Log.d(TAG, "Google Play 구독:")
                    if (purchases.isNotEmpty()) {
                        purchases.forEach { purchase ->
                            Log.d(TAG, "- 상태: ${getPurchaseStateString(purchase.purchaseState)}")
                            Log.d(TAG, "- 토큰: ${purchase.purchaseToken.take(8)}...")
                            Log.d(TAG, "- 확인됨: ${purchase.isAcknowledged}")
                            Log.d(TAG, "- 자동갱신: ${purchase.isAutoRenewing}")
                            Log.d(TAG, "- 해지됨: ${!purchase.isAutoRenewing}")
                            Log.d(TAG, "- 구매 시간: ${dateFormat.format(Date(purchase.purchaseTime))}")

                            // 로컬 토큰과 일치하는지 확인
                            if (purchase.purchaseToken == purchaseTokenLocal) {
                                Log.d(TAG, "  (로컬 토큰과 일치함)")
                            }
                        }
                    } else {
                        Log.d(TAG, "- 활성 구독 없음")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Google Play 구독 조회 중 오류 발생", e)
                }

                Log.d(TAG, "===== 구독 상태 진단 종료 =====")
            } catch (e: Exception) {
                Log.e(TAG, "구독 상태 진단 중 오류 발생", e)
            }
        }
    }

    /**
     * 구매 상태 문자열 변환
     */
    private fun getPurchaseStateString(state: Int): String {
        return when (state) {
            Purchase.PurchaseState.PURCHASED -> "PURCHASED"
            Purchase.PurchaseState.PENDING -> "PENDING"
            Purchase.PurchaseState.UNSPECIFIED_STATE -> "UNSPECIFIED"
            else -> "UNKNOWN($state)"
        }
    }

    /**
     * BillingManager에서 현재 구독 상태 동기적으로 조회
     */
    private suspend fun queryPurchasesSync(productType: String): List<Purchase> {
        var purchases = listOf<Purchase>()
        try {
            val result = com.google.android.gms.tasks.Tasks.await(
                com.google.android.gms.tasks.TaskCompletionSource<List<Purchase>>().apply {
                    billingManager.queryPurchases(productType) { billingResult, purchasesList ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            setResult(purchasesList)
                        } else {
                            setException(Exception("구독 조회 실패: ${billingResult.debugMessage}"))
                        }
                    }
                }.task
            )
            purchases = result
        } catch (e: Exception) {
            Log.e(TAG, "구독 동기 조회 실패", e)
        }
        return purchases
    }

    /**
     * 구독 상태 데이터 클래스
     */
    data class SubscriptionState(
        val isActive: Boolean = false,
        val isLinkedToOtherAccount: Boolean = false,
        val linkedAccountId: String = "",
        val isCanceled: Boolean = false,  // 해지 여부 추가
        val expiryDate: Long = 0          // 구독 종료 예정 날짜 추가
    )

    /**
     * 구독 구매 콜백 인터페이스
     */
    interface SubscriptionPurchaseCallback {
        /**
         * 구독 구매 완료
         *
         * @param purchase 구매 정보
         */
        fun onSubscriptionPurchaseCompleted(purchase: Purchase)

        /**
         * 구독 구매 실패
         *
         * @param errorCode 오류 코드
         * @param errorMessage 오류 메시지
         */
        fun onSubscriptionPurchaseFailed(errorCode: Int, errorMessage: String)
    }
}