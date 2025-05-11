package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.TabAlbumBinding
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class AlbumTabFragment: BaseFragment() {

    private var _binding: TabAlbumBinding? = null
    val binding get() = _binding!!

    private val pVm: MyAlbumViewModel by viewModels(
       ownerProducer = { requireParentFragment() }
    )
    private val vm: AlbumTabViewModel by viewModels()

    private val albumAdapter: AlbumPagingAdapter by lazy { AlbumPagingAdapter() }

    // 현재 리프레시 중인지 추적
    private var wasRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabAlbumBinding.inflate(inflater, container, false)

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
            MyAlbumMoreBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<Album>(MyAlbumMoreBottomSheetFragment.RESULT_EVENT_CLICK_TOGGLE_PUBLIC)?.let {
                vm.togglePublic(it.id, it.isPublic)
            }
            bundle.getParcelable<Album>(MyAlbumMoreBottomSheetFragment.RESULT_EVENT_CLICK_DELETE)?.let {
                vm.deletePublic(it.id, it.user.id)
            }
        }
    }

    private fun setupViews() {
        setupRecyclerView()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        binding.rvTabAlbum.adapter = albumAdapter.withLoadStateFooter(
            footer = AlbumLoadStateAdapter()
        )
        binding.rvTabAlbum.addItemDecoration(ItemSpacingDecoration(spacing = 12f.toPx))
        binding.rvTabAlbum.setItemViewCacheSize(3)
        binding.rvTabAlbum.setHasFixedSize(true)

        // 어댑터 상태 리스너 추가
        albumAdapter.addLoadStateListener { combinedLoadStates ->
            Log.d("AlbumTabFragment", "어댑터 로드 상태 변경: ${combinedLoadStates.source.refresh}")

            // 추가 페이지 로드 상태 확인
            val appendState = combinedLoadStates.append
            Log.d("AlbumTabFragment", "어펜드 상태: $appendState")

            // 리프레시 상태 확인
            val refreshState = combinedLoadStates.refresh
            Log.d("AlbumTabFragment", "리프레시 상태: $refreshState")
        }

        repeatOnLifecycle {
            while(currentCoroutineContext().isActive) {
                albumAdapter.refreshVisibleItems()
                delay(60_000)
            }
        }
    }

    private fun setupSwipeRefresh() {
        // 새로고침 색상 설정 (선택 사항)
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary_normal,
        )

        // 새로고침 리스너 설정
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("AlbumTabFragment", "사용자 제스처로 데이터 새로고침 시작")
            refreshData()
        }
    }

    private fun refreshData() {
        // 새로고침 중임을 표시
        vm.setRefreshing(true)

        // 어댑터 리프레시 호출
        Log.d("AlbumTabFragment", "어댑터 리프레시 호출")
        albumAdapter.refresh()
        pVm.fetchData()
    }

    private fun setupObservers() {
        observeRefreshAllEvent()
        observeRefreshState()
        observePagingData()
        observeViewEvents()
    }

    private fun observeRefreshAllEvent() {
        repeatOnLifecycle {
            pVm.refreshAllEvent.collect {
                vm.setRefreshing(true)
                albumAdapter.refresh()
            }
        }
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
                    binding.swipeRefreshLayout.visibility = View.GONE
                } else {
                    binding.swipeRefreshLayout.visibility = View.VISIBLE

                    if(isRefreshComplete) {
                        vm.setRefreshing(false)
                        Log.d("AlbumTabFragment", "데이터 새로고침 완료, 아이템 수: ${albumAdapter.itemCount}")

                        // 새로고침 완료 후 맨 위로 스크롤
                        binding.rvTabAlbum.scrollToPosition(0)
                    }
                }

                // 추가 데이터 로딩 상태 (무한 스크롤)
                when (val append = loadStates.append) {
                    is LoadState.Loading -> {
                        Log.d("AlbumTabFragment", "추가 데이터 로딩 중...")
                    }
                    is LoadState.NotLoading -> {
                        if (append.endOfPaginationReached) {
                            Log.d("AlbumTabFragment", "모든 데이터 로드 완료 (페이징 끝)")
                        } else {
                            Log.d("AlbumTabFragment", "추가 데이터 로드 완료")
                        }
                    }
                    is LoadState.Error -> {
                        Log.e("AlbumTabFragment", "추가 데이터 로딩 중 오류: ${append.error.message}")
                    }
                }

                // 로딩 완료 후 데이터가 없을 때
                val isEmptyAfterLoading = (loadStates.source.refresh is LoadState.NotLoading
                        && loadStates.append.endOfPaginationReached
                        && albumAdapter.itemCount < 1)

                if (isEmptyAfterLoading) {
                    Log.d("AlbumTabFragment", "데이터가 비어있음")
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
                    Log.e("AlbumTabFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")
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
                Log.d("AlbumTabFragment", "새 페이징 데이터 수신")
                albumAdapter.submitData(
                    pagingData.map {
                        AlbumItem.User(
                            value = it,
                            onItemClick = {
                                Log.d("AlbumTabFragment", "Item Clicked!")
                                findNavController().navigate(MainFragmentDirections.actionMainToAlbum(it.id, it.user.id))
                            },
                            onMoreClick = { album ->
                                val bottomSheet = MyAlbumMoreBottomSheetFragment.newInstance(
                                    bundle = bundleOf(
                                        MyAlbumMoreBottomSheetFragment.PARAMS to MyAlbumMoreBottomSheetFragment.ParamsIn(album)
                                    )
                                )
                                bottomSheet.show(childFragmentManager, MyAlbumMoreBottomSheetFragment.TAG)
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
                    (event as? AlbumTabViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: AlbumTabViewEvent) {
        when (event) {
            is AlbumTabViewEvent.Refresh -> {
                refreshData()
            }
            is AlbumTabViewEvent.RefreshAll -> {
                pVm.refreshAll()
            }
        }
    }

    @Parcelize
    data class ParamsIn(
        val userId: String,
    ): Parcelable

    companion object {
        const val KEY_PARAMS = "params"

        fun newInstance(bundle: Bundle? = null): AlbumTabFragment {
            return AlbumTabFragment().apply {
                arguments = bundle
            }
        }
    }
}