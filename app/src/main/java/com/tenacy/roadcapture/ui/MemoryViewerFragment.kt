package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.tenacy.roadcapture.databinding.FragmentMemoryViewerBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MemoryViewerFragment: BaseFragment() {

    override val onBackPressed: () -> Unit
        get() = vm::onBackClick

    private var _binding: FragmentMemoryViewerBinding? = null
    val binding get() = _binding!!

    private val vm: MemoryViewerViewModel by viewModels()

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            vm.updateCurrentMemoryIndex(position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoryViewerBinding.inflate(inflater, container, false)

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

    override fun onClick(v: View?) {

        super.onClick(v)
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            LocationBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getString(LocationBottomSheetFragment.RESULT_EVENT_CLICK_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                lifecycleScope.launch(Dispatchers.Main) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.CopyToClipboard(it))
                }
            }
        }
    }

    private fun setupObservers() {
        observePhotoUri()
        observeTags()
        observeViewEvents()
    }

    private fun observePhotoUri() {
        repeatOnLifecycle {
            vm.photoUris.collect {
                binding.vpMemoryViewerPhoto.registerOnPageChangeCallback(onPageChangeCallback)
                binding.vpMemoryViewerPhoto.adapter = PhotoSliderAdapter(it)
                binding.vpMemoryViewerPhoto.setCurrentItem(vm.currentMemoryIndex.value, false)
            }
        }
    }

    private fun observeTags() {
        repeatOnLifecycle {
            vm.tags.collect {
                addItemsToLayout(it)
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? MemoryViewerViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: MemoryViewerViewEvent) {
        when (event) {
            is MemoryViewerViewEvent.ShowLocation -> {
                val bottomSheet = LocationBottomSheetFragment.newInstance(
                    bundle = bundleOf(
                        LocationBottomSheetFragment.KEY_ADDRESS to event.address,
                    )
                )
                bottomSheet.show(childFragmentManager, LocationBottomSheetFragment.TAG)
            }
            is MemoryViewerViewEvent.MoveToPrevPage -> {
                binding.vpMemoryViewerPhoto.currentItem -= 1
            }
            is MemoryViewerViewEvent.MoveToNextPage -> {
                binding.vpMemoryViewerPhoto.currentItem += 1
            }
            is MemoryViewerViewEvent.ShowInfo -> {

            }
            is MemoryViewerViewEvent.ResultBack -> {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(TripFragment.KEY_COORDINATES, event.coordinates)
                findNavController().popBackStack()
            }
        }
    }

    private fun addItemsToLayout(items: List<String>) {
        val linearLayout = binding.llMemoryViewerTags
        linearLayout.removeViewsInLayout(1, linearLayout.childCount - 1)

        // 인플레이터 준비
        val inflater = LayoutInflater.from(requireContext())

        // 각 문자열에 대해 반복
        items.indices.forEach { index ->
            // 항목 뷰바인딩 인플레이트
            val itemBinding = ItemTagBinding.inflate(inflater, linearLayout, false)

            // 텍스트 설정
            itemBinding.name = items[index]

            // 레이아웃 파라미터 설정 (8dp 마진 추가)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8f.toPx, 0, 0, 0)
            }

            // 레이아웃에 뷰 추가
            linearLayout.addView(itemBinding.root, layoutParams)
        }
    }
}