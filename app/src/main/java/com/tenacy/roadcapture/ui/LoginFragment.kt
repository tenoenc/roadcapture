package com.tenacy.roadcapture.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.facebook.CallbackManager
import com.facebook.login.LoginManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.auth.FacebookOAuthLoginCallback
import com.tenacy.roadcapture.auth.GoogleOAuthLoginCallback
import com.tenacy.roadcapture.auth.KakaoOAuthLoginCallback
import com.tenacy.roadcapture.auth.NaverOAuthLoginCallback
import com.tenacy.roadcapture.databinding.FragmentLoginBinding
import com.tenacy.roadcapture.util.TagConstants
import com.tenacy.roadcapture.util.auth
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class LoginFragment : BaseFragment() {

    private var _binding: FragmentLoginBinding? = null
    val binding get() = _binding!!

    private val vm: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    private val permissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
        }
    }

    @Inject
    lateinit var callbackManager: CallbackManager

    @Inject
    lateinit var naverOAuthLoginCallback: NaverOAuthLoginCallback

    @Inject
    lateinit var kakaoOAuthLoginCallback: KakaoOAuthLoginCallback

    @Inject
    lateinit var googleOAuthLoginCallback: GoogleOAuthLoginCallback

    @Inject
    lateinit var facebookOAuthLoginCallback: FacebookOAuthLoginCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
    }

    override fun onStart() {
        super.onStart()

        val authority = arguments?.getString("authority")
        if (authority == "login-callback") {
            naverOAuthLoginCallback()
        }

        auth.currentUser?.let {
            findNavController().navigate(LoginFragmentDirections.actionLoginToMain())
        } ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TedPermission.create()
                    .setPermissionListener(permissionListener)
                    .setPermissions(Manifest.permission.POST_NOTIFICATIONS)
                    .check()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupObservers() {
        observeViewEvents()
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? LoginViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: LoginViewEvent) {
        when (event) {
            is LoginViewEvent.Login -> {
                findNavController().navigate(LoginFragmentDirections.actionLoginToMainBefore(event.authCredential, event.socialType, event.socialUserId, isExistingUser = true))
            }

            is LoginViewEvent.GoogleLogin -> {
                googleLogin()
            }

            is LoginViewEvent.FacebookLogin -> {
                facebookLogin()
            }

            is LoginViewEvent.KakaoLogin -> {
                kakaoLogin()
            }

            is LoginViewEvent.NaverLogin -> {
                naverLogin()
            }

            // 추가된 에러 처리
            is LoginViewEvent.SocialError -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("로그인 중에 문제가 발생했어요", ToastMessageType.Warning)))
                }
            }

            is LoginViewEvent.Signup -> {
                findNavController().navigate(LoginFragmentDirections.actionLoginToSignupUsername(event.authCredential, event.socialUserId, event.socialProfileUrl, event.socialType))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun naverLogin() {
        val baseUri = BuildConfig.NAVER_AUTH_URL
        val clientId = BuildConfig.NAVER_CLIENT_ID
        val redirectUri = BuildConfig.NAVER_AUTH_CALLBACK_URI

        // URL-safe Base64로 인코딩
        // 16바이트 길이의 임의의 바이트 배열 생성
        val stateString = Base64.encodeToString(ByteArray(16) { Random.nextInt(0, 256).toByte() }, Base64.NO_PADDING)

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("$baseUri?response_type=code&client_id=$clientId&redirect_uri=$redirectUri&state=$stateString")
        )
        startActivity(intent)
    }

    private fun kakaoLogin() {
        loginWithKakaoMethod(kakaoOAuthLoginCallback)
    }

    private fun googleLogin() {
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

    private fun facebookLogin() {
        LoginManager.getInstance().run {
            logInWithReadPermissions(
                this@LoginFragment,
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
}