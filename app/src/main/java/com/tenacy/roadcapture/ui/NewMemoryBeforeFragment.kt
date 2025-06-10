package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentNewMemoryBeforeBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewMemoryBeforeFragment: BaseFragment() {

    private var _binding: FragmentNewMemoryBeforeBinding? = null
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
        _binding = FragmentNewMemoryBeforeBinding.inflate(inflater, container, false)

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
                    is NsfwDetectionState.Loading -> {}
                    is NsfwDetectionState.ProcessingImage -> {
                        binding.txtNewMemoryBeforeStatus.text = requireContext().getString(R.string.processing_image)
                    }
                    is NsfwDetectionState.DetectingNsfw -> {
                        binding.txtNewMemoryBeforeStatus.text = requireContext().getString(R.string.reviewing_image)
                    }
                    is NsfwDetectionState.Completed -> {
                        val args: NewMemoryBeforeFragmentArgs by navArgs()
                        findNavController().navigate(NewMemoryBeforeFragmentDirections.actionNewMemoryBeforeToNewMemory(args.photoUri, args.coordinates))
                    }
                    is NsfwDetectionState.Error -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(it.message, ToastMessageType.Warning)))
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }
}