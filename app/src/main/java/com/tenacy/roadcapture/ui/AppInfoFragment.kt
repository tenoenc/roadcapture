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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.android.billingclient.api.Purchase
import com.facebook.CallbackManager
import com.facebook.login.LoginManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.auth.FacebookOAuthLoginCallback
import com.tenacy.roadcapture.auth.GoogleOAuthLoginCallback
import com.tenacy.roadcapture.auth.KakaoOAuthLoginCallback
import com.tenacy.roadcapture.data.SocialType
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.databinding.FragmentAppInfoBinding
import com.tenacy.roadcapture.manager.AppReviewManager
import com.tenacy.roadcapture.manager.DonationManager
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.manager.SubscriptionManager.SubscriptionPurchaseCallback
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class AppInfoFragment : BaseFragment(), FragmentVisibilityCallback,
    SubscriptionPurchaseCallback, DonationManager.DonationCallback {

    private var _binding: FragmentAppInfoBinding? = null
    val binding get() = _binding!!

    private val vm: AppInfoViewModel by viewModels()

    @Inject
    lateinit var appReviewManager: AppReviewManager

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    @Inject
    lateinit var donationManager: DonationManager

    @Inject
    lateinit var callbackManager: CallbackManager

    @Inject
    @Named("reauth")
    lateinit var kakaoOAuthLoginCallback: KakaoOAuthLoginCallback

    @Inject
    @Named("reauth")
    lateinit var googleOAuthLoginCallback: GoogleOAuthLoginCallback

    @Inject
    @Named("reauth")
    lateinit var facebookOAuthLoginCallback: FacebookOAuthLoginCallback

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBecameVisible() {
        vm.refreshUserStates()
    }

    override fun onSubscriptionPurchaseCompleted(purchase: Purchase) {
        if (!isAdded()) return

        // 바텀시트 표시
        val bottomSheet = SubscribeAfterBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, SubscribeAfterBottomSheetFragment.TAG)
        vm.setSubscribing(false)
    }

    override fun onSubscriptionPurchaseFailed(errorCode: Int, errorMessage: String) {
        if (!isAdded) return

        mainActivity.vm.viewEvent(
            GlobalViewEvent.Toast(
                ToastModel(requireContext().getString(R.string.subscription_process_error), ToastMessageType.Warning)
            )
        )
        vm.setSubscribing(false)
    }

    override fun onSubscriptionPurchaseCanceled() {
        vm.setSubscribing(false)
    }

    override fun onDonationCompleted(productId: String, purchase: Purchase) {
        if (!isAdded) return

        val message = when (productId) {
            "donation_small" -> requireContext().getString(R.string.donation_thanks_small)
            "donation_medium" -> requireContext().getString(R.string.donation_thanks_medium)
            "donation_large" -> requireContext().getString(R.string.donation_thanks_large)
            else -> requireContext().getString(R.string.donation_thanks_general)
        }

        viewLifecycleOwner.lifecycleScope.launch {
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

        viewLifecycleOwner.lifecycleScope.launch {
            mainActivity.vm.viewEvent(
                GlobalViewEvent.Toast(
                    ToastModel(requireContext().getString(R.string.donation_process_error), ToastMessageType.Warning)
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
        childFragmentManager.setFragmentResultListener(
            WithdrawBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<WithdrawBeforeBottomSheetFragment.ParamsOut.Positive>(
                WithdrawBeforeBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE
            )?.let {
                Log.d("TAG", "Positive Button Clicked!")
                reauthenticate()
            }
        }
        childFragmentManager.setFragmentResultListener(
            LogoutBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<LogoutBeforeBottomSheetFragment.ParamsOut.Positive>(
                LogoutBeforeBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE
            )?.let {
                Log.d("TAG", "Positive Button Clicked!")
                mainActivity.vm.logout()
            }
        }
        childFragmentManager.setFragmentResultListener(
            SubscriptionBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<SubscriptionBottomSheetFragment.ParamsOut.Positive>(SubscriptionBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                // 정기구독
                // 구독 시작
                subscriptionManager.setPurchaseCallback(this)
                subscriptionManager.subscribe(mainActivity, this)
                vm.setSubscribing(true)
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

            is AppInfoViewEvent.NavigateToLanguage -> {
                findNavController().navigate(MainFragmentDirections.actionMainToLanguage())
            }

            is AppInfoViewEvent.ReviewApp -> {
                appReviewManager.requestReview()
            }

            is AppInfoViewEvent.ShowLogoutBefore -> {
                val bottomSheet = LogoutBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, LogoutBeforeBottomSheetFragment.TAG)
            }

            is AppInfoViewEvent.Donate -> {
                val bottomSheet = DonateBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, DonateBeforeBottomSheetFragment.TAG)
            }

            is AppInfoViewEvent.ShowSubscription -> {
                val bottomSheet = SubscriptionBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, SubscriptionBottomSheetFragment.TAG)
            }

            is AppInfoViewEvent.Withdraw -> {
                findNavController().navigate(MainFragmentDirections.actionMainToWithdrawBefore())
            }

            is AppInfoViewEvent.ShowWithdrawBefore -> {
                val bottomSheet = WithdrawBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, WithdrawBeforeBottomSheetFragment.TAG)
            }

            is AppInfoViewEvent.OpenPlayStoreSubscriptionManager -> {
                openPlayStoreSubscriptionManager()
            }

            is AppInfoViewEvent.Error -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    when(event) {
                        is AppInfoViewEvent.Error.Reauth -> {
                            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.account_mismatch), ToastMessageType.Warning)))
                        }
                    }
                }
            }
        }
    }

    private fun openPlayStoreSubscriptionManager(): Boolean = with(requireContext()) {
        return try {
            // 기본 구독 관리 URL
            val subscriptionUrl = "https://play.google.com/store/account/subscriptions"

            // 앱 패키지가 있는 경우 해당 앱의 구독으로 바로 이동
            val appPackage = packageName
            val urlWithPackage = "$subscriptionUrl?package=${appPackage}"

            // 인텐트 생성
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(urlWithPackage)
                // Play 스토어 앱으로 열기 시도
                setPackage("com.android.vending")
            }

            // 인텐트를 처리할 수 있는 액티비티가 있는지 확인
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                true
            } else {
                // Play 스토어 앱이 없는 경우 브라우저로 열기
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlWithPackage))
                startActivity(browserIntent)
                true
            }
        } catch (e: Exception) {
            Log.e("Subscription", "구독 관리 화면 열기 실패", e)
            // 오류 발생 시 일반 Play 스토어 열기 시도
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/account/subscriptions")
                }
                startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                Log.e("Subscription", "일반 구독 관리 화면 열기 실패", e2)
                false
            }
        }
    }

    private fun reauthenticate() {
        vm.enterSigningIn()
        when (UserPref.provider) {
            SocialType.Google -> googleReauth()
            SocialType.Facebook -> facebookReauth()
            SocialType.Kakao -> kakaoReauth()
            SocialType.Naver -> {}
        }
    }

    private fun kakaoReauth() {
        loginWithKakaoMethod(kakaoOAuthLoginCallback)
    }

    private fun googleReauth() {
        val credentialManager = CredentialManager.create(requireContext())

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = credentialManager.getCredential(requireContext(), request)
                // 성공 Result 전달
                withContext(Dispatchers.Main) {
                    googleOAuthLoginCallback(Result.success(response))
                }
            } catch (e: GetCredentialException) {
                // 실패 Result 전달
                withContext(Dispatchers.Main) {
                    googleOAuthLoginCallback(Result.failure(e))
                }
            }
        }
    }

    private fun facebookReauth() {
        LoginManager.getInstance().run {
            logInWithReadPermissions(
                this@AppInfoFragment,
                listOf(
                    "email",
                    "public_profile"
                )
            )
            registerCallback(callbackManager, facebookOAuthLoginCallback)
        }
    }

    private fun loginWithKakaoMethod(kakaoLoginCallback: KakaoOAuthLoginCallback) {

        // 카카오톡이 설치되어 있으면 카카오톡으로 로그인, 아니면 카카오계정으로 로그인
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(requireContext())) {
            UserApiClient.instance.loginWithKakaoTalk(requireContext()) { token, error ->
                if (error != null) {
                    Log.e(TagConstants.AUTH, "카카오톡으로 로그인 실패", error)

                    kakaoLoginCallback(token, error)

                    // 사용자가 카카오톡 설치 후 디바이스 권한 요청 0화면에서 로그인을 취소한 경우,
                    // 의도적인 로그인 취소로 보고 카카오계정으로 로그인 시도 없이 로그인 취소로 처리 (예: 뒤로 가기)
                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        return@loginWithKakaoTalk
                    }

                    // 카카오톡에 연결된 카카오계정이 없는 경우, 카카오계정으로 로그인 시도
                    UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = kakaoLoginCallback)
                } else if (token != null) {
                    Log.i(TagConstants.AUTH, "카카오톡으로 로그인 성공 ${token.accessToken}")

                    kakaoLoginCallback(token, null)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(requireContext(), callback = kakaoLoginCallback)
        }
    }

    // 후원 시작
    private fun launchDonation(donationType: String) {
        donationManager.setDonationCallback(this)
        donationManager.donate(mainActivity, donationType)
    }

    private fun openEmailToContactDeveloper() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("tentenacy@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, requireContext().getString(R.string.contact_email_subject))

                    val `0` = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
                    val `1` = Build.MODEL
                    val `2` = Build.VERSION.RELEASE
                    putExtra(
                        Intent.EXTRA_TEXT, requireContext().getString(R.string.contact_template, `0`, `1`, `2`).trimIndent()
                    )
                }
                startActivity(emailIntent)
            } catch (e: Exception) {
                // 오류 처리 및 로깅
                Log.e("EmailError", "이메일을 보낼 수 없습니다: ${e.message}")

                if (isAdded()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(requireContext().getString(R.string.email_send_error), ToastMessageType.Warning)
                            )
                        )
                    }

                    // 대체 방법 제공
                    val clipboardManager =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("developer email", "tentenacy@gmail.com")
                    clipboardManager.setPrimaryClip(clipData)

                    viewLifecycleOwner.lifecycleScope.launch {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(requireContext().getString(R.string.dev_email_copied), ToastMessageType.Info)
                            )
                        )
                    }
                }
            }
        }
    }
}