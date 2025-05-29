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
import androidx.navigation.NavOptions
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
    }

    override fun onSubscriptionPurchaseFailed(errorCode: Int, errorMessage: String) {
        if (!isAdded) return

        viewLifecycleOwner.lifecycleScope.launch {
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
                mainActivity.vm.logout()
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

            is AppInfoViewEvent.WithdrawComplete -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("서비스 탈퇴가 완료되었어요", ToastMessageType.Success)))

                    val navOptions = NavOptions.Builder().setPopUpTo(
                        R.id.mainFragment,
                        true
                    ).build()
                    mainActivity.currentFragment?.findNavController()?.run {
                        navigate(
                            R.id.loginFragment,
                            null,
                            navOptions
                        )
                    }
                }
            }

            is AppInfoViewEvent.ShowWithdrawBefore -> {
                val bottomSheet = WithdrawBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, WithdrawBeforeBottomSheetFragment.TAG)
            }

            is AppInfoViewEvent.Error -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    when(event) {
                        is AppInfoViewEvent.Error.Reauth -> {
                            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("계정이 일치하지 않아요", ToastMessageType.Warning)))
                        }
                        is AppInfoViewEvent.Error.Withdraw -> {
                            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("서비스 탈퇴 중 오류가 발생했어요", ToastMessageType.Warning)))
                        }
                    }
                }
            }
        }
    }

    private fun reauthenticate() {
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
                    viewLifecycleOwner.lifecycleScope.launch {
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

                    viewLifecycleOwner.lifecycleScope.launch {
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