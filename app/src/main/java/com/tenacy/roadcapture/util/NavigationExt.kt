package com.tenacy.roadcapture.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.tenacy.roadcapture.MainActivity
import com.tenacy.roadcapture.R

val AppCompatActivity.navHostFragment get() = supportFragmentManager.findFragmentById(R.id.container) as NavHostFragment
val AppCompatActivity.navController get() = navHostFragment.navController
val Fragment.mainActivity get() =  (requireActivity() as MainActivity)
val AppCompatActivity.currentFragment get() = supportFragmentManager.findFragmentById(R.id.container)
val AppCompatActivity.currentDestinationId get() = currentFragment?.let { NavHostFragment.findNavController(it).currentDestination?.id }