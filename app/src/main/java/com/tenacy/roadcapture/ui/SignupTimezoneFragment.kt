package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.tenacy.roadcapture.databinding.FragmentSignupTimezoneBinding
import com.tenacy.roadcapture.ui.dto.SearchableTimezone
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignupTimezoneFragment : BaseFragment() {

    private var _binding: FragmentSignupTimezoneBinding? = null
    val binding get() = _binding!!

    private val vm: SignupTimezoneViewModel by viewModels()

    private val timezoneAdapter by lazy { TimezoneAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupTimezoneBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerview()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerview() {
        binding.rvSignupTimezone.adapter = timezoneAdapter
        binding.rvSignupTimezone.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSignupTimezone.addItemDecoration(ItemSpacingDecoration(spacing = 12.toPx))
        binding.rvSignupTimezone.setHasFixedSize(true)
    }

    private fun setupObservers() {
        observeTimezones()
        observeViewEvents()
    }

    private fun observeTimezones() {
        repeatOnLifecycle {
            vm.timezones.collect {
                val items = it.filter(SearchableTimezone::isFiltered).map { searchableTimezone ->
                    TimezoneItem(
                        id = searchableTimezone.id.toLong(),
                        value = searchableTimezone,
                        onItemClick = { _ ->
                            vm.selectTimezone(searchableTimezone.id)
                        }
                    )
                }
                timezoneAdapter.submitList(items) {
                    if(it.size == items.size) {
                        binding.rvSignupTimezone.scrollToPosition(items.indexOfFirst { it.value.isSelected })
                    }
                }
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? SignupTimezoneViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: SignupTimezoneViewEvent) {
        val args: SignupTimezoneFragmentArgs by navArgs()
        when (event) {
            is SignupTimezoneViewEvent.Next -> {
                findNavController().navigate(SignupTimezoneFragmentDirections.actionSignupTimezoneToSignupAgreement(args.authCredential, args.socialUserId, args.defaultProfile, args.socialType, args.username, event.timezone))
            }
        }
    }
}