package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentNewMemoryAfterBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewMemoryAfterFragment: BaseFragment() {

    private var _binding: FragmentNewMemoryAfterBinding? = null
    val binding get() = _binding!!

    private val vm: NewMemoryAfterViewModel by viewModels()

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
        _binding = FragmentNewMemoryAfterBinding.inflate(inflater, container, false)

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
                    is SaveMemoryState.Loading -> {}
                    is SaveMemoryState.CountMemory -> {
                        binding.txtNewMemoryAfterStatus.text = "위치 정보 불러오는 중"
                    }
                    is SaveMemoryState.ReversingGeocoding -> {}
                    is SaveMemoryState.SavingMemory -> {}
                    is SaveMemoryState.Completed -> {
                        val args: NewMemoryAfterFragmentArgs by navArgs()
                        val destinationId = R.id.tripFragment
                        findNavController().getBackStackEntry(destinationId).savedStateHandle[TripFragment.KEY_NEW_MEMORY] = bundleOf(
                            TripFragment.RESULT_MEMORY_ID to it.memoryId,
                            TripFragment.RESULT_COORDINATES to args.coordinates,
                        )
                        findNavController().popBackStack()
                    }
                    is SaveMemoryState.Error -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(it.message, ToastMessageType.Warning)))
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }
}