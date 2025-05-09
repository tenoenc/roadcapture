package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.Album
import com.tenacy.roadcapture.databinding.FragmentMainBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainFragment: BaseFragment() {

    private var _binding: FragmentMainBinding? = null
    val binding get() = _binding!!

    private val vm: MainViewModel by viewModels()

    // 메인 페이저 어댑터
    private lateinit var viewPagerAdapter: MainViewPagerAdapter

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
            bundle.getString(TripBeforeBottomSheetFragment.RESULT_EVENT_CLICK_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                findNavController().navigate(MainFragmentDirections.actionMainToTrip())
            }
        }
        childFragmentManager.setFragmentResultListener(
            TripOngoingBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getString(TripOngoingBottomSheetFragment.RESULT_EVENT_CLICK_POSITIVE)?.let {
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
    }

    private fun setupObservers() {
        observeViewEvents()
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
                mainActivity.signOut()
            }
            is MainViewEvent.ShowTripBefore -> {
                if(Album.createdAt > 0L) {
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
    }
}
