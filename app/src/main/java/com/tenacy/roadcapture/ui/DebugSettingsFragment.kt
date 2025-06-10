package com.tenacy.roadcapture.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.databinding.FragmentDebugSettingsBinding
import com.tenacy.roadcapture.manager.LocaleManager
import com.tenacy.roadcapture.util.ResourceProvider
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class DebugSettingsFragment : Fragment() {

    @Inject
    lateinit var resourceProvider: ResourceProvider

    private var _binding: FragmentDebugSettingsBinding? = null
    private val binding get() = _binding!!

    // 지원하는 로케일 목록 (코드, 표시 이름)
    private val supportedLocales = listOf(
        "en" to "English (영어)",
        "ko" to "한국어",
        "zh-CN" to "简体中文 (중국어 간체)",
        "es" to "Español (스페인어)",
        "pt" to "Português (포르투갈어)",
        "ja" to "日本語 (일본어)",
        "de" to "Deutsch (독일어)",
        "fr" to "Français (프랑스어)",
        "ru" to "Русский (러시아어)",
        "in" to "Bahasa Indonesia (인도네시아어)",
        "hi" to "हिन्दी (힌디어)",
        "ar" to "العربية (아랍어)"
    )

    // 로케일 코드 목록 (Spinner와 함께 사용)
    private val localeCodes = supportedLocales.map { it.first }

    // 로케일 표시 이름 목록 (Spinner에 표시)
    private val localeNames = supportedLocales.map { it.second }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 디버그 모드에서만 설정 보이기
        if (BuildConfig.DEBUG) {
            binding.debugSettingsContainer.visibility = View.VISIBLE

            // Mock Location 설정 초기화
            binding.switchMockLocation.isChecked = DebugSettings.useMockLocationInDebugMode

            // 스위치 변경 리스너
            binding.switchMockLocation.setOnCheckedChangeListener { _, isChecked ->
                DebugSettings.useMockLocationInDebugMode = isChecked
            }

            // 로케일 Spinner 설정
            setupLocaleSpinner()

            // 로케일 적용 버튼 리스너
            binding.btnApplyLocale.setOnClickListener {
                val selectedPosition = binding.spinnerLocale.selectedItemPosition
                val localeCode = localeCodes[selectedPosition]

                // 선택한 로케일 적용
                LocaleManager.setLocale(requireContext(), localeCode)

                // 액티비티 재시작하여 리소스 다시 로드
                requireActivity().recreate()

                resourceProvider.refreshConfigurationContext()
            }
        } else {
            binding.debugSettingsContainer.visibility = View.GONE
        }
    }

    private fun setupLocaleSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            localeNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLocale.adapter = adapter

        // 저장된 로케일이 있으면 해당 위치로 설정
        val savedLocale = DebugSettings.getSelectedLocale(requireContext())
        val position = localeCodes.indexOf(savedLocale)
        if (position != -1) {
            binding.spinnerLocale.setSelection(position)
        }
    }

    private fun applyLocale(localeCode: String) {
        val locale = when {
            localeCode.contains("-") -> {
                val parts = localeCode.split("-")
                Locale(parts[0], parts[1])
            }
            else -> Locale(localeCode)
        }

        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        requireContext().createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}