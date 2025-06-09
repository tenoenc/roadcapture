package com.tenacy.roadcapture.ui

import android.annotation.SuppressLint
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.databinding.ViewDataBinding
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.ItemAlbumBinding
import com.tenacy.roadcapture.databinding.ItemMyAlbumBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.ui.dto.Album
import com.tenacy.roadcapture.util.*
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@Parcelize
sealed class AlbumItem(open val value: Album): Parcelable {

    @Parcelize
    data class General(
        override val value: Album,
        val onItemClick: () -> Unit,
        val onProfileClick: () -> Unit,
        val onLongClick: (String) -> Unit,
    ) : AlbumItem(value)

    @Parcelize
    data class User(
        override val value: Album,
        val onItemClick: () -> Unit,
        val onMoreClick: (Album) -> Unit,
    ) : AlbumItem(value)
}


class AlbumPagingAdapter : PagingDataAdapter<AlbumItem, AlbumViewHolder<AlbumItem>>(AlbumComparator) {

    private var recyclerView: RecyclerView? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder<AlbumItem> {
        return when(viewType) {
            VIEW_TYPE_GENERAL -> AlbumViewHolder.General(ItemAlbumBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            ))
            VIEW_TYPE_USER -> AlbumViewHolder.User(ItemMyAlbumBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            ))
            else -> throw IllegalStateException(parent.context.getString(R.string.view_type_not_exist))
        }
    }

    override fun onBindViewHolder(holder: AlbumViewHolder<AlbumItem>, position: Int) {
        val item = getItem(position) ?: return

        when {
            holder is AlbumViewHolder.General && item is AlbumItem.General -> {
                holder.bind(item)
            }
            holder is AlbumViewHolder.User && item is AlbumItem.User -> {
                holder.bind(item)
            }
        }
    }

    override fun onBindViewHolder(holder: AlbumViewHolder<AlbumItem>, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            if (item != null) {
                holder.bind(item, payloads)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when(getItem(position)) {
            is AlbumItem.General -> VIEW_TYPE_GENERAL
            is AlbumItem.User -> VIEW_TYPE_USER
            else -> VIEW_TYPE_GENERAL
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this@AlbumPagingAdapter.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this@AlbumPagingAdapter.recyclerView = null
    }

    fun refreshVisibleItems() {
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager
        val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: return
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        for (position in firstVisible..lastVisible) {
            notifyItemChanged(position, listOf("time"))
        }
    }

    companion object {

        const val VIEW_TYPE_GENERAL = 0
        const val VIEW_TYPE_USER = 1

        object AlbumComparator : DiffUtil.ItemCallback<AlbumItem>() {
            override fun areItemsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
                return oldItem.value.id == newItem.value.id
            }

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
//                return oldItem == newItem
                return oldItem.value.user == newItem.value.user &&
                        oldItem.value.viewCount == newItem.value.viewCount &&
                        oldItem.value.title == newItem.value.title &&
                        oldItem.value.regionTags == newItem.value.regionTags &&
                        oldItem.value.isScraped == newItem.value.isScraped &&
                        oldItem.value.isPublic == newItem.value.isPublic &&
                        oldItem.value.shareId == newItem.value.shareId
            }

            override fun getChangePayload(oldItem: AlbumItem, newItem: AlbumItem): Any? {
                val payload = mutableListOf<String>()

                if (oldItem.value.thumbnailUrl != newItem.value.thumbnailUrl) {
                    payload.add("thumbnailUrl")
                }

                if (oldItem.value.user.photoUrl != newItem.value.user.photoUrl) {
                    payload.add("userPhotoUrl")
                }

                if (oldItem.value.user.displayName != newItem.value.user.displayName) {
                    payload.add("userDisplayName")
                }

                if (oldItem.value.title != newItem.value.title) {
                    payload.add("title")
                }

                if (oldItem.value.regionTags != newItem.value.regionTags) {
                    payload.add("regionTags")
                }

                if (oldItem.value.isScraped != newItem.value.isScraped) {
                    payload.add("scraped")
                }

                if (oldItem.value.isPublic != newItem.value.isPublic) {
                    payload.add("isPublic")
                }

                if (oldItem.value.shareId != newItem.value.shareId) {
                    payload.add("shareId")
                }

                // 시간은 항상 변경되므로 페이로드에 포함
                payload.add("time")

                return if (payload.isEmpty()) null else payload
            }
        }
    }
}

sealed class AlbumViewHolder<out T: AlbumItem>(private val binding: ViewDataBinding): RecyclerView.ViewHolder(binding.root) {

    abstract fun bind(item: @UnsafeVariance T)
    abstract fun bind(item: @UnsafeVariance T, payloads: List<Any>)

