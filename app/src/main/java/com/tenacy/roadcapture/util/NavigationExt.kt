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

val AppCompatActivity.navHostFragment get() = supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
val AppCompatActivity.navController get() = navHostFragment.navController
val Fragment.mainActivity get() =  (requireActivity() as MainActivity)
val AppCompatActivity.currentFragment get() = supportFragmentManager.findFragmentById(R.id.container)
val AppCompatActivity.currentDestinationId get() = currentFragment?.let { NavHostFragment.findNavController(it).currentDestination?.id }

/**
 * 메인 액티비티에서 특정 프래그먼트의 ViewModel을 얻는 확장 함수
 *
 * @param fragmentId NavHost 프래그먼트의 ID (일반적으로 R.id.nav_host_fragment)
 * @param fragmentClass 타겟 프래그먼트의 클래스
 * @return T 타입의 ViewModel 또는 찾을 수 없는 경우 null
 */
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

/**
 * 특정 클래스의 프래그먼트를 재귀적으로 찾는 함수
 */
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