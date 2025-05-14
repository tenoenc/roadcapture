package com.tenacy.roadcapture.ui

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class AlbumPagerAdapter(fragment: Fragment, val userId: String) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MyAlbumTabFragment.newInstance(
                bundle = bundleOf(
                    MyAlbumTabFragment.KEY_PARAMS to MyAlbumTabFragment.ParamsIn(
                        userId = userId
                    )
                )
            )
            1 -> MyMemoryTabFragment.newInstance(
                bundle = bundleOf(
                    MyMemoryTabFragment.KEY_PARAMS to MyMemoryTabFragment.ParamsIn(
                        userId = userId
                    )
                )
            )
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}