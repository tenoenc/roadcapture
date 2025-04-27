package com.tenacy.roadcapture.util

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.R

val MainActivity.navHostFragment get() = supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
val MainActivity.navController get() = navHostFragment.navController
val Fragment.mainActivity get() =  (requireActivity() as MainActivity)
val MainActivity.currentFragment get() = supportFragmentManager.findFragmentById(R.id.container)
val MainActivity.currentDestinationId get() = currentFragment?.let { NavHostFragment.findNavController(it).currentDestination?.id }