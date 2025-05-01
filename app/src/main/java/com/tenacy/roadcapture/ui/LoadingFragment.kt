package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.databinding.FragmentLoadingBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoadingFragment: BaseFragment() {

    private var _binding: FragmentLoadingBinding? = null
    val binding get() = _binding!!

    private val vm: LoadingViewModel by viewModels()

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                remove()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoadingBinding.inflate(inflater, container, false)

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

    private fun setupObservers() {
        repeatOnLifecycle {
            vm.fetchState.collect {
                when(it) {
                    is AddressFetchState.Initial -> {}
                    is AddressFetchState.Loading -> {}
                    is AddressFetchState.Completed -> {
                        findNavController().navigate(LoadingFragmentDirections.actionLoadingToNewMemory(it.address, it.photoUri))
                    }
                    is AddressFetchState.Error -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("위치 정보를 불러오는 중에\n오류가 발생했어요", ToastMessageType.Warning)))
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }
}