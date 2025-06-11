package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.databinding.FragmentLanguageBinding
import com.tenacy.roadcapture.manager.LocaleManager
import com.tenacy.roadcapture.util.ResourceProvider
import com.tenacy.roadcapture.util.mainActivity
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

        binding.llLanguageContentContainer.setupSelectionContainer(true) { selectedIndices ->
            binding.btnLanguageChange.visibility = if(selectedIndices.isEmpty()) View.GONE else View.VISIBLE
        }

        binding.btnLanguageChange.setOnClickListener {
            val selectedIndex = binding.llLanguageContentContainer.getSelectedIndices().firstOrNull()
                ?: supportedLocales.indexOf(DebugSettings.getSelectedLocale(requireContext()))
            val localeCode = supportedLocales[selectedIndex]

            // 선택한 로케일 적용
            LocaleManager.setLocale(requireContext(), localeCode)

            // 액티비티 재시작하여 리소스 다시 로드
            requireActivity().recreate()

            // 뷰모델에 적용
            resourceProvider.refreshConfigurationContext()

            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}