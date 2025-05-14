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
import com.tenacy.roadcapture.databinding.FragmentUserMemoryViewerBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserMemoryViewerFragment: BaseFragment() {

    private var _binding: FragmentUserMemoryViewerBinding? = null
    val binding get() = _binding!!

    private val vm: UserMemoryViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserMemoryViewerBinding.inflate(inflater, container, false)

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
    }

    private fun setupViews() {
        addItemsToLayout(vm.tags)
    }

    private fun setupObservers() {
        observeViewEvents()
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? UserMemoryViewerViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: UserMemoryViewerViewEvent) {
        when (event) {
            is UserMemoryViewerViewEvent.ShowLocation -> {
                val bottomSheet = LocationBottomSheetFragment.newInstance(
                    bundle = bundleOf(
                        LocationBottomSheetFragment.KEY_PARAMS_IN to LocationBottomSheetFragment.ParamsIn(event.address),
                    )
                )
                bottomSheet.show(childFragmentManager, LocationBottomSheetFragment.TAG)
            }
            is UserMemoryViewerViewEvent.ShowMore -> {
//                val bottomSheet = MemoryInfoBottomSheetFragment.newInstance(
//                    bundle = bundleOf(
//                        MemoryInfoBottomSheetFragment.KEY_MEMORY to vm.currentMemory,
//                    )
//                )
//                bottomSheet.show(childFragmentManager, MemoryInfoBottomSheetFragment.TAG)
            }
        }
    }

    private fun addItemsToLayout(items: List<String>) {
        val linearLayout = binding.llUserMemoryViewerTags
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