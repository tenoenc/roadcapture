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
import com.tenacy.roadcapture.databinding.FragmentModifiableMemoryViewerBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ModifiableMemoryViewerFragment: BaseFragment() {

    override val onBackPressed: () -> Unit
        get() = vm::onBackClick

    private var _binding: FragmentModifiableMemoryViewerBinding? = null
    val binding get() = _binding!!

    private val vm: ModifiableMemoryViewerViewModel by viewModels()

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
        _binding = FragmentModifiableMemoryViewerBinding.inflate(inflater, container, false)

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

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            LocationBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<LocationBottomSheetFragment.ParamsOut.Positive>(LocationBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                val address = it.address
                lifecycleScope.launch(Dispatchers.Main) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.CopyToClipboard(address))
                }
            }
        }
        childFragmentManager.setFragmentResultListener(
            MemoryMoreBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<MemoryMoreBottomSheetFragment.ParamsOut.Info>(MemoryMoreBottomSheetFragment.KEY_PARAMS_OUT_INFO)?.let {
                val memory = vm.currentMemory.value ?: return@let
                val bottomSheet = MemoryInfoBottomSheetFragment.newInstance(
                    bundle = bundleOf(
                        MemoryInfoBottomSheetFragment.KEY_PARAMS_IN to MemoryInfoBottomSheetFragment.ParamsIn(memory),
                    )
                )
                bottomSheet.show(childFragmentManager, MemoryInfoBottomSheetFragment.TAG)
            }
            bundle.getParcelable<MemoryMoreBottomSheetFragment.ParamsOut.Delete>(MemoryMoreBottomSheetFragment.KEY_PARAMS_OUT_DELETE)?.let {
                vm.deleteCurrentMemory()
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
                binding.vpModifiableMemoryViewerPhoto.registerOnPageChangeCallback(onPageChangeCallback)
                binding.vpModifiableMemoryViewerPhoto.adapter = PhotoSliderAdapter(photoUris = it)
                binding.vpModifiableMemoryViewerPhoto.setCurrentItem(vm.currentMemoryIndex.value, false)
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
                it.getContentIfNotHandled()?.let { event ->
                    (event as? ModifiableMemoryViewerViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: ModifiableMemoryViewerViewEvent) {
        when (event) {
            is ModifiableMemoryViewerViewEvent.ShowLocation -> {
                val bottomSheet = LocationBottomSheetFragment.newInstance(
                    bundle = bundleOf(
                        LocationBottomSheetFragment.KEY_PARAMS_IN to LocationBottomSheetFragment.ParamsIn(event.address),
                    )
                )
                bottomSheet.show(childFragmentManager, LocationBottomSheetFragment.TAG)
            }
            is ModifiableMemoryViewerViewEvent.MoveToPrevPage -> {
                binding.vpModifiableMemoryViewerPhoto.currentItem -= 1
            }
            is ModifiableMemoryViewerViewEvent.MoveToNextPage -> {
                binding.vpModifiableMemoryViewerPhoto.currentItem += 1
            }
            is ModifiableMemoryViewerViewEvent.ShowMore -> {
                val bottomSheet = MemoryMoreBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, MemoryMoreBottomSheetFragment.TAG)
            }
            is ModifiableMemoryViewerViewEvent.ResultBack -> {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    TripFragment.KEY_MODIFIABLE_MEMORY_VIEWER,
                    bundleOf(
                        TripFragment.RESULT_COORDINATES to event.coordinates
                    )
                )
                findNavController().popBackStack()
            }
        }
    }

    private fun addItemsToLayout(items: List<String>) {
        val linearLayout = binding.llModifiableMemoryViewerTags
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