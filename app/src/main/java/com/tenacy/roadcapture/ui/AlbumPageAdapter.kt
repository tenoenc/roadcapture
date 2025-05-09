package com.tenacy.roadcapture.ui

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class AlbumPagerAdapter(fragment: Fragment, val userId: String) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AlbumTabFragment.newInstance(
                bundle = bundleOf(
                    AlbumTabFragment.KEY_PARAMS to AlbumTabFragment.ParamsIn(
                        userId = userId
                    )
                )
            )
            1 -> MemoryTabFragment.newInstance(
                bundle = bundleOf(
                    MemoryTabFragment.KEY_PARAMS to MemoryTabFragment.ParamsIn(
                        userId = userId
                    )
                )
            )
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}