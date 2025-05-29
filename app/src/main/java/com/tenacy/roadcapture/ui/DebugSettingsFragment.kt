package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.data.pref.DebugSettings
import com.tenacy.roadcapture.databinding.FragmentDebugSettingsBinding

class DebugSettingsFragment : Fragment() {

    private var _binding: FragmentDebugSettingsBinding? = null
    private val binding get() = _binding!!

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
        } else {
            binding.debugSettingsContainer.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}