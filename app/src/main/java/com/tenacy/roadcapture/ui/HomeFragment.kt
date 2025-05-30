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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import androidx.recyclerview.widget.SimpleItemAnimator
import com.facebook.shimmer.Shimmer
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.databinding.FragmentHomeBinding
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.ui.dto.AlbumItemWithAds
import com.tenacy.roadcapture.util.consumeOnce
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels()

    private val albumAdapter = AlbumWithAdsPagingAdapter()
    private val loadStateAdapter = LoadStateAdapter()

    // RecyclerView 상태 관리
    private var recyclerViewState: Parcelable? = null

    // LoadStateListener 중복 등록 방지용
    private var isLoadStateListenerRegistered = false

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    private var wasRefreshing = false
    private var requireNoShimmer: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
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
        setupAdapter()
        setupObservers()

        // 스크롤 위치 복원
        restoreRecyclerViewState()

        // AppInfoFragment에 추가할 코드 예시
        if (BuildConfig.DEBUG) {
            binding.btnHomeTestSubscription.visibility = View.VISIBLE
            binding.btnHomeTestSubscription.setOnClickListener {
                subscriptionManager.debugSubscriptionStatus()
            }
        }
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

    private fun setupAdapter() {
        // 리사이클러뷰에 어댑터 설정
        binding.rvHomeAlbums.apply {
            adapter = albumAdapter.withLoadStateFooter(loadStateAdapter)
        }
    }

    private fun setupRecyclerViewBase() {
        with(binding.rvHomeAlbums) {
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

    private fun setupLoadStateListener() {
        albumAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading
            val isRefreshComplete = wasRefreshing && loadStates.refresh is LoadState.NotLoading
            wasRefreshing = isLoading

            // 로딩 시 Shimmer 효과 처리
            if (isLoading && !binding.swipeRefreshLayout.isRefreshing && !requireNoShimmer) {
                binding.shimmerLayout.visibility = View.VISIBLE
                binding.shimmerLayout.startShimmer()
                binding.swipeRefreshLayout.visibility = View.GONE
            } else {
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.visibility = View.GONE
                binding.swipeRefreshLayout.visibility = View.VISIBLE

                if (isRefreshComplete) {
                    requireNoShimmer = false
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
                requireNoShimmer = false
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
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun setupObservers() {
        observeSubscriptionState()
        observeSavedState()
        observePagingData()
        observeViewEvents()
    }

    private fun observeSubscriptionState() {
        // 구독 상태 변화 감지 및 어댑터 업데이트
        repeatOnLifecycle {
            vm.subscriptionState.collect { state ->
                // 링크된 계정 처리 (필요 시)
                if (state.isLinkedToOtherAccount) {
                    // showLinkedAccountMessage(state.linkedAccountId)
                } else {
                    // hideLinkedAccountMessage()
                }
            }
        }
    }

    private fun observeSavedState() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_ALBUM) { bundle ->
                if (bundle == null) return@consumeOnce
                bundle.getLong(RESULT_FORBIDDEN, 0L).takeIf { it > 0 }?.let {
                    refreshData(requireNoShimmer = true)
                }
            }
        }
    }

    private fun observePagingData() {
        repeatOnLifecycle {
            vm.albums.collectLatest { pagingData ->
                albumAdapter.submitData(
                    pagingData.map {
                        when (it) {
                            is AlbumItemWithAds.Album.General -> it.copy(
                                onItemClick = {
                                    findNavController().navigate(
                                        MainFragmentDirections.actionMainToAlbum(
                                            it.id,
                                            it.value.user.id
                                        )
                                    )
                                },
                                onProfileClick = {
                                    // 프로필 클릭 처리
                                },
                                onLongClick = { albumId ->
                                    val bottomSheet = AlbumMoreBottomSheetFragment.newInstance(
                                        bundleOf(
                                            AlbumMoreBottomSheetFragment.KEY_PARAMS_IN to AlbumMoreBottomSheetFragment.ParamsIn(
                                                albumId
                                            )
                                        )
                                    )
                                    bottomSheet.show(childFragmentManager, AlbumMoreBottomSheetFragment.TAG)
                                }
                            )

                            else -> it
                        }
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

            is HomeViewEvent.ReportComplete -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    mainActivity.vm.viewEvent(
                        GlobalViewEvent.Toast(
                            ToastModel(
                                "신고 내용이 접수되었어요",
                                ToastMessageType.Success
                            )
                        )
                    )
                }
            }
        }
    }

    private fun saveRecyclerViewState() {
        binding.rvHomeAlbums.layoutManager?.let { layoutManager ->
            recyclerViewState = layoutManager.onSaveInstanceState()
        }
    }

    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvHomeAlbums.post {
                binding.rvHomeAlbums.layoutManager?.onRestoreInstanceState(state)
            }
        }
        recyclerViewState = null
    }

    fun refreshData(requireNoShimmer: Boolean = false) {
        this@HomeFragment.requireNoShimmer = requireNoShimmer
        albumAdapter.refresh()
    }

    companion object {
        const val KEY_ALBUM = "album"
        const val RESULT_FORBIDDEN = "result_forbidden"
    }
}