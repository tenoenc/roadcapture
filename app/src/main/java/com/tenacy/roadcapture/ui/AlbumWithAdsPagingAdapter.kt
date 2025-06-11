package com.tenacy.roadcapture.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.databinding.ViewDataBinding
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
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
) : PagingDataAdapter<AlbumItemWithAds, AlbumWithAdsPagingAdapter.AlbumWithAdsViewHolder<AlbumItemWithAds>>(
    AlbumListItemComparator
) {

    private var context: Context? = null
    private val adCache = ConcurrentHashMap<String, NativeAd>()
    private val loadingAds = mutableSetOf<String>()
    private val mediaContentCache = ConcurrentHashMap<String, Any>()

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

    // 개선된 preloadAds 함수
    private fun preloadAds(startPosition: Int, count: Int) {
        val items = snapshot().items
        if (items.isNullOrEmpty()) {
            Log.w(TAG, "Items is null or empty, skipping preload")
            return
        }

        // startPosition이 유효한 범위인지 확인
        if (startPosition < 0 || startPosition >= items.size) {
            Log.w(TAG, "Invalid startPosition: $startPosition, items size: ${items.size}")
            return
        }

        for (i in startPosition until minOf(startPosition + count, items.size)) {
            val item = items.getOrNull(i) // 안전한 접근
            if (item is AlbumItemWithAds.Ad) {
                val id = item.id
                if (!adCache.containsKey(id) && !loadingAds.contains(id)) {
                    loadAdForPosition(item)
                }
            }
        }
    }

    // 개선된 onAttachedToRecyclerView 메소드
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context

        // 스크롤 리스너 추가
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                try {
                    recyclerView.layoutManager?.let { layoutManager ->
                        if (layoutManager is LinearLayoutManager) {
                            val firstVisible = layoutManager.findFirstVisibleItemPosition()
                            // firstVisible이 유효한 경우에만 preload 실행
                            if (firstVisible >= 0) {
                                preloadAds(firstVisible, 10)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scroll listener: ${e.message}")
                }
            }
        })
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

    // 개선된 onBindViewHolder
    override fun onBindViewHolder(holder: AlbumWithAdsViewHolder<AlbumItemWithAds>, position: Int) {
        try {
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
                    // Loading placeholder - 로그 추가
                    Log.d(TAG, "Item at position $position is null (loading)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding view holder at position $position: ${e.message}")
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

    // 안전한 notifyItemChanged 헬퍼 함수
    private fun notifyItemChangedSafely(adItem: AlbumItemWithAds.Ad) {
        try {
            val items = snapshot().items
            if (items.isNullOrEmpty()) {
                Log.w(TAG, "Cannot notify item changed: items is null or empty")
                return
            }

            val position = items.indexOf(adItem)
            if (position >= 0) {
                notifyItemChanged(position)
            } else {
                Log.w(TAG, "Ad item not found in current items list")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying item changed: ${e.message}")
        }
    }

    // 개선된 loadAdForPosition 메소드
    private fun loadAdForPosition(adItem: AlbumItemWithAds.Ad) {
        val id = adItem.id
        if (context == null || loadingAds.contains(id) || adCache.containsKey(id)) return

        loadingAds.add(id)

        // 광고 로드 시간 측정 시작
        val startTime = System.currentTimeMillis()

        val adUnitId = if (BuildConfig.DEBUG) {
            BuildConfig.AD_MOB_APP_UNIT_NATIVE_TEST_ID
        } else {
            BuildConfig.AD_MOB_APP_HOME_ALBUM_ID
        }

        // 광고 로드 타임아웃 추가 (5초)
        val loadTimeout = 5000L
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (loadingAds.contains(id)) {
                Log.w(TAG, "Ad load timeout for ID: $id")
                loadingAds.remove(id)
                // 타임아웃 발생 시 해당 위치 업데이트 (안전하게)
                notifyItemChangedSafely(adItem)
            }
        }

        // 타임아웃 설정
        timeoutHandler.postDelayed(timeoutRunnable, loadTimeout)

        val adLoader = AdLoader.Builder(context!!, adUnitId)
            .forNativeAd { nativeAd ->
                // 타임아웃 핸들러 제거
                timeoutHandler.removeCallbacks(timeoutRunnable)

                // 캐시에 광고 저장
                adCache[id] = nativeAd
                loadingAds.remove(id)

                // 로드 시간 로깅
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Ad loaded in $loadTime ms for position ${adItem.position}")

                // 해당 위치의 아이템 업데이트 (안전하게)
                notifyItemChangedSafely(adItem)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // 타임아웃 핸들러 제거
                    timeoutHandler.removeCallbacks(timeoutRunnable)

                    Log.e(TAG, "Failed to load ad: ${loadAdError.message}")
                    loadingAds.remove(id)
                }
            })
            .build()

        // 높은 우선순위로 광고 로드
        val adRequest = AdRequest.Builder().build()
        adLoader.loadAd(adRequest)
    }

    // AlbumWithAdsPagingAdapter.kt에 추가
    override fun onViewRecycled(holder: AlbumWithAdsViewHolder<AlbumItemWithAds>) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    // destroyAds 메소드 수정
    private fun destroyAds() {
        adCache.values.forEach { it.destroy() }
        adCache.clear()
        mediaContentCache.clear()
        loadingAds.clear()
    }

    private inner class AdViewHolder(private val binding: ItemNativeAdBinding) :
        AlbumWithAdsViewHolder<AlbumItemWithAds.Ad>(binding) {

        private var currentAd: NativeAd? = null
        private var isMediaViewPrepared = false

        // AdViewHolder의 bind 메소드 수정
        override fun bind(item: AlbumItemWithAds.Ad) {
            val cachedAd = adCache[item.id]
            if (cachedAd != null) {
                // 광고가 로드된 경우 부드럽게 표시
                if (currentAd != cachedAd || !isMediaViewPrepared) {
                    // 애니메이션 추가
                    binding.adContainer.alpha = 0.5f
                    binding.adContainer.animate()
                        .alpha(1.0f)
                        .setDuration(300)
                        .start()

                    populateNativeAdView(cachedAd)
                    isMediaViewPrepared = true
                }
            } else {
                // 광고가 로드되지 않은 경우 로딩 표시
                if (loadingAds.contains(item.id)) {
                    // 이미 로딩 중인 경우
                    binding.adContainer.alpha = 0.5f
                } else {
                    // 로딩 시작하는 경우
                    binding.adContainer.alpha = 0.3f
                    loadAdForPosition(item)
                }
            }
        }

        override fun bind(item: AlbumItemWithAds.Ad, payloads: List<Any>) {
            // 페이로드 처리는 필요 없음
        }

        override fun recycle() {
            try {
                // MediaView 비우기 전에 현재 표시 중인 컨텐츠를 null로 설정
                binding.adMediaView.setMediaContent(null)

                // NativeAdView에서 참조 제거
                binding.nativeAdView.mediaView = null
                binding.nativeAdView.headlineView = null
                binding.nativeAdView.bodyView = null
                binding.nativeAdView.callToActionView = null
                binding.nativeAdView.iconView = null
                binding.nativeAdView.starRatingView = null

                // 상태 초기화
                isMediaViewPrepared = false
                currentAd = null
            } catch (e: Exception) {
                Log.e(TAG, "Error during view recycling: ${e.message}")
            }
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

            // MediaView 설정 - 캐시된 키 사용
            val mediaContentKey = "media_${nativeAd.hashCode()}"

            nativeAd.mediaContent?.let { mediaContent ->
                try {
                    // MediaContent 캐싱
                    mediaContentCache[mediaContentKey] = mediaContent

                    // 현재 MediaView 상태 확인
                    if (!isMediaViewPrepared) {
                        binding.adMediaView.mediaContent = mediaContent
                        binding.nativeAdView.mediaView = binding.adMediaView
                        binding.adMediaView.visibility = View.VISIBLE
                        isMediaViewPrepared = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting MediaContent: ${e.message}")
                    binding.adMediaView.visibility = View.INVISIBLE
                    isMediaViewPrepared = false
                }
            } ?: run {
                // 캐시된 MediaContent 확인
                val cachedMediaContent = mediaContentCache[mediaContentKey]
                if (cachedMediaContent != null && !isMediaViewPrepared) {
                    try {
                        binding.adMediaView.mediaContent = cachedMediaContent as? com.google.android.gms.ads.MediaContent
                        binding.nativeAdView.mediaView = binding.adMediaView
                        binding.adMediaView.visibility = View.VISIBLE
                        isMediaViewPrepared = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting cached MediaContent: ${e.message}")
                        binding.adMediaView.visibility = View.INVISIBLE
                    }
                } else {
                    binding.adMediaView.visibility = View.INVISIBLE
                }
            }

            // 최종적으로 광고 뷰에 네이티브 광고 설정
            binding.nativeAdView.setNativeAd(nativeAd)
        }
    }

    sealed class AlbumWithAdsViewHolder<out T : AlbumItemWithAds>(private val binding: ViewDataBinding) :
        RecyclerView.ViewHolder(binding.root) {

        abstract fun bind(item: @UnsafeVariance T)
        abstract fun bind(item: @UnsafeVariance T, payloads: List<Any>)

        open fun recycle() {

        }

        class General(private val binding: ItemAlbumBinding) :
            AlbumWithAdsViewHolder<AlbumItemWithAds.Album.General>(binding) {

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

        class User(private val binding: ItemMyAlbumBinding) :
            AlbumWithAdsViewHolder<AlbumItemWithAds.Album.User>(binding) {

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
                        marginStart = 8.toPx
                    }
                }

                // 레이아웃에 뷰 추가
                linearLayout.addView(itemBinding.root, layoutParams)
            }
        }

        protected fun getNumericalText(album: AlbumItemWithAds.Album): String {
            val localizedTimeAgoText = album.value.endedAt.toUtcTimestamp().toLocalizedTimeAgo(binding.root.context)
            val localizedText = album.value.viewCount.toLocalizedString(binding.root.context).takeUnless { it == "0" } ?: binding.root.context.getString(R.string.none)
            return StringBuilder().let { sb ->
                if (!album.value.isPublic) {
                    sb.append(binding.root.context.getString(R.string.visibility_private))
                    sb.append(" · ")
                }
                if (album.value.isScraped) {
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
}