package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.AppPrefs
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.databinding.FragmentMainBinding
import com.tenacy.roadcapture.manager.AppReviewManager
import com.tenacy.roadcapture.util.consumeOnce
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    val binding get() = _binding!!

    private val vm: MainViewModel by viewModels()

    @Inject
    lateinit var appReviewManager: AppReviewManager

    // 메인 페이저 어댑터
    private lateinit var viewPagerAdapter: MainViewPagerAdapter

    private var previousPosition: Int? = null

    // ViewPager 페이지 변경 콜백
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            // 바텀 네비게이션 아이템 선택 상태 업데이트
            val destinationId = when(position) {
                0 -> R.id.homeFragment
                1 -> R.id.scrapFragment
                2 -> R.id.myAlbumFragment
                3 -> R.id.appInfoFragment
                else -> R.id.homeFragment
            }
            binding.bottomNav.selectItem(destinationId)

            // 프래그먼트 찾기
            val currentFragment = viewPagerAdapter.getFragment(position)
            val previousFragment = previousPosition?.let { viewPagerAdapter.getFragment(it) }

            // 가시성 콜백 호출
            (previousFragment as? FragmentVisibilityCallback)?.onBecameInvisible()
            (currentFragment as? FragmentVisibilityCallback)?.onBecameVisible()

            previousPosition = position
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupObservers()
    }

    override fun onStart() {
        super.onStart()
        if(!AppPrefs.pendingDeepLinkShareId.isNullOrBlank()) {
            if(!AppPrefs.isDirectDeepLink) {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.move_to_shared_album), ToastMessageType.Info)))
            }
            findNavController().navigate(MainFragmentDirections.actionMainToAlbum())
        }
    }

    override fun onResume() {
        super.onResume()
        // 페이지 변경 콜백 등록
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onPause() {
        super.onPause()
        // 페이지 변경 콜백 해제
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            TripBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<TripBeforeBottomSheetFragment.ParamsOut.Positive>(TripBeforeBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                findNavController().navigate(MainFragmentDirections.actionMainToTrip())
            }
        }
        childFragmentManager.setFragmentResultListener(
            TripOngoingBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<TripOngoingBottomSheetFragment.ParamsOut.Positive>(TripOngoingBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                findNavController().navigate(MainFragmentDirections.actionMainToTrip())
            }
        }
    }

    private fun setupViews() {
        // ViewPager2 어댑터 설정
        viewPagerAdapter = MainViewPagerAdapter(this)
        binding.viewPager.apply {
            adapter = viewPagerAdapter
            // 스와이프 비활성화 (선택사항)
            isUserInputEnabled = false
            // 오프스크린 페이지 제한 (메모리 관리)
            offscreenPageLimit = 4
        }

        // 바텀 네비게이션 클릭 리스너
        binding.bottomNav.setupWithCustomNavigation { destinationId ->
            val position = when(destinationId) {
                R.id.homeFragment -> 0
                R.id.scrapFragment -> 1
                R.id.myAlbumFragment -> 2
                R.id.appInfoFragment -> 3
                else -> 0
            }
            binding.viewPager.setCurrentItem(position, false)
        }

        // 초기 선택 페이지 설정
        binding.bottomNav.selectItem(R.id.homeFragment)

        viewPagerAdapter.registerFragmentTransactionCallback(object : FragmentStateAdapter.FragmentTransactionCallback() {
            override fun onFragmentMaxLifecyclePreUpdated(
                fragment: Fragment,
                maxLifecycleState: Lifecycle.State
            ): OnPostEventListener {
                if (binding.childVm == null && fragment is AppInfoFragment && maxLifecycleState == Lifecycle.State.RESUMED) {
                    val childViewModel = ViewModelProvider(fragment)[AppInfoViewModel::class.java]
                    binding.childVm = childViewModel
                }
                return super.onFragmentMaxLifecyclePreUpdated(fragment, maxLifecycleState)
            }
        })
    }

    private fun setupObservers() {
        observeViewEvents()
        observeSavedState()
    }

    private fun observeSavedState() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_ALBUM_UPLOAD_PROGRESS) { bundle ->
                if (bundle == null) return@consumeOnce
                val albumUploaded = bundle.getBoolean(RESULT_ALBUM_UPLOADED)
                if(albumUploaded) {
                    Log.d("MainFragment", "Album uploaded")
                    appReviewManager.requestReview()
                }
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? MainViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: MainViewEvent) {
        when (event) {
            is MainViewEvent.Logout -> {
                mainActivity.vm.logout()
            }
            is MainViewEvent.ShowTripBefore -> {
                // Album.createdAt 대신 TravelStatePref.isTraveling 사용
                if(TravelPref.isTraveling) {
                    val bottomSheet = TripOngoingBottomSheetFragment.newInstance()
                    bottomSheet.show(childFragmentManager, TripOngoingBottomSheetFragment.TAG)
                } else {
                    val bottomSheet = TripBeforeBottomSheetFragment.newInstance()
                    bottomSheet.show(childFragmentManager, TripBeforeBottomSheetFragment.TAG)
                }
            }
        }
    }

    /**
     * ViewPager2용 어댑터
     */
    private inner class MainViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when(position) {
                0 -> HomeFragment()
                1 -> ScrapFragment()
                2 -> MyAlbumFragment()
                3 -> AppInfoFragment()
                else -> HomeFragment()
            }
        }

        fun getFragment(position: Int): Fragment? {
            val name = "f" + getItemId(position) // FragmentStateAdapter가 내부적으로 사용하는 태그 형식
            return childFragmentManager.findFragmentByTag(name)
        }
    }

    companion object {
        const val KEY_ALBUM_UPLOAD_PROGRESS = "album_upload_progress"
        const val RESULT_ALBUM_UPLOADED = "album_uploaded"
    }
}
