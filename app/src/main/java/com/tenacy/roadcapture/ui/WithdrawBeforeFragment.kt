package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentWithdrawBeforeBinding
import com.tenacy.roadcapture.util.currentFragment
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WithdrawBeforeFragment: BaseFragment() {

    private var _binding: FragmentWithdrawBeforeBinding? = null
    val binding get() = _binding!!

    private val vm: WithdrawBeforeViewModel by viewModels()

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
        _binding = FragmentWithdrawBeforeBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
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
        observeViewEvents()
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? WithdrawBeforeViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: WithdrawBeforeViewEvent) {
        when (event) {
            is WithdrawBeforeViewEvent.Complete -> {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(getString(R.string.withdrawal_complete), ToastMessageType.Success)))

                val navOptions = NavOptions.Builder().setPopUpTo(
                    R.id.mainFragment,
                    true
                ).build()
                mainActivity.currentFragment?.findNavController()?.run {
                    navigate(
                        R.id.loginFragment,
                        null,
                        navOptions
                    )
                }
            }
            is WithdrawBeforeViewEvent.Error.Withdraw -> {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(getString(R.string.withdrawal_error), ToastMessageType.Warning)))
                mainActivity.vm.logout()
            }
        }
    }
}