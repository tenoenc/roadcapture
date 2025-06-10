package com.tenacy.roadcapture.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.SimpleItemAnimator
import com.facebook.shimmer.Shimmer
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.databinding.FragmentScrapBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class ScrapFragment: BaseFragment() {

    private var _binding: FragmentScrapBinding? = null
    val binding get() = _binding!!

    private val vm: ScrapViewModel by viewModels()

    private val albumAdapter = AlbumPagingAdapter(scrapVisible = false)
    private val emptyStateAdapter = EmptyStateAdapter(EmptyItem.Scrap)

    // RecyclerView 상태 관리
    private var recyclerViewState: Parcelable? = null

    // LoadStateListener 중복 등록 방지용
    private var isLoadStateListenerRegistered = false

    private var wasRefreshing = false
    private var requireNoShimmer: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
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

        // 어댑터가 없을 때만 설정
        if (binding.rvScrapAlbums.adapter == null) {
            setupAdapter()
        }

        setupObservers()

        // 스크롤 위치 복원
        restoreRecyclerViewState()
    }

    override fun onDestroyView() {
        // RecyclerView 상태 저장
        saveRecyclerViewState()

        // ViewBinding만 정리, adapter는 그대로 유지
        super.onDestroyView()
        _binding = null
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            ReportBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<ReportBottomSheetFragment.ParamsOut.Report>(ReportBottomSheetFragment.KEY_PARAMS_OUT_REPORT)
                ?.let {
                    Log.d("TAG", "Positive Button Clicked!")
                    vm.report(it.albumId, it.reason)
                }
        }
        childFragmentManager.setFragmentResultListener(
            AlbumMoreBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<AlbumMoreBottomSheetFragment.ParamsOut.Report>(AlbumMoreBottomSheetFragment.KEY_PARAMS_OUT_REPORT)
                ?.let {
                    Log.d("TAG", "Positive Button Clicked!")
                    val bottomSheet = ReportBottomSheetFragment.newInstance(
                        bundleOf(
                            ReportBottomSheetFragment.KEY_PARAMS_IN to ReportBottomSheetFragment.ParamsIn(it.albumId)
                        )
                    )
                    bottomSheet.show(childFragmentManager, ReportBottomSheetFragment.TAG)
                }
        }
    }

    private fun setupViews() {
        setupRecyclerViewBase()
        setupShimmer()
        setupSwipeRefresh()
    }

    private fun setupRecyclerViewBase() {
        with(binding.rvScrapAlbums) {
            // ItemDecoration이 중복 추가되지 않도록 체크
            if (itemDecorationCount == 0) {
                addItemDecoration(ItemSpacingDecoration(spacing = 24f.toPx))
            }

            // 아이템 변경 애니메이션 비활성화 (깜빡임 방지)
            (itemAnimator as? SimpleItemAnimator)?.apply {
                supportsChangeAnimations = false
                changeDuration = 0
            }

            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        // LoadStateListener는 한 번만 등록
        if (!isLoadStateListenerRegistered) {
            setupLoadStateListener()
            isLoadStateListenerRegistered = true
        }
    }

    private fun setupAdapter() {
        val concatAdapter = ConcatAdapter(
            emptyStateAdapter,
            albumAdapter.withLoadStateFooter(
                footer = LoadStateAdapter()
            ),
        )

        binding.rvScrapAlbums.adapter = concatAdapter
    }

    private fun setupLoadStateListener() {
        albumAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading
            val isRefreshComplete = wasRefreshing && loadStates.refresh is LoadState.NotLoading
            wasRefreshing = isLoading

            // 로딩 상태 처리
            if (isLoading && !binding.swipeRefreshLayout.isRefreshing && !requireNoShimmer) {
                // 사용자 제스처가 아닌 경우만 Shimmer 표시
                binding.shimmerLayout.visibility = View.VISIBLE
                binding.shimmerLayout.startShimmer()
                binding.swipeRefreshLayout.visibility = View.GONE
            } else {
                // 로딩 완료 시
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.visibility = View.GONE
                binding.swipeRefreshLayout.visibility = View.VISIBLE

                // 빈 상태 처리
                val isEmpty = loadStates.append.endOfPaginationReached && albumAdapter.itemCount < 1
                emptyStateAdapter.isVisible = isEmpty

                // 초기 로딩이나 사용자 리프레시인 경우에만 스크롤
                if (isRefreshComplete) {
                    requireNoShimmer = false
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.rvScrapAlbums.scrollToPosition(0)
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
                requireNoShimmer = false
                wasRefreshing = false
                binding.swipeRefreshLayout.isRefreshing = false
                Log.e("ScrapFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")
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

    fun refreshData(requireNoShimmer: Boolean = false) {
        this@ScrapFragment.requireNoShimmer = requireNoShimmer
        albumAdapter.refresh()
    }

    private fun setupObservers() {
        observePagingData()
        observeViewEvents()
        setupAlbumRefreshTimer()
    }

    private fun observePagingData() {
        // 페이징 데이터 관찰
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
                            onLongClick = { albumId ->
                                val bottomSheet = AlbumMoreBottomSheetFragment.newInstance(
                                    bundleOf(
                                        AlbumMoreBottomSheetFragment.KEY_PARAMS_IN to AlbumMoreBottomSheetFragment.ParamsIn(albumId)
                                    )
                                )
                                bottomSheet.show(childFragmentManager, ReportBottomSheetFragment.TAG)
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

    private fun setupAlbumRefreshTimer() {
        repeatOnLifecycle {
            while(currentCoroutineContext().isActive) {
                albumAdapter.refreshVisibleItems()
                delay(60_000)
            }
        }
    }

    private fun handleViewEvents(event: ScrapViewEvent) {
        when (event) {
            is ScrapViewEvent.Search -> {
                findNavController().navigate(MainFragmentDirections.actionMainToSearch(SearchFilter.Scrap))
            }
            is ScrapViewEvent.ReportComplete -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.report_submitted), ToastMessageType.Success)))
                }
            }
        }
    }

    private fun saveRecyclerViewState() {
        binding.rvScrapAlbums.layoutManager?.let { layoutManager ->
            recyclerViewState = layoutManager.onSaveInstanceState()
        }
    }

    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvScrapAlbums.post {
                binding.rvScrapAlbums.layoutManager?.onRestoreInstanceState(state)
            }
        }
        recyclerViewState = null
    }
}