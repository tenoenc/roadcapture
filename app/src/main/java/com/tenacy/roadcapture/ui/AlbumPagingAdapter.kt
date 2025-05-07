package com.tenacy.roadcapture.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.data.firebase.dto.FirebaseAlbum
import com.tenacy.roadcapture.databinding.ItemAlbumBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.util.getFormattedDuration
import com.tenacy.roadcapture.util.toPx
import com.tenacy.roadcapture.util.toReadableUnitText
import com.tenacy.roadcapture.util.toTimestamp
import java.time.LocalDateTime

data class AlbumItem(
    val value: FirebaseAlbum,
    val onItemClick: () -> Unit,
    val onProfileClick: () -> Unit,
)

class AlbumPagingAdapter : PagingDataAdapter<AlbumItem, AlbumViewHolder>(AlbumComparator) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bind(item)
        }
    }

    companion object {
        object AlbumComparator : DiffUtil.ItemCallback<AlbumItem>() {
            override fun areItemsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
                return oldItem.value.id == newItem.value.id
            }

            override fun areContentsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
                Log.d("TAG", "oldItem.regionTags == newItem.regionTags : ${oldItem.value.regionTags == newItem.value.regionTags}")
                return oldItem.value.user == newItem.value.user &&
                        oldItem.value.viewCount == newItem.value.viewCount &&
                        oldItem.value.title == newItem.value.title &&
                        oldItem.value.regionTags == newItem.value.regionTags
            }
        }
    }
}

class AlbumViewHolder(private val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(album: AlbumItem) {
        val currentTimeStamp = LocalDateTime.now().toTimestamp()
        val (duration, durationUnit) = getFormattedDuration(album.value.endedAt.toTimestamp(), currentTimeStamp)
        val (viewCount, viewCountUnit) = album.value.viewCount.toLong().toReadableUnitText()

        binding.thumbnailUrl = album.value.thumbnailUrl
        binding.profileImageUrl = album.value.user.photoUrl
        binding.username = album.value.user.name
        binding.numericalText = "조회수 ${viewCount}${viewCountUnit} · ${duration}${durationUnit} 전"
        binding.title = album.value.title

        setItemsToLayout(extractUniqueLocations(album.value.regionTags))

        binding.root.setOnClickListener {
            album.onItemClick()
        }
        binding.clIAlbumRow1Profile.setOnClickListener {
            album.onProfileClick()
        }
    }

    private fun extractUniqueLocations(locations: List<Map<String, String>>): List<String> {
        val result = mutableListOf<String>()
        val seenValues = mutableSetOf<String>()

        // 먼저 등장한 순서대로 국가, 지역1, 지역2를 기록
        val orderedCountries = mutableListOf<String>()
        val orderedDepth1ByCountry = mutableMapOf<String, MutableList<String>>()
        val orderedDepth2ByDepth1 = mutableMapOf<Pair<String, String>, MutableList<String>>()

        // 데이터 구조화 및 순서 기록
        locations.forEach { location ->
            val country = location["country"] ?: return@forEach
            val depth1 = location["depth1"] ?: return@forEach
            val depth2 = location["depth2"] ?: return@forEach

            // 국가 순서 기록
            if (country !in orderedCountries) {
                orderedCountries.add(country)
            }

            // 지역1 순서 기록
            if (!orderedDepth1ByCountry.containsKey(country)) {
                orderedDepth1ByCountry[country] = mutableListOf()
            }
            if (depth1 !in orderedDepth1ByCountry[country]!!) {
                orderedDepth1ByCountry[country]!!.add(depth1)
            }

            // 지역2 순서 기록
            val key = Pair(country, depth1)
            if (!orderedDepth2ByDepth1.containsKey(key)) {
                orderedDepth2ByDepth1[key] = mutableListOf()
            }
            if (depth2 !in orderedDepth2ByDepth1[key]!!) {
                orderedDepth2ByDepth1[key]!!.add(depth2)
            }
        }

        // 순서대로 결과 생성
        orderedCountries.forEach { country ->
            // 국가 추가
            if (country !in seenValues) {
                result.add(country)
                seenValues.add(country)
            }

            // 해당 국가의 지역1 추가 (순서 유지)
            orderedDepth1ByCountry[country]?.forEach { depth1 ->
                if (depth1 !in seenValues) {
                    result.add(depth1)
                    seenValues.add(depth1)
                }

                // 해당 지역1의 지역2 추가 (순서 유지)
                val key = Pair(country, depth1)
                orderedDepth2ByDepth1[key]?.forEach { depth2 ->
                    if (depth2 !in seenValues) {
                        result.add(depth2)
                        seenValues.add(depth2)
                    }
                }
            }
        }

        return result
    }

    private fun setItemsToLayout(items: List<String>) {
        val linearLayout = binding.llIAlbumTags
        linearLayout.removeAllViews()

        // 인플레이터 준비
        val inflater = LayoutInflater.from(binding.root.context)

        // 각 문자열에 대해 반복
        items.forEachIndexed { index, item ->
            // 항목 뷰바인딩 인플레이트
            val itemBinding = ItemTagBinding.inflate(inflater, linearLayout, false)

            // 텍스트 설정
            itemBinding.name = item

            // 레이아웃 파라미터 설정 (8dp 마진 추가)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    setMargins(8f.toPx, 0, 0, 0)
                }
            }

            // 레이아웃에 뷰 추가
            linearLayout.addView(itemBinding.root, layoutParams)
        }
    }
}