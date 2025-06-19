package com.tenacy.roadcapture.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google 계정 변경을 감지하고 SubscriptionManager에 알림
 */
@Singleton
class GoogleAccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subscriptionManager: SubscriptionManager
) {
    private val TAG = "GoogleAccountManager"

    // 마지막 확인된 계정 정보
    private var lastAccountName: String? = null

    // Google 로그인 클라이언트
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
            .requestEmail()
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    // 계정 상태 감지 핸들러
    private val accountCheckHandler = Handler(Looper.getMainLooper())
    private val accountCheckRunnable = Runnable { checkForAccountChanges() }

    // 계정 변경 감지 간격 (밀리초)
    private val ACCOUNT_CHECK_INTERVAL_MS = 30 * Constants.MILLIS_PER_MINUTES // 30분

    init {
        // 초기 계정 정보 저장
        saveCurrentAccountInfo()

        // 주기적 확인 시작
        startPeriodicChecks()
    }

    // 현재 Google 계정 정보 저장
    private fun saveCurrentAccountInfo() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        lastAccountName = account?.email
        Log.d(TAG, "현재 Google 계정 저장: ${maskEmail(lastAccountName)}")
    }

    // 계정 변경 확인
    fun checkForAccountChanges() {
        val currentAccount = GoogleSignIn.getLastSignedInAccount(context)
        val currentAccountName = currentAccount?.email

        if (lastAccountName != currentAccountName) {
            Log.d(TAG, "Google 계정 변경 감지: ${maskEmail(lastAccountName)} -> ${maskEmail(currentAccountName)}")

            // 계정 정보 업데이트
            lastAccountName = currentAccountName

            // 구독 상태 확인 트리거
            subscriptionManager.onGoogleAccountChanged()
        }

        // 다음 확인 예약
        scheduleNextCheck()
    }

    // 주기적 확인 시작
    fun startPeriodicChecks() {
        accountCheckHandler.removeCallbacks(accountCheckRunnable)
        accountCheckHandler.postDelayed(accountCheckRunnable, ACCOUNT_CHECK_INTERVAL_MS)
        Log.d(TAG, "Google 계정 감지 주기적 확인 시작")
    }

    // 주기적 확인 중지
    fun stopPeriodicChecks() {
        accountCheckHandler.removeCallbacks(accountCheckRunnable)
        Log.d(TAG, "Google 계정 감지 주기적 확인 중지")
    }

    // 다음 확인 예약
    private fun scheduleNextCheck() {
        accountCheckHandler.removeCallbacks(accountCheckRunnable)
        accountCheckHandler.postDelayed(accountCheckRunnable, ACCOUNT_CHECK_INTERVAL_MS)
    }

    // 즉시 확인 강제 트리거
    fun forceCheck() {
        accountCheckHandler.removeCallbacks(accountCheckRunnable)
        accountCheckRunnable.run()
    }

    // 이메일 마스킹 (로깅용)
    private fun maskEmail(email: String?): String {
        if (email.isNullOrEmpty()) return "없음"

        val atIndex = email.indexOf('@')
        if (atIndex <= 1) return "*****"

        val username = email.substring(0, atIndex)
        val domain = email.substring(atIndex)

        val maskedUsername = if (username.length <= 3) {
            username.first() + "****"
        } else {
            username.take(3) + "****"
        }

        return maskedUsername + domain
    }
}