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
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentLoadingBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UsernameSavingFragment: BaseFragment() {

    private var _binding: FragmentLoadingBinding? = null
    val binding get() = _binding!!

    private val vm: UsernameSavingViewModel by viewModels()

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
            vm.saveState.collect {
                when(it) {
                    is UsernameSaveState.Initial -> {}
                    is UsernameSaveState.Loading -> {}
                    is UsernameSaveState.Completed -> {
                        findNavController().getBackStackEntry(R.id.mainFragment).savedStateHandle[MyAlbumFragment.KEY_USERNAME_SAVING] =
                            bundleOf(
                                MyAlbumFragment.RESULT_EVENT_REFRESH to System.currentTimeMillis()
                            )
                        findNavController().popBackStack(R.id.mainFragment, false)
                    }
                    is UsernameSaveState.Error -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("이름을 변경하는 중에\n오류가 발생했어요", ToastMessageType.Warning)))
                        findNavController().popBackStack(R.id.mainFragment, false)
                    }
                }
            }
        }
    }
}