package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.paging.LoadState
import androidx.paging.map
import com.tenacy.roadcapture.databinding.FragmentHomeBinding
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class HomeFragment: BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels()

    private val albumAdapter: AlbumPagingAdapter by lazy { AlbumPagingAdapter() }

    // 현재 리프레시 중인지 추적
    private var isCurrentlyRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

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

    private fun setupViews() {
        // RecyclerView 설정
        binding.rvHomeAlbums.adapter = albumAdapter.withLoadStateFooter(
            footer = AlbumLoadStateAdapter()
        )
        binding.rvHomeAlbums.addItemDecoration(ItemSpacingDecoration(spacing = 12f.toPx))
        binding.rvHomeAlbums.setHasFixedSize(true)

        // 어댑터 상태 리스너 추가
        albumAdapter.addLoadStateListener { combinedLoadStates ->
            Log.d("HomeFragment", "어댑터 로드 상태 변경: ${combinedLoadStates.source.refresh}")

            // 추가 페이지 로드 상태 확인
            val appendState = combinedLoadStates.append
            Log.d("HomeFragment", "어펜드 상태: $appendState")

            // 리프레시 상태 확인
            val refreshState = combinedLoadStates.refresh
            Log.d("HomeFragment", "리프레시 상태: $refreshState")
        }

        // SwipeRefreshLayout 설정
        setupSwipeRefresh()
    }

    private fun setupSwipeRefresh() {
        // 새로고침 색상 설정 (선택 사항)
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )

        // 새로고침 리스너 설정
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("HomeFragment", "사용자 제스처로 데이터 새로고침 시작")
            refreshData()
        }
    }

    private fun refreshData() {
        // 새로고침 중임을 표시
        isCurrentlyRefreshing = true
        vm.setRefreshing(true)

        // 스크롤을 맨 위로 이동
        binding.rvHomeAlbums.scrollToPosition(0)

        // 어댑터 리프레시 호출
        Log.d("HomeFragment", "어댑터 리프레시 호출")
        albumAdapter.refresh()
    }

    private fun setupObservers() {
        observePagingData()
        observeViewEvents()
        observeRefreshState()
    }

    private fun observeRefreshState() {
        repeatOnLifecycle {
            vm.isRefreshing.collect { isRefreshing ->
                binding.swipeRefreshLayout.isRefreshing = isRefreshing
            }
        }
    }

    private fun observePagingData() {
        // 로딩 상태 관찰
        repeatOnLifecycle {
            albumAdapter.loadStateFlow.collectLatest { loadStates ->
                // 새로고침 상태 처리
                val isRefreshing = loadStates.refresh is LoadState.Loading

                // 리프레시 완료 감지
                if (isCurrentlyRefreshing && loadStates.refresh is LoadState.NotLoading) {
                    isCurrentlyRefreshing = false
                    vm.setRefreshing(false)
                    Log.d("HomeFragment", "데이터 새로고침 완료, 아이템 수: ${albumAdapter.itemCount}")

                    // 새로고침 완료 후 맨 위로 스크롤
                    binding.rvHomeAlbums.scrollToPosition(0)
                }

                // 추가 데이터 로딩 상태 (무한 스크롤)
                when (val append = loadStates.append) {
                    is LoadState.Loading -> {
                        Log.d("HomeFragment", "추가 데이터 로딩 중...")
                    }
                    is LoadState.NotLoading -> {
                        if (append.endOfPaginationReached) {
                            Log.d("HomeFragment", "모든 데이터 로드 완료 (페이징 끝)")
                        } else {
                            Log.d("HomeFragment", "추가 데이터 로드 완료")
                        }
                    }
                    is LoadState.Error -> {
                        Log.e("HomeFragment", "추가 데이터 로딩 중 오류: ${append.error.message}")
                    }
                }

                // 로딩 완료 후 데이터가 없을 때만 빈 상태 화면 표시
                val isEmpty = (loadStates.source.refresh is LoadState.NotLoading
                        && loadStates.append.endOfPaginationReached
                        && albumAdapter.itemCount < 1)

                if (isEmpty) {
                    Log.d("HomeFragment", "데이터가 비어있음")
                    // 여기에 빈 상태 화면 표시 로직 추가
                }

                // 에러 처리
                val errorState = when {
                    loadStates.append is LoadState.Error -> loadStates.append as LoadState.Error
                    loadStates.prepend is LoadState.Error -> loadStates.prepend as LoadState.Error
                    loadStates.refresh is LoadState.Error -> loadStates.refresh as LoadState.Error
                    else -> null
                }

                errorState?.let {
                    // 에러 상태 처리
                    Log.e("HomeFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")
                    binding.swipeRefreshLayout.isRefreshing = false
                    vm.setRefreshing(false)
                    isCurrentlyRefreshing = false

                    // 여기에 에러 화면 표시 로직 추가
                }
            }
        }

        // 페이징 데이터 관찰
        repeatOnLifecycle {
            vm.albums.collect { pagingData ->
                Log.d("HomeFragment", "새 페이징 데이터 수신")
                albumAdapter.submitData(pagingData)
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? HomeViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: HomeViewEvent) {
//        when (event) {
//        }
    }
}