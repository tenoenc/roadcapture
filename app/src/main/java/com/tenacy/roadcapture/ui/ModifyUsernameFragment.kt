package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.tenacy.roadcapture.databinding.FragmentModifyUsernameBinding
import com.tenacy.roadcapture.di.UsernameFilter
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@AndroidEntryPoint
class ModifyUsernameFragment: BaseFragment() {

    private var _binding: FragmentModifyUsernameBinding? = null
    val binding get() = _binding!!

    private val vm: ModifyUsernameViewModel by viewModels()

    @Inject
    @UsernameFilter
    lateinit var usernameFilter: LengthFilter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentModifyUsernameBinding.inflate(inflater, container, false)

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

    private fun setListeners() {
        binding.etModifyUsernameValue.apply {
            filters = arrayOf(usernameFilter)
            setOnFocusChangeListener { _, hasFocus ->
                vm.setUsernameFocus(hasFocus)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val currentLength = s?.length ?: 0
                    vm.onUsernameInputAttempt(currentLength)
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
                    (event as? ModifyUsernameViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: ModifyUsernameViewEvent) {
        when (event) {
            is ModifyUsernameViewEvent.Complete -> {
                findNavController().navigate(ModifyUsernameFragmentDirections.actionModifyUsernameToUsernameSaving(event.username))
            }
        }
    }

    @Parcelize
    data class Arguments(
        val title: String,
        val isPublic: Boolean,
    ): Parcelable
}