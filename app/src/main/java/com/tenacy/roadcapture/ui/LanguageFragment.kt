package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.AppPrefs
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.databinding.FragmentLanguageBinding
import com.tenacy.roadcapture.manager.LocaleManager
import com.tenacy.roadcapture.util.ResourceProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LanguageFragment : BaseFragment() {

    companion object {
        private val supportedLocales = listOf(
            "en",
            "ko",
            "zh-CN",
            "es",
            "pt",
            "ja",
            "de",
            "fr",
            "ru",
            "in",
            "hi",
            "ar",
        )
    }

    private var _binding: FragmentLanguageBinding? = null
    val binding get() = _binding!!

    @Inject
    lateinit var resourceProvider: ResourceProvider

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 언어 변경 후 재생성된 경우 뒤로 가기
        checkAndHandleLanguageChangeComplete()

        binding.llLanguageContentContainer.setupSelectionContainer(true) { selectedIndices ->
            binding.btnLanguageChange.visibility = if(selectedIndices.isEmpty()) View.GONE else View.VISIBLE
        }

        binding.btnLanguageChange.setOnClickListener {
            changeLanguage()
        }
    }

    private fun checkAndHandleLanguageChangeComplete() {
        if (AppPrefs.languageChanged) {
            // 플래그 초기화
            AppPrefs.languageChanged = false

            // 현재 로케일 확인 로그
            val currentLocale = LocaleManager.getCurrentLocale(requireContext())
            Log.d("Language", "Language change completed. Current locale: ${currentLocale.toLanguageTag()}")

            // 네비게이션
            view?.post {
                if (isAdded && findNavController().currentDestination?.id == R.id.languageFragment) {
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun changeLanguage() {
        val selectedIndex = binding.llLanguageContentContainer.getSelectedIndices().firstOrNull()
            ?: supportedLocales.indexOf(DebugSettings.getSelectedLocale(requireContext()))
        val localeCode = supportedLocales[selectedIndex]

        Log.d("Language", "Changing language to: $localeCode")

        // 플래그 설정 (recreate 후 확인용)
        AppPrefs.languageChanged = true

        // 선택한 로케일 적용
        LocaleManager.setLocale(requireContext(), localeCode)

        // 뷰모델에 적용
        resourceProvider.refreshConfigurationContext()

        // 현재 적용된 로케일 확인
        val appliedLocale = LocaleManager.getCurrentLocale(requireContext())
        Log.d("Language", "Applied locale: ${appliedLocale.toLanguageTag()}")

        // 액티비티 재시작
        requireActivity().recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}