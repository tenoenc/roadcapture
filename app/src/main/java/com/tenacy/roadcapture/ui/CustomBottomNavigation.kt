package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.BindingAdapter
import androidx.navigation.NavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.CustomBottomNavigationBinding
import com.tenacy.roadcapture.util.toPx

class CustomBottomNavigation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: CustomBottomNavigationBinding
    private var navController: NavController? = null
    private var onNewItemClickListener: (() -> Unit)? = null
    private var currentSelectedId: Int = R.id.homeFragment

    // 네비게이션 아이템 ID를 뷰 ID에 매핑
    private val navigationMap = mapOf(
        R.id.homeFragment to R.id.bn_home,
        R.id.searchFragment to R.id.bn_search,
        R.id.albumMarkedFragment to R.id.bn_album_marked,
        R.id.myAlbumFragment to R.id.bn_my_album
    )

    // 뷰 ID를 네비게이션 아이템 ID에 매핑
    private val viewMap = mapOf(
        R.id.bn_home to R.id.homeFragment,
        R.id.bn_search to R.id.searchFragment,
        R.id.bn_album_marked to R.id.albumMarkedFragment,
        R.id.bn_my_album to R.id.myAlbumFragment
    )

    init {
        val inflater = LayoutInflater.from(context)
        binding = CustomBottomNavigationBinding.inflate(inflater, this, true)

        // 클릭 리스너 설정
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // 홈 버튼 클릭 리스너
        binding.bnHome.setOnClickListener {
            navigateTo(R.id.homeFragment)
        }

        // 검색 버튼 클릭 리스너
        binding.bnSearch.setOnClickListener {
            navigateTo(R.id.searchFragment)
        }
        
        // 새 아이템 버튼 클릭 리스너
        binding.bnNew.setOnClickListener {
            onNewItemClickListener?.invoke()
        }
        
        // 북마크 버튼 클릭 리스너
        binding.bnAlbumMarked.setOnClickListener {
            navigateTo(R.id.albumMarkedFragment)
        }
        
        // 앨범 버튼 클릭 리스너
        binding.bnMyAlbum.setOnClickListener {
            navigateTo(R.id.myAlbumFragment)
        }
    }

    /**
     * Navigation Component와 연동하는 메서드
     */
    fun setupWithNavController(navController: NavController) {
        this.navController = navController

        // Navigation 목적지 변경 리스너 등록
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateSelection(destination.id)
        }

        // 초기 선택 상태 설정
        updateSelection(navController.currentDestination?.id ?: R.id.homeFragment)
    }

    /**
     * 새 아이템 버튼 클릭 리스너 설정
     */
    fun setOnNewItemClickListener(listener: (() -> Unit)?) {
        this.onNewItemClickListener = listener
    }

    /**
     * 특정 목적지로 네비게이션
     */
    private fun navigateTo(destinationId: Int) {
        if (currentSelectedId == destinationId) return

        navController?.let { controller ->
            // 현재 위치가 목적지와 다를 때만 이동
            if (controller.currentDestination?.id != destinationId) {
                controller.navigate(destinationId)
            }
        }
    }

    /**
     * 선택된 메뉴 아이템 업데이트
     */
    private fun updateSelection(destinationId: Int) {
        // 이전 선택 상태 초기화
        resetAllSelections()

        // 매핑된 뷰 ID 찾기
        navigationMap[destinationId]?.let { viewId ->
            when (viewId) {
                R.id.bn_home -> updateHomeSelection(true)
                R.id.bn_search -> updateSearchSelection(true)
                R.id.bn_album_marked -> updateAlbumMarkedSelection(true)
                R.id.bn_my_album -> updateAlbumSelection(true)
            }
        }

        currentSelectedId = destinationId
    }

    /**
     * 모든 선택 상태 초기화
     */
    private fun resetAllSelections() {
        updateHomeSelection(false)
        updateSearchSelection(false)
        updateAlbumMarkedSelection(false)
        updateAlbumSelection(false)
    }

    /**
     * 홈 메뉴 선택 상태 업데이트
     */
    private fun updateHomeSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_home_selected else R.drawable.ic_home

        binding.bnHome.findViewById<ImageView>(R.id.img_bn_home)?.setImageResource(iconResource)
    }

    /**
     * 검색 메뉴 선택 상태 업데이트
     */
    private fun updateSearchSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_search_selected else R.drawable.ic_search

        binding.bnSearch.findViewById<ImageView>(R.id.img_bn_search)?.setImageResource(iconResource)
    }

    /**
     * 북마크 메뉴 선택 상태 업데이트
     */
    private fun updateAlbumMarkedSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_book_marked_selected else R.drawable.ic_book_marked

        binding.bnAlbumMarked.findViewById<ImageView>(R.id.img_bn_album_marked)?.setImageResource(iconResource)
    }

    /**
     * 앨범 메뉴 선택 상태 업데이트
     */
    private fun updateAlbumSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_book_selected else R.drawable.ic_book

        binding.bnMyAlbum.findViewById<ImageView>(R.id.img_bn_my_album)?.setImageResource(iconResource)
    }

    /**
     * 프로그래매틱하게 아이템 선택
     */
    fun selectItem(destinationId: Int) {
        navigateTo(destinationId)
    }

    companion object {

        /**
         * onNewItemClick 바인딩 어댑터
         */
        @JvmStatic
        @BindingAdapter("onNewItemClick")
        fun setOnNewItemClick(view: CustomBottomNavigation, listener: (() -> Unit)?) {
            view.setOnNewItemClickListener(listener)
        }
    }
}