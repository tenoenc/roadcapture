package com.tenacy.roadcapture.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import com.tenacy.roadcapture.databinding.FragmentNewMemoryBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.di.ContentFilter
import com.tenacy.roadcapture.util.launchOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NewMemoryFragment: BaseFragment() {

    private var _binding: FragmentNewMemoryBinding? = null
    val binding get() = _binding!!

    private val vm: NewMemoryViewModel by viewModels()

    @Inject
    @ContentFilter
    lateinit var contentFilter: LengthFilter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewMemoryBinding.inflate(inflater, container, false)

        binding.vm = vm
        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setListeners()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        addItemsToLayout(vm.tags)
    }

    private fun setupObservers() {
        observeViewEvents()
    }

    private fun setListeners() {
        binding.etNewMemoryContent.apply {
            filters = arrayOf(contentFilter)
            setOnFocusChangeListener { _, hasFocus ->
                vm.setContentFocus(hasFocus)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val currentLength = s?.length ?: 0
                    vm.onContentInputAttempt(currentLength)
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun observeViewEvents() {
        launchOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? NewMemoryViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: NewMemoryViewEvent) {
//        when (event) {
//        }
    }

    private fun addItemsToLayout(items: List<String>) {
        val linearLayout = binding.llNewMemoryTags
        // 인플레이터 준비
        val inflater = LayoutInflater.from(requireContext())

        // 각 문자열에 대해 반복
        for (item in items) {
            // 항목 뷰바인딩 인플레이트
            val itemBinding = ItemTagBinding.inflate(inflater, linearLayout, false)

            // 텍스트 설정
            itemBinding.name = item

            // 레이아웃 파라미터 설정 (8dp 마진 추가)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8f.toPx, 0, 0, 0)
            }

            // 레이아웃에 뷰 추가
            linearLayout.addView(itemBinding.root, layoutParams)
        }
    }
}