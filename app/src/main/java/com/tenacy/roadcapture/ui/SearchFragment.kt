package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.paging.map
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.SimpleItemAnimator
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.AlbumFilter
import com.tenacy.roadcapture.data.firebase.SearchFilter
import com.tenacy.roadcapture.databinding.FragmentSearchBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class SearchFragment: BaseFragment() {

    private var _binding: FragmentSearchBinding? = null
    val binding get() = _binding!!

    private val vm: SearchViewModel by viewModels()
    private val args: SearchFragmentArgs by navArgs()

    private val albumAdapter: AlbumPagingAdapter by lazy {
        AlbumPagingAdapter(args.albumFilter !is SearchFilter.Scrap)
    }
    private val emptyStateAdapter = EmptyStateAdapter(EmptyItem.Search)

    // RecyclerView 상태 관리
    private var recyclerViewState: Parcelable? = null

    // LoadStateListener 중복 등록 방지용
    private var isLoadStateListenerRegistered = false

    private var wasRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()

        // 어댑터가 없을 때만 설정
        if (binding.rvSearchAlbums.adapter == null) {
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
        setupSearchBar()
    }

    private fun setupRecyclerViewBase() {
        with(binding.rvSearchAlbums) {
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

        binding.rvSearchAlbums.adapter = concatAdapter
    }

    private fun setupLoadStateListener() {
        albumAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading
            val isRefreshComplete = wasRefreshing && loadStates.refresh is LoadState.NotLoading
            wasRefreshing = isLoading

            // 로딩 상태 처리
            if (isLoading) {
                binding.lottieSearchLoading.visibility = View.VISIBLE
                binding.viewSearchCover.visibility = View.VISIBLE
            } else {
                // 로딩 완료 시
                binding.lottieSearchLoading.visibility = View.GONE
                val isEmpty = loadStates.append.endOfPaginationReached && albumAdapter.itemCount < 1
                emptyStateAdapter.isVisible = isEmpty
                binding.viewSearchCover.visibility = View.GONE

                // 초기 로딩이나 사용자 리프레시인 경우에만 스크롤
                if (isRefreshComplete) {
                    binding.rvSearchAlbums.scrollToPosition(0)
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
                Log.e("SearchFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")
                binding.lottieSearchLoading.visibility = View.GONE
                binding.viewSearchCover.visibility = View.GONE
            }
        }
    }

    private fun setupSearchBar() {
        // X 버튼 클릭 이벤트
        binding.ibtnSearchInputClear.setSafeClickListener {
            binding.etSearchInput.text.clear()
        }

        // 키보드 엔터 이벤트
        binding.etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun performSearch() {
        vm.performSearch()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun setupObservers() {
        observeSearchResults()
        observeViewEvents()
        setupAlbumRefreshTimer()
    }

    private fun observeSearchResults() {
        repeatOnLifecycle {
            vm.pagingData.collectLatest { pagingData ->
                albumAdapter.submitData(
                    pagingData.map {
                        AlbumItem.General(
                            value = it,
                            onItemClick = {
                                findNavController().navigate(SearchFragmentDirections.actionSearchToAlbum(it.id, it.user.id))
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
                    (event as? SearchViewEvent)?.let { handleViewEvents(it) }
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

    private fun handleViewEvents(event: SearchViewEvent) {
        // 필요한 이벤트 처리 로직 구현
        when(event) {
            is SearchViewEvent.ReportComplete -> {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.report_submitted), ToastMessageType.Success)))
                }
            }
        }
    }

    private fun saveRecyclerViewState() {
        binding.rvSearchAlbums.layoutManager?.let { layoutManager ->
            recyclerViewState = layoutManager.onSaveInstanceState()
        }
    }

    private fun restoreRecyclerViewState() {
        recyclerViewState?.let { state ->
            binding.rvSearchAlbums.post {
                binding.rvSearchAlbums.layoutManager?.onRestoreInstanceState(state)
            }
        }
        recyclerViewState = null
    }
}