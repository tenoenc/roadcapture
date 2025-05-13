package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.paging.map
import androidx.recyclerview.widget.ConcatAdapter
import com.tenacy.roadcapture.databinding.FragmentSearchBinding
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

@AndroidEntryPoint
class SearchFragment: BaseFragment() {

    private var _binding: FragmentSearchBinding? = null
    val binding get() = _binding!!

    private val vm: SearchViewModel by viewModels()

    private val albumAdapter: AlbumPagingAdapter by lazy { AlbumPagingAdapter() }

    private val emptyStateAdapter: EmptyStateAdapter by lazy {
        EmptyStateAdapter(EmptyItem.Search)
    }

    // 클래스 변수로 추가
    private var wasRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
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
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        setupRecyclerView()
        setupSearchBar()
    }

    private fun setupSearchBar() {
        // X 버튼 클릭 이벤트
        binding.ibtnSearchInputClear.setOnClickListener {
            binding.etSearchInput.text.clear()
        }

        // 키보드 엔터 이벤트
        binding.etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                this@SearchFragment.vm.performSearch()
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun setupRecyclerView() {
        val concatAdapter = ConcatAdapter(
            emptyStateAdapter,
            albumAdapter.withLoadStateFooter(
                footer = LoadStateAdapter()
            ),
        )

        binding.rvSearchAlbums.adapter = concatAdapter
        binding.rvSearchAlbums.addItemDecoration(ItemSpacingDecoration(spacing = 24f.toPx))
        binding.rvSearchAlbums.setHasFixedSize(true)

        // 어댑터 상태 리스너 추가
        albumAdapter.addLoadStateListener { combinedLoadStates ->
            Log.d("SearchFragment", "어댑터 로드 상태 변경: ${combinedLoadStates.source.refresh}")

            // 추가 페이지 로드 상태 확인
            val appendState = combinedLoadStates.append
            Log.d("SearchFragment", "어펜드 상태: $appendState")

            // 리프레시 상태 확인
            val refreshState = combinedLoadStates.refresh
            Log.d("SearchFragment", "리프레시 상태: $refreshState")
        }

        repeatOnLifecycle {
            while(currentCoroutineContext().isActive) {
                albumAdapter.refreshVisibleItems()
                delay(60_000)
            }
        }
    }

    private fun setupObservers() {
        observePagingData()
        observeSearchResults()
        observeViewEvents()
    }

    private fun observeSearchResults() {
        repeatOnLifecycle {
            vm.pagingData.collectLatest { pagingData ->
                albumAdapter.submitData(
                    pagingData.map {
                        AlbumItem.General(
                            value = it,
                            onItemClick = {
                                Log.d("SearchFragment", "Item Clicked!")
                                findNavController().navigate(SearchFragmentDirections.actionSearchToAlbum(it.id, it.user.id))
                            },
                            onProfileClick = {
                                Log.d("SearchFragment", "Item Clicked!")
                            },
                        )
                    }
                )
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

                // 표시/숨김 처리
                if(isRefreshing) {
                    binding.lottieSearchLoading.visibility = View.VISIBLE
                    binding.viewSearchCover.visibility = View.VISIBLE
                } else {
                    binding.lottieSearchLoading.visibility = View.GONE

                    // 리프레시 완료시 스크롤 맨 위로
                    if (isRefreshComplete && albumAdapter.itemCount > 0) {
                        binding.rvSearchAlbums.scrollToPosition(0)
                        Log.d("SearchFragment", "스크롤을 맨 위로 이동")
                    }
                }

                // 추가 데이터 로딩 상태 (무한 스크롤)
                when (val append = loadStates.append) {
                    is LoadState.Loading -> {
                        Log.d("SearchFragment", "추가 데이터 로딩 중...")
                    }
                    is LoadState.NotLoading -> {
                        if (append.endOfPaginationReached) {
                            Log.d("SearchFragment", "모든 데이터 로드 완료 (페이징 끝)")
                        } else {
                            Log.d("SearchFragment", "추가 데이터 로드 완료")
                        }
                    }
                    is LoadState.Error -> {
                        Log.e("SearchFragment", "추가 데이터 로딩 중 오류: ${append.error.message}")
                    }
                }

                // 로딩 완료 후 데이터가 없을 때
                val isEmptyAfterLoading = (loadStates.source.refresh is LoadState.NotLoading
                        && loadStates.append.endOfPaginationReached
                        && albumAdapter.itemCount < 1)

                emptyStateAdapter.isVisible = isEmptyAfterLoading

                // 로딩 완료 후 데이터가 있을 때 (최초 로딩에만 스크롤 위로)
                val isNotEmptyAfterInitialLoading = (loadStates.source.refresh is LoadState.NotLoading
                        && albumAdapter.itemCount >= 1) // 추가 로딩 중이 아닐 때만

                if (isEmptyAfterLoading || isNotEmptyAfterInitialLoading) {
                    Log.d("SearchFragment", "데이터가 비어있음")
                    // 여기에 빈 상태 화면 표시 로직 추가
                    binding.viewSearchCover.visibility = View.GONE
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
                    Log.e("SearchFragment", "데이터 로딩 중 오류 발생: ${it.error.message}")

                    // 여기에 에러 화면 표시 로직 추가
                    wasRefreshing = false
                }
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

    private fun handleViewEvents(event: SearchViewEvent) {
//        when (event) {
//        }
    }
}
