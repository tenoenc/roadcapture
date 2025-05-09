package com.tenacy.roadcapture.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import com.facebook.shimmer.Shimmer
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.databinding.FragmentScrapBinding
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

class ScrapFragment: BaseFragment() {

    private var _binding: FragmentScrapBinding? = null
    val binding get() = _binding!!

    private val vm: ScrapViewModel by viewModels()

    private val albumAdapter: AlbumPagingAdapter by lazy { AlbumPagingAdapter() }

    // 현재 리프레시 중인지 추적
    private var wasRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScrapBinding.inflate(inflater, container, false)

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
        setupRecyclerView()
        setupShimmer()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        binding.rvScrapAlbums.adapter = albumAdapter.withLoadStateFooter(
            footer = AlbumLoadStateAdapter()
        )
        binding.rvScrapAlbums.addItemDecoration(ItemSpacingDecoration(spacing = 24f.toPx))
        binding.rvScrapAlbums.setHasFixedSize(true)

        // 어댑터 상태 리스너 추가
        albumAdapter.addLoadStateListener { combinedLoadStates ->
            Log.d("ScrapFragment", "어댑터 로드 상태 변경: ${combinedLoadStates.source.refresh}")

            // 추가 페이지 로드 상태 확인
            val appendState = combinedLoadStates.append
            Log.d("ScrapFragment", "어펜드 상태: $appendState")

            // 리프레시 상태 확인
            val refreshState = combinedLoadStates.refresh
            Log.d("ScrapFragment", "리프레시 상태: $refreshState")
        }

        repeatOnLifecycle {
            while(currentCoroutineContext().isActive) {
                albumAdapter.refreshVisibleItems()
                delay(60_000)
            }
        }
    }

    private fun setupShimmer() {
        with(binding.shimmerLayout) {
            // 새로운 Shimmer 객체 생성
            val shimmer = Shimmer.ColorHighlightBuilder()
                .setBaseColor(ContextCompat.getColor(requireContext(), R.color.fill_assistive))
                .setHighlightColor(ContextCompat.getColor(requireContext(), R.color.fill_alternative))
                .setBaseAlpha(1.0f)
                .setHighlightAlpha(0.3f)
                .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)  // 방향 설정
                .setDuration(1500)  // 애니메이션 지속 시간
                .setRepeatMode(ValueAnimator.RESTART)  // 반복 모드
                .setIntensity(0.3f)  // 강도
                .setDropoff(0.5f)  // 그라데이션 드롭오프
                .setTilt(20f)  // 기울기 각도
                .build()

            // 생성한 Shimmer 설정 적용
            setShimmer(shimmer)
        }
    }

    private fun setupSwipeRefresh() {
        // 새로고침 색상 설정 (선택 사항)
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary_normal,
//            android.R.color.holo_blue_bright,
//            android.R.color.holo_green_light,
//            android.R.color.holo_orange_light,
//            android.R.color.holo_red_light
        )

        // 새로고침 리스너 설정
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("ScrapFragment", "사용자 제스처로 데이터 새로고침 시작")
            refreshData()
        }
    }

    private fun refreshData() {
        // 새로고침 중임을 표시
        vm.setRefreshing(true)

        // 어댑터 리프레시 호출
        Log.d("ScrapFragment", "어댑터 리프레시 호출")
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

                // 리프레시 완료 감지 (리프레싱이 true에서 false로 바뀌었을 때)
                val isRefreshComplete = wasRefreshing && !isRefreshing
                wasRefreshing = isRefreshing

                // Shimmer 효과 표시/숨김 처리
                if (isRefreshing && !binding.swipeRefreshLayout.isRefreshing) {
                    binding.shimmerLayout.visibility = View.VISIBLE
                    binding.shimmerLayout.startShimmer()
                    binding.swipeRefreshLayout.visibility = View.GONE
                } else {
                    binding.shimmerLayout.stopShimmer()
                    binding.shimmerLayout.visibility = View.GONE
                    binding.swipeRefreshLayout.visibility = View.VISIBLE

                    if(isRefreshComplete && albumAdapter.itemCount > 0) {
                        vm.setRefreshing(false)
                        Log.d("ScrapFragment", "데이터 새로고침 완료, 아이템 수: ${albumAdapter.itemCount}")

                        // 새로고침 완료 후 맨 위로 스크롤
                        binding.rvScrapAlbums.scrollToPosition(0)
                    }
                }

                // 추가 데이터 로딩 상태 (무한 스크롤)
                when (val append = loadStates.append) {
                    is LoadState.Loading -> {
                        Log.d("ScrapFragment", "추가 데이터 로딩 중...")
                    }
                    is LoadState.NotLoading -> {
                        if (append.endOfPaginationReached) {
                            Log.d("ScrapFragment", "모든 데이터 로드 완료 (페이징 끝)")
                        } else {
                            Log.d("ScrapFragment", "추가 데이터 로드 완료")
                        }
                    }
                    is LoadState.Error -> {
                        Log.e("ScrapFragment", "추가 데이터 로딩 중 오류: ${append.error.message}")
                    }
                }

                // 로딩 완료 후 데이터가 없을 때
                val isEmptyAfterLoading = (loadStates.source.refresh is LoadState.NotLoading
                        && loadStates.append.endOfPaginationReached
                        && albumAdapter.itemCount < 1)

                if (isEmptyAfterLoading) {
                    Log.d("ScrapFragment", "데이터가 비어있음")
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
                    Log.e("ScrapFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")
                    binding.swipeRefreshLayout.isRefreshing = false
                    vm.setRefreshing(false)
                    wasRefreshing = false

                    // 여기에 에러 화면 표시 로직 추가
                }
            }
        }

        // 페이징 데이터 관찰
        repeatOnLifecycle {
            vm.albums.collectLatest { pagingData ->
                Log.d("ScrapFragment", "새 페이징 데이터 수신")
                albumAdapter.submitData(
                    pagingData.map {
                        AlbumItem(
                            value = it,
                            onItemClick = {
                                Log.d("ScrapFragment", "Item Clicked!")
                                findNavController().navigate(MainFragmentDirections.actionMainToAlbum(it.id))
                            },
                            onProfileClick = {
                                Log.d("ScrapFragment", "Profile Clicked!")
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
                    (event as? ScrapViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: ScrapViewEvent) {
        when (event) {
            is ScrapViewEvent.Search -> {
                findNavController().navigate(MainFragmentDirections.actionMainToSearch(AlbumFilter.SCRAP))
            }
        }
    }
}