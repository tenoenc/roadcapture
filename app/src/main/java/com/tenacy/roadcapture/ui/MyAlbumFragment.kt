package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.esafirm.imagepicker.features.ImagePickerConfig
import com.esafirm.imagepicker.features.ImagePickerLauncher
import com.esafirm.imagepicker.features.ImagePickerMode
import com.esafirm.imagepicker.features.registerImagePicker
import com.esafirm.imagepicker.model.Image
import com.google.android.material.tabs.TabLayoutMediator
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.firebase.exception.SystemConfigException
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.databinding.FragmentMyAlbumBinding
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.UpdateUserPhotoWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MyAlbumFragment: BaseFragment() {

    private var _binding: FragmentMyAlbumBinding? = null
    val binding get() = _binding!!

    private val vm: MyAlbumViewModel by viewModels()

    private lateinit var imagePickerLauncher: ImagePickerLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        setupImagePicker()
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

    private fun setupImagePicker() {
        imagePickerLauncher = registerImagePicker { result: List<Image> ->
            result.getOrNull(0)?.let {
                UpdateUserPhotoWorker.enqueueOneTimeWork(requireContext(), it.uri)
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.profile_photo_changing))))
            }
        }
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            ProfileMoreBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<ProfileMoreBottomSheetFragment.ParamsOut.ModifyPhoto>(ProfileMoreBottomSheetFragment.KEY_PARAMS_OUT_MODIFY_PHOTO)?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    // [VALIDATE_SYSTEM_CONFIG]
                    try {
                        validateSystemConfig()
                    } catch (exception: SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        // 프로필 사진 변경
                        imagePickerLauncher.launch(ImagePickerConfig { mode = ImagePickerMode.SINGLE })
                    }
                }
            }
            bundle.getParcelable<ProfileMoreBottomSheetFragment.ParamsOut.ModifyName>(ProfileMoreBottomSheetFragment.KEY_PARAMS_OUT_MODIFY_NAME)?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    // [VALIDATE_SYSTEM_CONFIG]
                    try {
                        validateSystemConfig()
                    } catch (exception: SystemConfigException) {
                        handleSystemConfigException(exception)
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        // 이름 변경 -> 화면 이동
                        findNavController().navigate(MainFragmentDirections.actionMainToModifyUsername())
                    }
                }
            }
        }
    }

    private fun setupViews() {
        setupViewPager()
        setupTabLayout()
    }

    private fun setupViewPager() {
        // 뷰페이저 어댑터 설정
        val pagerAdapter = AlbumPagerAdapter(this, UserPref.id)
        binding.vpMyAlbum.adapter = pagerAdapter
        binding.vpMyAlbum.isUserInputEnabled = false
    }

    private fun setupTabLayout() {
        // TabLayout과 ViewPager2 연결
        TabLayoutMediator(binding.tlMyAlbum, binding.vpMyAlbum) { tab, position ->
            val albumCount = vm.totalCounts.value[MyAlbumViewModel.KEY_ALBUM_COUNT] ?: 0L
            val memoryCount = vm.totalCounts.value[MyAlbumViewModel.KEY_MEMORY_COUNT] ?: 0L
            val `0` = albumCount.toLocalizedString(requireContext())
            val `1` = memoryCount.toLocalizedString(requireContext())
            when (position) {
                0 -> tab.text = createTabText(requireContext().getString(R.string.album_tab), requireContext().getString(R.string.item_count, `0`))
                1 -> tab.text = createTabText(requireContext().getString(R.string.memory_tab), requireContext().getString(R.string.item_count, `1`))
            }
        }.attach()
    }

    private fun setupObservers() {
        observeSavedState()
        observeUserData()
        observeViewEvents()
    }

    private fun observeSavedState() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_PROFILE_UPLOAD_PROGRESS) { bundle ->
                if (bundle == null) return@consumeOnce
                bundle.getLong(RESULT_EVENT_REFRESH, 0L).takeIf { it > 0 }?.let {
                    vm.fetchData()
                }
            }
        }
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
        when(event) {
            is MyAlbumViewEvent.ShowMore -> {
                val bottomSheet = ProfileMoreBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, ProfileMoreBottomSheetFragment.TAG)
            }
        }
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
        val `0` = albumCount.toLocalizedString(requireContext())
        val `1` = memoryCount.toLocalizedString(requireContext())
        if (tabLayout.tabCount >= 2) {
            tabLayout.getTabAt(0)?.text = createTabText(requireContext().getString(R.string.album_tab), requireContext().getString(R.string.item_count, `0`))
            tabLayout.getTabAt(1)?.text = createTabText(requireContext().getString(R.string.memory_tab), requireContext().getString(R.string.item_count, `1`))
        }
    }

    companion object {
        const val KEY_PROFILE_UPLOAD_PROGRESS = "profile_upload_progress"
        const val RESULT_EVENT_REFRESH = "event_refresh"
    }
}