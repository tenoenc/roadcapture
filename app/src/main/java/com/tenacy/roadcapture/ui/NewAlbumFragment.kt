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
import com.tenacy.roadcapture.databinding.FragmentNewAlbumBinding
import com.tenacy.roadcapture.di.AlbumTitleFilter
import com.tenacy.roadcapture.util.handleCommonSystemViewEvents
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
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
            bundle.getParcelable<AlbumCompeteBeforeBottomSheetFragment.ParamsOut.Public>(AlbumCompeteBeforeBottomSheetFragment.KEY_PARAMS_OUT_PUBLIC).let {
                Log.d("TAG", "Positive Button Clicked!")
                val args = it ?: return@setFragmentResultListener
                val title = vm.albumTitle.value
                findNavController().navigate(NewAlbumFragmentDirections.actionNewAlbumToUploadProgress(title, args.isPublic))
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
        observeViewEvents()
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    handleViewEvents(event)
                }
            }
        }
    }

    private fun handleViewEvents(event: ViewEvent) {
        // [VALIDATE_SYSTEM_CONFIG]
        if(event is CommonSystemViewEvent) {
            handleCommonSystemViewEvents(event)
            return
        }

        if(event is NewAlbumViewEvent) {
            when (event) {
                is NewAlbumViewEvent.ShowCompleteBefore -> {
                    val bottomSheet = AlbumCompeteBeforeBottomSheetFragment.newInstance()
                    bottomSheet.show(childFragmentManager, AlbumCompeteBeforeBottomSheetFragment.TAG)
                }
            }
        }
    }
}