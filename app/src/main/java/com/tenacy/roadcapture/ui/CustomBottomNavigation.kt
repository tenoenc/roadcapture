package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.BindingAdapter
import androidx.navigation.NavController
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.CustomBottomNavigationBinding

class CustomBottomNavigation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: CustomBottomNavigationBinding
    private var navController: NavController? = null
    private var onNewItemClickListener: (() -> Unit)? = null
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var currentSelectedId: Int = R.id.homeFragment

    // 네비게이션 아이템 ID를 뷰 ID에 매핑
    private val navigationMap = mapOf(
        R.id.homeFragment to R.id.bn_home,
        R.id.scrapFragment to R.id.bn_scrap,
        R.id.myAlbumFragment to R.id.bn_my_album,
        R.id.appInfoFragment to R.id.bn_app_info,
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

        // 북마크 버튼 클릭 리스너
        binding.bnScrap.setOnClickListener {
            navigateTo(R.id.scrapFragment)
        }

        // 새 아이템 버튼 클릭 리스너
        binding.bnNew.setSafeClickListener {
            onNewItemClickListener?.invoke()
        }

        // 앨범 버튼 클릭 리스너
        binding.bnMyAlbum.setOnClickListener {
            navigateTo(R.id.myAlbumFragment)
        }

        // 검색 버튼 클릭 리스너
        binding.bnAppInfo.setOnClickListener {
            navigateTo(R.id.appInfoFragment)
        }
    }

    fun setupWithCustomNavigation(onTabSelected: (Int) -> Unit) {
        this.onTabSelectedListener = onTabSelected

        // 초기 선택 상태 설정
        updateSelection(R.id.homeFragment)
        onTabSelectedListener?.invoke(R.id.homeFragment)
    }

    fun setOnNewItemClickListener(listener: (() -> Unit)?) {
        this.onNewItemClickListener = listener
    }

    // 특정 목적지로 네비게이션
    private fun navigateTo(destinationId: Int) {
        if (currentSelectedId == destinationId) return

        // NavController 사용하는 경우
        navController?.let { controller ->
            // 현재 위치가 목적지와 다를 때만 이동
            if (controller.currentDestination?.id != destinationId) {
                controller.navigate(destinationId)
            }
            return
        }

        // 커스텀 네비게이션 사용하는 경우
        onTabSelectedListener?.invoke(destinationId)
        updateSelection(destinationId)
    }

    private fun updateSelection(destinationId: Int) {
        // 이전 선택 상태 초기화
        resetAllSelections()

        // 매핑된 뷰 ID 찾기
        navigationMap[destinationId]?.let { viewId ->
            when (viewId) {
                R.id.bn_home -> updateHomeSelection(true)
                R.id.bn_scrap -> updateScrapSelection(true)
                R.id.bn_my_album -> updateAlbumSelection(true)
                R.id.bn_app_info -> updateAppInfoSelection(true)
            }
        }

        currentSelectedId = destinationId
    }

    private fun resetAllSelections() {
        updateHomeSelection(false)
        updateScrapSelection(false)
        updateAlbumSelection(false)
        updateAppInfoSelection(false)
    }

    private fun updateHomeSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_home_selected else R.drawable.ic_home

        binding.bnHome.findViewById<ImageView>(R.id.img_bn_home)?.setImageResource(iconResource)
    }

    private fun updateScrapSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_book_marked_selected else R.drawable.ic_book_marked

        binding.bnScrap.findViewById<ImageView>(R.id.img_bn_scrap)?.setImageResource(iconResource)
    }

    private fun updateAlbumSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_book_selected else R.drawable.ic_book

        binding.bnMyAlbum.findViewById<ImageView>(R.id.img_bn_my_album)?.setImageResource(iconResource)
    }

    private fun updateAppInfoSelection(isSelected: Boolean) {
        val iconResource = if (isSelected) R.drawable.ic_circle_info_selected else R.drawable.ic_circle_info

        binding.bnAppInfo.findViewById<ImageView>(R.id.img_bn_app_info)?.setImageResource(iconResource)
    }

    fun selectItem(destinationId: Int) {
        navigateTo(destinationId)
    }

    companion object {

        @JvmStatic
        @BindingAdapter("onNewItemClick")
        fun setOnNewItemClick(view: CustomBottomNavigation, listener: (() -> Unit)?) {
            view.setOnNewItemClickListener(listener)
        }
    }
}