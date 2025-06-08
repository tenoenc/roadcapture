package com.tenacy.roadcapture.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.databinding.ViewDataBinding
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.databinding.ItemAlbumBinding
import com.tenacy.roadcapture.databinding.ItemMyAlbumBinding
import com.tenacy.roadcapture.databinding.ItemNativeAdBinding
import com.tenacy.roadcapture.databinding.ItemTagBinding
import com.tenacy.roadcapture.ui.dto.AlbumItemWithAds
import com.tenacy.roadcapture.util.*
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class AlbumWithAdsPagingAdapter(
    private var showAds: Boolean = true
) : PagingDataAdapter<AlbumItemWithAds, AlbumWithAdsViewHolder<AlbumItemWithAds>>(AlbumListItemComparator) {

    private var context: Context? = null
    private val adCache = ConcurrentHashMap<String, NativeAd>()
    private val loadingAds = mutableSetOf<String>()

    companion object {
        const val VIEW_TYPE_AD = 0
        const val VIEW_TYPE_GENERAL_ALBUM = 1
        const val VIEW_TYPE_USER_ALBUM = 2
        private const val TAG = "AlbumWithAdsPagingAdapter"

        object AlbumListItemComparator : DiffUtil.ItemCallback<AlbumItemWithAds>() {
            override fun areItemsTheSame(oldItem: AlbumItemWithAds, newItem: AlbumItemWithAds): Boolean {
                return when {
                    oldItem is AlbumItemWithAds.Album && newItem is AlbumItemWithAds.Album ->
                        oldItem.id == newItem.id
                    oldItem is AlbumItemWithAds.Ad && newItem is AlbumItemWithAds.Ad ->
                        oldItem.id == newItem.id
                    else -> false
                }
            }

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: AlbumItemWithAds, newItem: AlbumItemWithAds): Boolean {
                return when {
                    oldItem is AlbumItemWithAds.Album && newItem is AlbumItemWithAds.Album -> {
                        oldItem.value.user == newItem.value.user &&
                                oldItem.value.viewCount == newItem.value.viewCount &&
                                oldItem.value.title == newItem.value.title &&
                                oldItem.value.regionTags == newItem.value.regionTags &&
                                oldItem.value.isScraped == newItem.value.isScraped &&
                                oldItem.value.isPublic == newItem.value.isPublic &&
                                oldItem.value.shareId == newItem.value.shareId
                    }
                    oldItem is AlbumItemWithAds.Ad && newItem is AlbumItemWithAds.Ad -> true
                    else -> false
                }
            }

            override fun getChangePayload(oldItem: AlbumItemWithAds, newItem: AlbumItemWithAds): Any? {
                if (oldItem is AlbumItemWithAds.Album && newItem is AlbumItemWithAds.Album) {
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

                return null
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        context = null
        destroyAds()
    }

    // getItemViewType 함수 수정
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item is AlbumItemWithAds.Album.General -> VIEW_TYPE_GENERAL_ALBUM
            item is AlbumItemWithAds.Album.User -> VIEW_TYPE_USER_ALBUM
            // 구독자일 경우 광고를 앨범 아이템으로 표시
            item is AlbumItemWithAds.Ad && !showAds -> VIEW_TYPE_GENERAL_ALBUM
            item is AlbumItemWithAds.Ad -> VIEW_TYPE_AD
            else -> VIEW_TYPE_GENERAL_ALBUM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumWithAdsViewHolder<AlbumItemWithAds> {
        return when (viewType) {
            VIEW_TYPE_GENERAL_ALBUM -> {
                val binding = ItemAlbumBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AlbumWithAdsViewHolder.General(binding)
            }
            VIEW_TYPE_USER_ALBUM -> {
                val binding = ItemMyAlbumBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AlbumWithAdsViewHolder.User(binding)
            }
            VIEW_TYPE_AD -> {
                val binding = ItemNativeAdBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AdViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: AlbumWithAdsViewHolder<AlbumItemWithAds>, position: Int) {
        when (val item = getItem(position)) {
            is AlbumItemWithAds.Album -> {
                holder.bind(item)
            }
            is AlbumItemWithAds.Ad -> {
                if (holder is AdViewHolder) {
                    holder.bind(item)
                }
            }
            null -> {
                // Loading placeholder
            }
        }
    }

    override fun onBindViewHolder(
        holder: AlbumWithAdsViewHolder<AlbumItemWithAds>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val item = getItem(position)
            if (item != null) {
                holder.bind(item, payloads)
            }
        }
    }

    fun refreshVisibleItems() {
        notifyItemRangeChanged(0, itemCount, listOf("time"))
    }

    private fun loadAdForPosition(adItem: AlbumItemWithAds.Ad) {
        val id = adItem.id
        if (context == null || loadingAds.contains(id) || adCache.containsKey(id)) return

        loadingAds.add(id)

        val adUnitId = if (BuildConfig.DEBUG) {
            BuildConfig.AD_MOB_APP_UNIT_NATIVE_TEST_ID
        } else {
            BuildConfig.AD_MOB_APP_HOME_ALBUM_ID
        }

        AdLoader.Builder(context!!, adUnitId)
            .forNativeAd { nativeAd ->
                adCache[id] = nativeAd
                loadingAds.remove(id)

                // 해당 위치의 아이템 업데이트
                notifyItemChanged(snapshot().items.indexOf(adItem))
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Failed to load ad: ${loadAdError.message}")
                    loadingAds.remove(id)
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun destroyAds() {
        adCache.values.forEach { it.destroy() }
        adCache.clear()
        loadingAds.clear()
    }

    private inner class AdViewHolder(private val binding: ItemNativeAdBinding) : AlbumWithAdsViewHolder<AlbumItemWithAds.Ad>(binding) {

        private var currentAd: NativeAd? = null

        override fun bind(item: AlbumItemWithAds.Ad) {
            val cachedAd = adCache[item.id]
            if (cachedAd != null) {
                populateNativeAdView(cachedAd)
            } else {
                // 광고가 로드되지 않았으면 로드 시작
                binding.adContainer.alpha = 0.5f // 로딩 상태 표시
                loadAdForPosition(item)
            }
        }

        override fun bind(item: AlbumItemWithAds.Ad, payloads: List<Any>) {
        }

        fun unbind() {
            currentAd = null
        }

        private fun populateNativeAdView(nativeAd: NativeAd) {
            currentAd = nativeAd
            binding.adContainer.alpha = 1.0f

            // 필수 요소 설정
            binding.adHeadline.text = nativeAd.headline
            binding.nativeAdView.headlineView = binding.adHeadline

            binding.adBody.text = nativeAd.body
            binding.nativeAdView.bodyView = binding.adBody

            // 선택적 요소 설정
            nativeAd.callToAction?.let {
                binding.adCallToAction.text = it
                binding.nativeAdView.callToActionView = binding.adCallToAction
                binding.adCallToAction.visibility = View.VISIBLE
            } ?: run {
                binding.adCallToAction.visibility = View.INVISIBLE
            }

            nativeAd.icon?.let { icon ->
                binding.adIcon.showImage(
                    url = null,
                    bitmap = null,
                    uri = icon.uri,
                    src = null,
                    radius = 4,
                    borderWidth = null,
                    borderColor = null,
                    coverColor = null
                )
                binding.nativeAdView.iconView = binding.adIcon
                binding.adIcon.visibility = View.VISIBLE
            } ?: run {
                binding.adIcon.visibility = View.INVISIBLE
            }

            nativeAd.starRating?.let { rating ->
                binding.adStars.rating = rating.toFloat()
                binding.nativeAdView.starRatingView = binding.adStars
                binding.adStars.visibility = View.VISIBLE
            } ?: run {
                binding.adStars.visibility = View.INVISIBLE
            }

            nativeAd.mediaContent?.let { mediaContent ->
                binding.adMediaView.mediaContent = mediaContent
                binding.nativeAdView.mediaView = binding.adMediaView
                binding.adMediaView.visibility = View.VISIBLE
            } ?: run {
                binding.adMediaView.visibility = View.INVISIBLE
            }

            binding.nativeAdView.setNativeAd(nativeAd)
        }
    }
}

sealed class AlbumWithAdsViewHolder<out T: AlbumItemWithAds>(binding: ViewDataBinding): RecyclerView.ViewHolder(binding.root) {

    abstract fun bind(item: @UnsafeVariance T)
    abstract fun bind(item: @UnsafeVariance T, payloads: List<Any>)

    class General(private val binding: ItemAlbumBinding) : AlbumWithAdsViewHolder<AlbumItemWithAds.Album.General>(binding) {

        override fun bind(item: AlbumItemWithAds.Album.General) {
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

        override fun bind(item: AlbumItemWithAds.Album.General, payloads: List<Any>) {
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

    class User(private val binding: ItemMyAlbumBinding) : AlbumWithAdsViewHolder<AlbumItemWithAds.Album.User>(binding) {

        override fun bind(item: AlbumItemWithAds.Album.User) {
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

        override fun bind(item: AlbumItemWithAds.Album.User, payloads: List<Any>) {
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

    protected fun getNumericalText(album: AlbumItemWithAds.Album): String {
        val currentTimeStamp = LocalDateTime.now().toTimestamp()
        val (duration, durationUnit) = getFormattedDuration(album.value.endedAt.toTimestamp(), currentTimeStamp)
        val (viewCount, viewCountUnit) = album.value.viewCount.toLong().toReadableUnit()
        return StringBuilder().let { sb ->
            if(!album.value.isPublic) {
                sb.append("비공개 · ")
            }
            if(album.value.isScraped) {
                sb.append("스크랩됨 · ")
            }
            sb.append("조회수 ${viewCount.toFormattedDecimalText()}${viewCountUnit} · ${duration}${durationUnit} 전")
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