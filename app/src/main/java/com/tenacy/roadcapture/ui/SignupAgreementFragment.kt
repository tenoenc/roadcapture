package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.tenacy.roadcapture.databinding.FragmentSignupAgreementBinding
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignupAgreementFragment: BaseFragment() {

    private var _binding: FragmentSignupAgreementBinding? = null
    val binding get() = _binding!!

    private val vm: SignupAgreementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupAgreementBinding.inflate(inflater, container, false)
        binding.vm = vm
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
                    (event as? SignupAgreementViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: SignupAgreementViewEvent) {
        val args: SignupAgreementFragmentArgs by navArgs()
        when (event) {
            is SignupAgreementViewEvent.NavigateToHtml -> {
                findNavController().navigate(SignupAgreementFragmentDirections.actionSignupAgreementToHtml(event.type))
            }
            is SignupAgreementViewEvent.Start -> {
                findNavController().navigate(SignupAgreementFragmentDirections.actionSignupAgreementToMainBefore(
                    args.authCredential,
                    args.socialType,
                    false,
                    socialUserId = args.socialUserId,
                    username = args.username
                ))
            }
        }
    }
}