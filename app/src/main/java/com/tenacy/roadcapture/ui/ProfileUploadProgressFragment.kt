package com.tenacy.roadcapture.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentUploadProgressBinding
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class ProfileUploadProgressFragment: BaseFragment() {

    private var _binding: FragmentUploadProgressBinding? = null
    val binding get() = _binding!!

    private val vm: ProfileUploadProgressViewModel by viewModels()

    private val progressUpdateFlow = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 1)
    private var progressAnimator: ObjectAnimator? = null

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                remove()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUploadProgressBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupObservers() {
        observeData()
        observeDebounceProgress()
    }

    private fun observeData() {
        repeatOnLifecycle {
            vm.saveState.collect {
                when (it) {
                    is ProfileSaveState.Loading -> {}
                    is ProfileSaveState.FetchingData -> {
                        binding.llUploadProgressContainer.visibility = View.VISIBLE
                        binding.txtUploadProgressStatus.text = "준비하는 중"
                    }
                    is ProfileSaveState.CompressingImage -> {}
                    is ProfileSaveState.UploadingImage -> {
                        binding.txtUploadProgressStatus.text = "이미지 업로드 중"
                        binding.pbUploadProgress.max = 1 * ANIMATION_SMOOTHNESS_FACTOR
                        progressUpdateFlow.emit(0 * ANIMATION_SMOOTHNESS_FACTOR)
                    }
                    is ProfileSaveState.SavingToFirestore -> {
                        progressUpdateFlow.emit(1 * ANIMATION_SMOOTHNESS_FACTOR)
                        binding.txtUploadProgressStatus.text = "프로필 사진을 저장하는 중"
                    }
                    is ProfileSaveState.Completed -> {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(
                                    "프로필 사진을 성공적으로 변경했어요",
                                    ToastMessageType.Success
                                )
                            )
                        )
                        findNavController().previousBackStackEntry?.savedStateHandle?.set(
                            MyAlbumFragment.KEY_PROFILE_UPLOAD_PROGRESS,
                            bundleOf(
                                MyAlbumFragment.RESULT_EVENT_REFRESH to System.currentTimeMillis()
                            )
                        )
                        findNavController().popBackStack(R.id.mainFragment, false)
                    }

                    is ProfileSaveState.Error -> {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(
                                    "작업 중 오류가 발생했어요\n다시 시도해주세요",
                                    ToastMessageType.Warning
                                )
                            )
                        )
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun observeDebounceProgress() {
        repeatOnLifecycle {
            progressUpdateFlow
                // 애니메이션 지속 시간과 동일한 디바운스 시간 설정
                .collect { newProgress ->
                    progressAnimator?.cancel()
                    progressAnimator = ObjectAnimator.ofInt(
                        binding.pbUploadProgress, "progress",
                        binding.pbUploadProgress.progress, newProgress
                    ).apply {
                        duration = ANIMATION_DURATION
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                }
        }
    }

    companion object {
        const val ANIMATION_DURATION = 500L
        const val ANIMATION_SMOOTHNESS_FACTOR = 1000
    }
}