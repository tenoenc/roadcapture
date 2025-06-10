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
import androidx.recyclerview.widget.ConcatAdapter
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.TabMyAlbumBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class MyAlbumTabFragment: BaseFragment() {

    private var _binding: TabMyAlbumBinding? = null
    val binding get() = _binding!!

    private val pVm: MyAlbumViewModel by viewModels(
       ownerProducer = { requireParentFragment() }
    )
    private val vm: MyAlbumTabViewModel by viewModels()

    private val albumAdapter: AlbumPagingAdapter by lazy { AlbumPagingAdapter() }

    private val emptyStateAdapter: EmptyStateAdapter by lazy {
        EmptyStateAdapter(EmptyItem.MyAlbum(28f.toPx))
    }

    private var wasRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabMyAlbumBinding.inflate(inflater, container, false)

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
            AlbumModifyMoreBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<AlbumModifyMoreBottomSheetFragment.ParamsOut.TogglePublic>(AlbumModifyMoreBottomSheetFragment.KEY_PARAMS_OUT_TOGGLE_PUBLIC)?.let {
                val album = it.album
                val updatePublic = !album.isPublic
                vm.updateAlbumPublic(album.id, updatePublic)
            }
            bundle.getParcelable<AlbumModifyMoreBottomSheetFragment.ParamsOut.Share>(AlbumModifyMoreBottomSheetFragment.KEY_PARAMS_OUT_SHARE)?.let {
                val album = it.album
                vm.createShareLink(album.id)
            }
            bundle.getParcelable<AlbumModifyMoreBottomSheetFragment.ParamsOut.Delete>(AlbumModifyMoreBottomSheetFragment.KEY_PARAMS_OUT_DELETE)?.let {
                val album = it.album
                vm.deleteAlbum(album.user.id, album.id)
            }
        }
    }

    private fun setupViews() {
        setupRecyclerView()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        val concatAdapter = ConcatAdapter(
            emptyStateAdapter,
            albumAdapter.withLoadStateFooter(footer = LoadStateAdapter()),
        )

        binding.rvTabMyAlbum.adapter = concatAdapter
        binding.rvTabMyAlbum.addItemDecoration(ItemSpacingDecoration(spacing = 12f.toPx))
        binding.rvTabMyAlbum.setItemViewCacheSize(3)
        binding.rvTabMyAlbum.setHasFixedSize(true)

        // 로드 상태 리스너 간소화
        albumAdapter.addLoadStateListener { loadStates ->
            val isLoading = loadStates.refresh is LoadState.Loading

            val isRefreshComplete = wasRefreshing && loadStates.refresh is LoadState.NotLoading
            wasRefreshing = isLoading

            // 로딩 완료 시
            if (!isLoading) {
                val isEmpty = loadStates.append.endOfPaginationReached && albumAdapter.itemCount < 1
                emptyStateAdapter.isVisible = isEmpty

                // 초기 로딩이나 사용자 리프레시인 경우에만 스크롤
                if (isRefreshComplete) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.rvTabMyAlbum.scrollToPosition(0)
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
            Log.d("MyAlbumTabFragment", "사용자 제스처로 데이터 새로고침 시작")
            refreshData()
        }
    }

    fun refreshData(includeParent: Boolean = true) {
        // 데이터 리프레시 로직
        albumAdapter.refresh()
        if(includeParent) {
            pVm.fetchData()
        }
    }

    private fun setupObservers() {
        observeRefreshAllEvent()
        observePagingData()
        observeViewEvents()
    }


    private fun observeRefreshAllEvent() {
        repeatOnLifecycle {
            pVm.refreshAllEvent.collect {
                refreshData()
            }
        }
    }

    private fun observePagingData() {
        // 페이징 데이터만 관찰
        repeatOnLifecycle {
            vm.albums.collectLatest { pagingData ->
                albumAdapter.submitData(
                    pagingData.map {
                        AlbumItem.User(
                            value = it,
                            onItemClick = {
                                findNavController().navigate(MainFragmentDirections.actionMainToAlbum(it.id, it.user.id))
                            },
                            onMoreClick = { album ->
                                val bottomSheet = AlbumModifyMoreBottomSheetFragment.newInstance(
                                    bundle = bundleOf(
                                        AlbumModifyMoreBottomSheetFragment.KEY_PARAMS_IN to AlbumModifyMoreBottomSheetFragment.ParamsIn(album)
                                    )
                                )
                                bottomSheet.show(childFragmentManager, AlbumModifyMoreBottomSheetFragment.TAG)
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
                    (event as? MyAlbumTabViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: MyAlbumTabViewEvent) {
        when(event) {
            is MyAlbumTabViewEvent.EnqueueComplete -> {
                when(event) {
                    is MyAlbumTabViewEvent.EnqueueComplete.DeleteAlbum -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.deleting_album), ToastMessageType.Info)))
                    }
                    is MyAlbumTabViewEvent.EnqueueComplete.UpdateAlbumPublic -> {
                        val `0` = event.publicText
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.changing_album_visibility, `0`), ToastMessageType.Info)))
                    }
                    is MyAlbumTabViewEvent.EnqueueComplete.CreateShareLink -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.create_share_link), ToastMessageType.Info)))
                    }
                }
            }
        }
    }

    @Parcelize
    data class ParamsIn(
        val userId: String,
    ): Parcelable

    companion object {
        const val KEY_PARAMS = "params"

        fun newInstance(bundle: Bundle? = null): MyAlbumTabFragment {
            return MyAlbumTabFragment().apply {
                arguments = bundle
            }
        }
    }
}