package com.tenacy.roadcapture.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.fragment.NavHostFragment
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.R

val AppCompatActivity.navHostFragment get() = supportFragmentManager.findFragmentById(R.id.container) as? NavHostFragment
val AppCompatActivity.navController get() = navHostFragment?.navController
val Fragment.mainActivity get() =  (requireActivity() as MainActivity)
val AppCompatActivity.currentFragment get() = supportFragmentManager.findFragmentById(R.id.container)
val AppCompatActivity.currentDestinationId get() = currentFragment?.let { NavHostFragment.findNavController(it).currentDestination?.id }

inline fun <reified T : ViewModel> FragmentActivity.getFragmentViewModel(
    fragmentId: Int,
    fragmentClass: Class<out Fragment>
): T? {
    // NavHostFragment 찾기
    val navHostFragment = supportFragmentManager.findFragmentById(fragmentId) as? NavHostFragment
        ?: return null

    // NavController 얻기
    val navController = navHostFragment.navController

    // 현재 네비게이션 그래프에서 활성화된 프래그먼트 찾기
    val currentFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
        ?: navHostFragment.childFragmentManager.fragments.firstOrNull()
        ?: return null

    // 특정 타입의 프래그먼트 찾기 (재귀적으로 검색)
    val targetFragment = findFragmentByClass(currentFragment, fragmentClass)
        ?: return null

    // 프래그먼트의 ViewModel 반환
    return ViewModelProvider(targetFragment as ViewModelStoreOwner)[T::class.java]
}

fun findFragmentByClass(fragment: Fragment, targetClass: Class<out Fragment>): Fragment? {
    // 현재 프래그먼트가 타겟 클래스인지 확인
    if (targetClass.isInstance(fragment)) {
        return fragment
    }

    // 자식 프래그먼트에서 검색
    for (childFragment in fragment.childFragmentManager.fragments) {
        val result = findFragmentByClass(childFragment, targetClass)
        if (result != null) {
            return result
        }
    }

    return null
}