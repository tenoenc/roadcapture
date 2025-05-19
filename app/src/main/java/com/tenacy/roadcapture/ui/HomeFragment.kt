package com.tenacy.roadcapture.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import com.facebook.shimmer.Shimmer
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.databinding.FragmentHomeBinding
import com.tenacy.roadcapture.util.consumeOnce
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment: BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels()

    // 기존 방식과 유사하게 lazy 초기화 유지 가능
    private val albumAdapter: AlbumPagingAdapter by lazy { AlbumPagingAdapter() }

    // 광고 어댑터도 lazy로 초기화
    private val adAdapter by lazy {
        AdmobContainerAdapter(
            originalAdapter = albumAdapter,
            adPosition = 5,
            adInterval = 10
        )
    }

    private var wasRefreshing = false

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
        setupRecyclerView()
        setupShimmer()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        // 로드 상태 어댑터 생성
        val loadStateAdapter = LoadStateAdapter()

        // 광고와 로드 상태 어댑터를 결합한 최종 어댑터
        val finalAdapter = adAdapter.withLoadStateAdapter(loadStateAdapter)

        // 최종 어댑터를 RecyclerView에 설정
        binding.rvHomeAlbums.adapter = finalAdapter

        // 나머지 설정은 동일하게 유지
        binding.rvHomeAlbums.addItemDecoration(ItemSpacingDecoration(spacing = 24f.toPx))
        binding.rvHomeAlbums.setHasFixedSize(true)

        // 뷰홀더 캐시 사이즈 증가
        binding.rvHomeAlbums.setItemViewCacheSize(5)

        // 어댑터 상태 리스너
        albumAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading

            val isRefreshComplete = wasRefreshing && loadStates.refresh is LoadState.NotLoading
            wasRefreshing = isLoading

            // 로딩 시 Shimmer 효과 처리
            if (isLoading && !binding.swipeRefreshLayout.isRefreshing) {
                // 사용자 제스처가 아닌 경우에만 Shimmer 표시
                binding.shimmerLayout.visibility = View.VISIBLE
                binding.shimmerLayout.startShimmer()
                binding.swipeRefreshLayout.visibility = View.GONE
            } else {
                // 로딩 완료 시
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.visibility = View.GONE
                binding.swipeRefreshLayout.visibility = View.VISIBLE

                if(isRefreshComplete) {
                    // 로딩 완료 후 맨 위로 스크롤
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.rvHomeAlbums.scrollToPosition(0)
                }
            }

            // 에러 처리
            val errorState = when {
                loadStates.append is LoadState.Error -> loadStates.append as LoadState.Error
                loadStates.prepend is LoadState.Error -> loadStates.prepend as LoadState.Error
                loadStates.refresh is LoadState.Error -> loadStates.refresh as LoadState.Error
                else -> null
            }

            errorState?.let {
                wasRefreshing = false
                binding.swipeRefreshLayout.isRefreshing = false
                Log.e("HomeFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")
            }
        }
    }

    private fun setupShimmer() {
        with(binding.shimmerLayout) {
            val shimmer = Shimmer.ColorHighlightBuilder()
                .setBaseColor(ContextCompat.getColor(requireContext(), R.color.fill_assistive))
                .setHighlightColor(ContextCompat.getColor(requireContext(), R.color.fill_alternative))
                .setBaseAlpha(1.0f)
                .setHighlightAlpha(0.3f)
                .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
                .setDuration(1500)
                .setRepeatMode(ValueAnimator.RESTART)
                .setIntensity(0.3f)
                .setDropoff(0.5f)
                .setTilt(20f)
                .build()

            setShimmer(shimmer)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary_normal)

        // 사용자 제스처로 인한 리프레시
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun refreshData() {
        albumAdapter.refresh()
    }

    private fun setupObservers() {
        observeSavedState()
        observePagingData()
        observeViewEvents()
    }

    private fun observeSavedState() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_ALBUM) { bundle ->
                if (bundle == null) return@consumeOnce
                bundle.getLong(RESULT_FORBIDDEN, 0L).takeIf { it > 0 }?.let {
                    albumAdapter.refresh()
                }
            }
        }
    }

    private fun observePagingData() {
        repeatOnLifecycle {
            vm.albums.collectLatest { pagingData ->
                albumAdapter.submitData(
                    pagingData.map {
                        AlbumItem.General(
                            value = it,
                            onItemClick = {
                                findNavController().navigate(MainFragmentDirections.actionMainToAlbum(it.id, it.user.id))
                            },
                            onProfileClick = {
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
                    (event as? HomeViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: HomeViewEvent) {
        when (event) {
            is HomeViewEvent.Search -> {
                findNavController().navigate(MainFragmentDirections.actionMainToSearch(SearchFilter.All))
            }
        }
    }

    companion object {
        const val KEY_ALBUM = "album"
        const val RESULT_FORBIDDEN = "result_forbidden"
    }
}