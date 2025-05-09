package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentMyAlbumBinding
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.user
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyAlbumFragment: BaseFragment() {

    private var _binding: FragmentMyAlbumBinding? = null
    val binding get() = _binding!!

    private val vm: MyAlbumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyAlbumBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        setupViewPager()
        setupTabLayout()
    }

    private fun setupViewPager() {
        // 뷰페이저 어댑터 설정
        val pagerAdapter = AlbumPagerAdapter(this, user!!.uid)
        binding.vpMyAlbum.adapter = pagerAdapter
        binding.vpMyAlbum.isUserInputEnabled = false
    }

    private fun setupTabLayout() {
        // TabLayout과 ViewPager2 연결
        TabLayoutMediator(binding.tlMyAlbum, binding.vpMyAlbum) { tab, position ->
            when (position) {
                0 -> tab.text = "앨범"
                1 -> tab.text = "추억"
            }
        }.attach()
    }

    private fun setupObservers() {
        observeUserData()
        observeViewEvents()
    }

    private fun observeUserData() {
        // ViewModel의 user StateFlow 관찰
        repeatOnLifecycle {
            vm.totalCounts.collect {
                if(it.isEmpty()) return@collect
                updateTabTexts(it[MyAlbumViewModel.KEY_ALBUM_COUNT]!!, it[MyAlbumViewModel.KEY_MEMORY_COUNT]!!)
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? MyAlbumViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: MyAlbumViewEvent) {
        // 이벤트 처리 로직
    }

    private fun createTabText(mainText: String, countText: String): SpannableString {
        val fullText = "$mainText $countText"
        val spannableString = SpannableString(fullText)

        val countStart = mainText.length + 1

        spannableString.setSpan(
            TextAppearanceSpan(requireContext(), R.style.Body_10_M),
            countStart, fullText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannableString
    }

    private fun updateTabTexts(albumCount: Long, memoryCount: Long) {
        // 각 탭의 텍스트만 업데이트
        val tabLayout = binding.tlMyAlbum
        if (tabLayout.tabCount >= 2) {
            tabLayout.getTabAt(0)?.text = createTabText("앨범", "${albumCount}개")
            tabLayout.getTabAt(1)?.text = createTabText("추억", "${memoryCount}개")
        }
    }
}