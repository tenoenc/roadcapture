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
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.databinding.FragmentAppInfoBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import kotlinx.coroutines.launch

class AppInfoFragment: BaseFragment(), FragmentVisibilityCallback {

    private var _binding: FragmentAppInfoBinding? = null
    val binding get() = _binding!!

    private val vm: AppInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onBecameVisible() {
        vm.refreshStates()
    }

    private fun setupObservers() {
        observeViewEvents()
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