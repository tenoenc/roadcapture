package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout.LayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.databinding.FragmentLoadingBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewMemoryBeforeFragment: BaseFragment() {

    private var _binding: FragmentLoadingBinding? = null
    val binding get() = _binding!!

    private val vm: NewMemoryBeforeViewModel by viewModels()

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoadingBinding.inflate(inflater, container, false)

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

    private fun setupObservers() {
        repeatOnLifecycle {
            vm.readyState.collect {
                when(it) {
                    is MemoryReadyState.Loading -> {}
                    is MemoryReadyState.ProcessingImage -> {
                        binding.txtLoadingStatus.text = "이미지 처리 중"
                    }
                    is MemoryReadyState.DetectingNsfw -> {
                        binding.txtLoadingStatus.text = "이미지 검토 중"
                    }
                    is MemoryReadyState.FetchingAddress -> {
                        binding.lottieLoadingLayers.visibility = View.GONE
                        binding.lottieLoadingSearchLocations.visibility = View.VISIBLE
                        binding.txtLoadingStatus.text = "위치 정보 불러오는 중"
                    }
                    is MemoryReadyState.Completed -> {
                        findNavController().navigate(NewMemoryBeforeFragmentDirections.actionLoadingToNewMemory(it.address, it.photoUri))
                    }
                    is MemoryReadyState.Error -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(it.message, ToastMessageType.Warning)))
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }
}