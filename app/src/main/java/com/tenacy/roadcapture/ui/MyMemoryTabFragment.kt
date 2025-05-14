package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.TabMyMemoryBinding
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class MyMemoryTabFragment: BaseFragment() {

    private var _binding: TabMyMemoryBinding? = null
    val binding get() = _binding!!

    private val pVm: MyAlbumViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )
    private val vm: MyMemoryTabViewModel by viewModels()

    private val memoryAdapter: MemoryPagingAdapter by lazy { MemoryPagingAdapter() }
    private val emptyStateAdapter: EmptyStateAdapter by lazy {
        EmptyStateAdapter(EmptyItem.MyMemory)
    }

    private var wasRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabMyMemoryBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupObservers()
    }

    private fun setupViews() {
        setupRecyclerView()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        binding.rvTabMyMemory.apply {
            val spanCount = 3
            val layoutManager = GridLayoutManager(requireContext(), spanCount)

            // 메모리 어댑터에 로드 상태 푸터 추가
            val memoryWithFooter = memoryAdapter.withLoadStateFooter(
                footer = LoadStateAdapter()
            )

            // ConcatAdapter 생성
            val concatAdapter = ConcatAdapter(emptyStateAdapter, memoryWithFooter)
            adapter = concatAdapter

            // 위치 기반 spanSizeLookup 설정
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    // 현재 위치가 어떤 어댑터에 속하는지 계산
                    var currentPos = 0

                    // EmptyStateAdapter 범위 체크
                    val emptyCount = emptyStateAdapter.itemCount
                    if (position < currentPos + emptyCount) {
                        return spanCount // 빈 상태는 전체 너비
                    }
                    currentPos += emptyCount

                    // MemoryAdapter 범위 체크
                    val memoryCount = memoryAdapter.itemCount
                    if (position < currentPos + memoryCount) {
                        return 1 // 메모리 아이템은 1칸
                    }
                    currentPos += memoryCount

                    // LoadStateAdapter 범위 (나머지 위치)
                    return spanCount // 로드 상태는 전체 너비
                }
            }

            this@apply.layoutManager = layoutManager

            // 그리드 아이템 간격 설정
            addItemDecoration(
                GridItemSpacingDecoration(
                    spanCount = spanCount,
                    horizontalSpacing = 6.toPx,
                    verticalSpacing = 6.toPx,
                )
            )
            setHasFixedSize(true)
        }

        // 어댑터 상태 리스너 추가
        memoryAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading

            val isRefreshComplete = wasRefreshing && loadStates.refresh is LoadState.NotLoading
            wasRefreshing = isLoading

            // 로딩 완료 시
            if (!isLoading) {
                val isEmpty = loadStates.append.endOfPaginationReached && memoryAdapter.itemCount < 1
                emptyStateAdapter.isVisible = isEmpty

                // 초기 로딩이나 사용자 리프레시인 경우에만 스크롤
                if (isRefreshComplete) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.rvTabMyMemory.scrollToPosition(0)
                }
            }

            // 오류 처리
            val errorState = when {
                loadStates.append is LoadState.Error -> loadStates.append as LoadState.Error
                loadStates.prepend is LoadState.Error -> loadStates.prepend as LoadState.Error
                loadStates.refresh is LoadState.Error -> loadStates.refresh as LoadState.Error
                else -> null
            }

            errorState?.let {
                wasRefreshing = false
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun setupSwipeRefresh() {
        // 새로고침 색상 설정
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary_normal,
        )

        // 새로고침 리스너 설정 - 사용자 제스처를 통한 리프레시
        binding.swipeRefreshLayout.setOnRefreshListener {
            // 사용자 제스처로 인한 리프레시 - 스와이프 인디케이터 유지
            refreshData()
        }
    }

    private fun refreshData() {
        // 데이터 리프레시 공통 로직
        memoryAdapter.refresh()
        pVm.fetchData()
    }

    private fun refreshProgrammatically() {
        // 프로그래매틱 리프레시 시 스와이프 인디케이터 숨김
        binding.swipeRefreshLayout.isRefreshing = false
        refreshData()
    }

    private fun setupObservers() {
        observeRefreshAllEvent()
        observePagingData()
        observeViewEvents()
    }

    private fun observeRefreshAllEvent() {
        repeatOnLifecycle {
            pVm.refreshAllEvent.collect {
                // 프로그래매틱 리프레시 호출
                refreshProgrammatically()
            }
        }
    }

    private fun observePagingData() {
        // 페이징 데이터 관찰
        repeatOnLifecycle {
            vm.memories.collectLatest { pagingData ->
                memoryAdapter.submitData(
                    pagingData.map {
                        MemoryItem(
                            value = it,
                            onItemClick = {
                                findNavController().navigate(MainFragmentDirections.actionMainToUserMemoryViewer(it))
                            },
                        )
                    }
                )
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? MyMemoryTabViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: MyMemoryTabViewEvent) {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Parcelize
    data class ParamsIn(
        val userId: String,
    ): Parcelable

    companion object {

        const val KEY_PARAMS = "params"
        fun newInstance(bundle: Bundle? = null): MyMemoryTabFragment {
            return MyMemoryTabFragment().apply {
                arguments = bundle
            }
        }
    }
}