    class General(private val binding: ItemAlbumBinding) : AlbumViewHolder<AlbumItem.General>(binding) {

        override fun bind(item: AlbumItem.General) {
            binding.thumbnailUrl = item.value.thumbnailUrl
            binding.profileImageUrl = item.value.user.photoUrl
            binding.username = item.value.user.displayName
            binding.title = item.value.title
            binding.numericalText = getNumericalText(item)

            binding.llIAlbumTags.setItemsToLayout(extractUniqueLocations(item.value.regionTags))

            binding.clIAlbumTouchContainer.setOnLongClickListener {
                item.onLongClick(item.value.id)
                true
            }
            binding.clIAlbumTouchContainer.setSafeClickListener {
                item.onItemClick()
            }
            binding.clIAlbumRow1Profile.setSafeClickListener {
                item.onProfileClick()
            }
        }

        override fun bind(item: AlbumItem.General, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                bind(item)
                return
            }

            payloads.forEach { payload ->
                if (payload is List<*>) {
                    payload.forEach { change ->
                        when (change) {
                            "thumbnailUrl" -> {
                                binding.thumbnailUrl = item.value.thumbnailUrl
                            }

                            "userPhotoUrl" -> {
                                binding.profileImageUrl = item.value.user.photoUrl
                            }

                            "userDisplayName" -> {
                                binding.username = item.value.user.displayName
                            }

                            "title" -> {
                                binding.title = item.value.title
                            }

                            "regionTags" -> {
                                binding.llIAlbumTags.setItemsToLayout(extractUniqueLocations(item.value.regionTags))
                            }

                            "time", "scraped" -> {
                                binding.numericalText = getNumericalText(item)
                            }
                        }
                    }
                }
            }
        }
    }

    class User(private val binding: ItemMyAlbumBinding) : AlbumViewHolder<AlbumItem.User>(binding) {

        override fun bind(item: AlbumItem.User) {
            binding.thumbnailUrl = item.value.thumbnailUrl
            binding.title = item.value.title
            binding.numericalText = getNumericalText(item)

            binding.llIMyAlbumTags.setItemsToLayout(extractUniqueLocations(item.value.regionTags))

            binding.cardIMyAlbumTouchContainer.setSafeClickListener {
                item.onItemClick()
            }
            binding.ibtnIMyAlbumMore.setSafeClickListener {
                item.onMoreClick(item.value)
            }
        }

        override fun bind(item: AlbumItem.User, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                bind(item)
                return
            }

            payloads.forEach { payload ->
                if (payload is List<*>) {
                    payload.forEach { change ->
                        when (change) {
                            "thumbnailUrl" -> {
                                binding.thumbnailUrl = item.value.thumbnailUrl
                            }

                            "title" -> {
                                binding.title = item.value.title
                            }

                            "regionTags" -> {
                                binding.llIMyAlbumTags.setItemsToLayout(extractUniqueLocations(item.value.regionTags))
                            }

                            "isPublic" -> {
                                binding.ibtnIMyAlbumMore.setSafeClickListener {
                                    item.onMoreClick(item.value)
                                    binding.numericalText = getNumericalText(item)
                                }
                            }
                            "shareId" -> {
                                binding.ibtnIMyAlbumMore.setSafeClickListener {
                                    item.onMoreClick(item.value)
                                }
                            }

                            "time", "scraped" -> {
                                binding.numericalText = getNumericalText(item)
                            }
                        }
                    }
                }
            }
        }
    }

    protected fun LinearLayout.setItemsToLayout(items: List<String>) {
        val linearLayout = this
        linearLayout.removeAllViews()

        // 인플레이터 준비
        val inflater = LayoutInflater.from(context)

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

    protected fun getNumericalText(album: AlbumItem): String {
        val localizedTimeAgoText = album.value.endedAt.toUtcTimestamp().toLocalizedTimeAgo(binding.root.context)
        val localizedText = album.value.viewCount.toLocalizedString(binding.root.context).takeUnless { it == "0" } ?: "없음"
        return StringBuilder().let { sb ->
            if(!album.value.isPublic) {
                sb.append(binding.root.context.getString(R.string.visibility_private))
                sb.append(" · ")
            }
            if(album.value.isScraped) {
                sb.append(binding.root.context.getString(R.string.scrap_status))
                sb.append(" · ")
            }
            val `0` = localizedText
            sb.append(binding.root.context.getString(R.string.view_count, `0`))
            sb.append(" · ")
            sb.append(localizedTimeAgoText)
            sb.toString()
        }
    }

    protected fun extractUniqueLocations(locations: List<Map<String, String>>): List<String> {
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
}