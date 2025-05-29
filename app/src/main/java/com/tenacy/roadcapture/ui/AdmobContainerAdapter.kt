package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.databinding.ItemNativeAdBinding
import com.tenacy.roadcapture.util.toPx
import java.util.concurrent.ConcurrentHashMap

class AdmobContainerAdapter(
    private val originalAdapter: AlbumPagingAdapter,
    private val adPosition: Int = 5,
    private val adInterval: Int = 10
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val adCache = ConcurrentHashMap<Int, NativeAd>()
    private val loadingAds = mutableSetOf<Int>()

    private lateinit var context: Context
    private var recyclerView: RecyclerView? = null

    companion object {
        const val VIEW_TYPE_AD = 100
        const val VIEW_TYPE_CONTENT = 101
        private const val TAG = "AdmobContainerAdapter"
    }

    init {
        setHasStableIds(true)
        registerObserver()
    }

    private fun registerObserver() {
        originalAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                val adjustedPosition = getAdjustedPositionForAd(positionStart)
                val adjustedCount = getAdjustedRangeCount(positionStart, itemCount)

                if (payload != null) {
                    notifyItemRangeChanged(adjustedPosition, adjustedCount, payload)
                } else {
                    notifyItemRangeChanged(adjustedPosition, adjustedCount)
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val adjustedPosition = getAdjustedPositionForAd(positionStart)
                val adjustedCount = getAdjustedRangeCount(positionStart, itemCount)
                notifyItemRangeInserted(adjustedPosition, adjustedCount)
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                val adjustedPosition = getAdjustedPositionForAd(positionStart)
                val adjustedCount = getAdjustedRangeCount(positionStart, itemCount)
                notifyItemRangeRemoved(adjustedPosition, adjustedCount)
            }
        })
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        this.context = recyclerView.context

        recyclerView.apply {
            addOnScrollListener(scrollListener)
            recycledViewPool.setMaxRecycledViews(VIEW_TYPE_AD, 5)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerView.removeOnScrollListener(scrollListener)
        this.recyclerView = null
        destroy()
    }

    override fun getItemId(position: Int): Long {
        return if (isAdPosition(position)) {
            "ad_$position".hashCode().toLong()
        } else {
            val originalPosition = getOriginalPosition(position)
            originalAdapter.getItemId(originalPosition)
        }
    }

    override fun getItemCount(): Int {
        return getCountWithAds(originalAdapter.itemCount)
    }

    override fun getItemViewType(position: Int): Int {
        return if (isAdPosition(position)) {
            VIEW_TYPE_AD
        } else {
            VIEW_TYPE_CONTENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (!::context.isInitialized) {
            context = parent.context
        }

        return when (viewType) {
            VIEW_TYPE_AD -> {
                val binding = ItemNativeAdBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AdViewHolder(binding)
            }
            else -> {
                ContentViewHolder(FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AdViewHolder -> holder.bind(position)
            is ContentViewHolder -> bindContentViewHolder(holder, position)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindContentViewHolder(holder: ContentViewHolder, position: Int) {
        try {
            val originalPosition = getOriginalPosition(position)
            val container = holder.itemView as FrameLayout

            val existingViewHolder = container.getChildAt(0)?.tag as? AlbumViewHolder<AlbumItem>

            if (existingViewHolder != null) {
                originalAdapter.onBindViewHolder(existingViewHolder, originalPosition)
            } else {
                container.removeAllViews()

                val viewType = originalAdapter.getItemViewType(originalPosition)
                val newViewHolder = originalAdapter.createViewHolder(container, viewType) as AlbumViewHolder<AlbumItem>

                container.addView(newViewHolder.itemView)
                newViewHolder.itemView.tag = newViewHolder

                originalAdapter.onBindViewHolder(newViewHolder, originalPosition)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding content view", e)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is AdViewHolder -> holder.unbind()
            is ContentViewHolder -> {
                val container = holder.itemView as? FrameLayout
                container?.removeAllViews()
            }
        }
    }

    private fun isAdPosition(position: Int): Boolean {
        if (position == 0) return false
        return position >= adPosition && (position - adPosition) % (adInterval + 1) == 0
    }

    private fun getOriginalPosition(position: Int): Int {
        if (position < adPosition) return position

        val adCount = (position - adPosition + adInterval) / (adInterval + 1)
        return position - adCount
    }

    private fun getAdjustedPositionForAd(originalPosition: Int): Int {
        if (originalPosition < adPosition) return originalPosition

        val adCount = (originalPosition - adPosition + adInterval) / adInterval
        return originalPosition + adCount
    }

    private fun getAdjustedRangeCount(startPosition: Int, itemCount: Int): Int {
        val endPosition = startPosition + itemCount - 1
        val adjustedStart = getAdjustedPositionForAd(startPosition)
        val adjustedEnd = getAdjustedPositionForAd(endPosition)
        return adjustedEnd - adjustedStart + 1
    }

    private fun getCountWithAds(originalCount: Int): Int {
        if (originalCount <= adPosition) return originalCount

        val adCount = (originalCount - adPosition + adInterval - 1) / adInterval
        return originalCount + adCount
    }

    // 심플한 스크롤 리스너
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            layoutManager?.let { lm ->
                val lastVisiblePosition = lm.findLastVisibleItemPosition()
                val firstVisiblePosition = lm.findFirstVisibleItemPosition()

                // 다음 2개의 광고만 미리 로드
                for (i in firstVisiblePosition..lastVisiblePosition + 2) {
                    if (isAdPosition(i) && !adCache.containsKey(i) && !loadingAds.contains(i)) {
                        loadAdForPosition(i)
                    }
                }
            }
        }
    }

    private fun loadAdForPosition(position: Int) {
        if (!::context.isInitialized || loadingAds.contains(position)) return

        loadingAds.add(position)

        val adUnitId = if (BuildConfig.DEBUG) {
            BuildConfig.AD_MOB_APP_UNIT_NATIVE_TEST_ID
        } else {
            BuildConfig.AD_MOB_APP_HOME_ALBUM_TEST_ID
        }

        AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                adCache[position] = nativeAd
                loadingAds.remove(position)

                // 지연 없이 바로 업데이트
                notifyItemChanged(position)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Failed to load ad: ${loadAdError.message}")
                    loadingAds.remove(position)
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun withLoadStateAdapter(loadStateAdapter: LoadStateAdapter): RecyclerView.Adapter<*> {
        originalAdapter.addLoadStateListener { loadStates ->
            loadStateAdapter.loadState = loadStates.append
        }

        val config = ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .build()

        return ConcatAdapter(config, this, loadStateAdapter)
    }

    fun destroy() {
        adCache.values.forEach { it.destroy() }
        adCache.clear()
        loadingAds.clear()
    }

    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class AdViewHolder(
        private val binding: ItemNativeAdBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentAd: NativeAd? = null

        init {
            // 아이템 루트에 고정 높이 설정
            /*binding.root.layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                356.toPx  // 고정 높이
            ).apply {
                setMargins(20.toPx, 0, 20.toPx, 0)
            }*/
        }

        fun bind(position: Int) {
            val cachedAd = adCache[position]
            if (cachedAd != null) {
                populateNativeAdView(cachedAd)
            } else {
//                showPlaceholder()
                if (!loadingAds.contains(position)) {
                    loadAdForPosition(position)
                }
            }
        }

        /*private fun showPlaceholder() {
            binding.adContainer.visibility = View.VISIBLE
            binding.adContainer.minimumHeight = 356.toPx  // 동일한 높이
            binding.adContainer.alpha = 0.2f
        }*/

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