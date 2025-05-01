package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentNewAlbumBinding
import com.tenacy.roadcapture.di.AlbumTitleFilter
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class NewAlbumFragment: BaseFragment() {

    private var _binding: FragmentNewAlbumBinding? = null
    val binding get() = _binding!!

    private val vm: NewAlbumViewModel by viewModels()

    @Inject
    @AlbumTitleFilter
    lateinit var albumTitleFilter: LengthFilter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewAlbumBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setListeners()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            AlbumCompeteBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getBoolean(AlbumCompeteBeforeBottomSheetFragment.RESULT_PUBLIC).let {
                Log.d("TAG", "Positive Button Clicked!")
                vm.saveAlbum(it)
            }
        }
    }

    private fun setListeners() {
        binding.etNewAlbumTitle.apply {
            filters = arrayOf(albumTitleFilter)
            setOnFocusChangeListener { _, hasFocus ->
                vm.setAlbumTitleFocus(hasFocus)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val currentLength = s?.length ?: 0
                    vm.onAlbumTitleInputAttempt(currentLength)
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun setupObservers() {
        repeatOnLifecycle {
            vm.saveState.collectLatest {
                when(it) {
                    is AlbumSaveState.Loading -> {}
                    is AlbumSaveState.FetchingData -> {}
                    is AlbumSaveState.CreatingTags -> {}
                    is AlbumSaveState.ProcessingLocations -> {}
                    is AlbumSaveState.UploadingImages -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("${it.current} / ${it.total}", ToastMessageType.Info)))
                    }
                    is AlbumSaveState.ProcessingMemories -> {}
                    is AlbumSaveState.SavingToFirestore -> {}
                    is AlbumSaveState.ClearingLocalData -> {}
                    is AlbumSaveState.Completed -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("앨범을 성공적으로 생성했어요", ToastMessageType.Success)))
                        findNavController().popBackStack(R.id.mainFragment, false)
                    }
                    is AlbumSaveState.Error -> {}
                }
            }
        }
        observeViewEvents()
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? NewAlbumViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: NewAlbumViewEvent) {
        when (event) {
            is NewAlbumViewEvent.ShowCompleteBefore -> {
                val bottomSheet = AlbumCompeteBeforeBottomSheetFragment.newInstance()
                bottomSheet.show(childFragmentManager, AlbumCompeteBeforeBottomSheetFragment.TAG)
            }
        }
    }
}