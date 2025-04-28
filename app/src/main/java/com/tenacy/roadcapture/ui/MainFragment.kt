package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentMainBinding
import com.tenacy.roadcapture.util.launchOnLifecycle
import com.tenacy.roadcapture.util.mainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    val binding get() = _binding!!

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            TripBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getString(TripBeforeBottomSheetFragment.RESULT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                findNavController().navigate(MainFragmentDirections.actionMainToTrip())
            }
        }
    }

    private fun setupViews() {
        val nestedNavHostFragment = childFragmentManager.findFragmentById(R.id.nested_container) as NavHostFragment
        val nestedNavController = nestedNavHostFragment.navController
        binding.bottomNav.setupWithNavController(nestedNavController)
    }

    private fun setupObservers() {
        observeViewEvents()
    }

    private fun observeViewEvents() {
        launchOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? MainViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: MainViewEvent) {
        when (event) {
            is MainViewEvent.Logout -> {
                mainActivity.signOut()
            }
            is MainViewEvent.New -> {
                val bottomSheet = TripBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, TripBeforeBottomSheetFragment.TAG)
            }
        }
    }
}