package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentHtmlBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@AndroidEntryPoint
class HtmlFragment: BaseFragment() {

    private var _binding: FragmentHtmlBinding? = null
    val binding get() = _binding!!

    private val args: HtmlFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHtmlBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
    }

    private fun setupViews() {
        binding.title = when (args.type) {
            HtmlType.PrivacyPolicy -> getString(R.string.privacy_policy)
            HtmlType.TermsOfService -> getString(R.string.terms_of_service)
            HtmlType.PrivacyPolicyAgreement -> getString(R.string.privacy_collection_consent)
            HtmlType.TermsOfServiceAgreement -> getString(R.string.terms_consent)
        }

        setupWebView()
    }

    private fun setupWebView() {
        // WebView 설정
        binding.webHtml.settings.apply {
            javaScriptEnabled = false // 보안을 위해 JavaScript 비활성화
            loadWithOverviewMode = true // 화면에 맞게 컨텐츠 크기 조정
            useWideViewPort = true // 넓은 화면 지원
            setSupportZoom(true) // 확대/축소 지원
            builtInZoomControls = true // 내장 확대/축소 컨트롤 사용
            displayZoomControls = false // 줌 컨트롤 표시 않함
        }

        repeatOnLifecycle {
            try {
                // HTML 문서 로드 (assets 폴더에서)
                val htmlContent = loadHtmlFromAssets()
                binding.webHtml.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } catch (exception: Exception) {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(getString(R.string.load_error), ToastMessageType.Warning)))
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun loadHtmlFromAssets(): String = withContext(Dispatchers.IO) {
        val fileName = when(args.type) {
            HtmlType.TermsOfService -> "terms_of_service.html"
            HtmlType.PrivacyPolicy -> "privacy_policy.html"
            HtmlType.TermsOfServiceAgreement -> "terms_of_service_agreement.html"
            HtmlType.PrivacyPolicyAgreement -> "privacy_policy_agreement.html"
        }
        val inputStream = requireContext().assets.open(fileName) // assets 폴더에 있는 HTML 파일 이름
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val stringBuilder = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line)
        }

        reader.close()
        stringBuilder.toString()
    }
}