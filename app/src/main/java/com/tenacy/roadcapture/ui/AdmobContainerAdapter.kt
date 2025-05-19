package com.tenacy.roadcapture.ui

import android.content.Context
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

class AdmobContainerAdapter(
    private val originalAdapter: AlbumPagingAdapter,
    private val adPosition: Int = 5, // 광고 첫 삽입 위치 (5번째 아이템 다음)
    private val adInterval: Int = 10 // 광고 삽입 간격 (10개 아이템마다)
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 광고 캐시 맵 추가 (위치 -> 광고)
    private val adCache = mutableMapOf<Int, NativeAd>()

    // 컨텍스트 참조
    private lateinit var context: Context

    companion object {
        const val VIEW_TYPE_AD = 100
        const val VIEW_TYPE_CONTENT = 101
    }

    init {
        // 원본 어댑터의 이벤트를 감지
        originalAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                // 실제 변경된 위치를 광고가 포함된 위치로 계산
                val newPositionStart = getAdjustedPositionForAd(positionStart)
                val newItemCount = getCountWithAds(positionStart + itemCount) - getCountWithAds(positionStart)
                notifyItemRangeChanged(newPositionStart, newItemCount)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                // 새 아이템이 삽입된 경우, 광고 위치 고려하여 notify
                val newPositionStart = getAdjustedPositionForAd(positionStart)
                val newItemCount = getCountWithAds(positionStart + itemCount) - getCountWithAds(positionStart)
                notifyItemRangeInserted(newPositionStart, newItemCount)
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                // 아이템이 제거된 경우, 광고 위치 고려하여 notify
                val newPositionStart = getAdjustedPositionForAd(positionStart)
                val newItemCount = getCountWithAds(positionStart + itemCount) - getCountWithAds(positionStart)
                notifyItemRangeRemoved(newPositionStart, newItemCount)
            }
        })
    }

    override fun getItemCount(): Int {
        // 원본 데이터 + 광고 개수
        return getCountWithAds(originalAdapter.itemCount)
    }

    override fun getItemViewType(position: Int): Int {
        // 광고 위치인지 확인
        return if (isAdPosition(position)) {
            VIEW_TYPE_AD
        } else {
            VIEW_TYPE_CONTENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // 컨텍스트 저장
        context = parent.context

        return when (viewType) {
            VIEW_TYPE_AD -> {
                // 네이티브 광고용 뷰홀더 생성
                val inflater = LayoutInflater.from(parent.context)
                val binding = ItemNativeAdBinding.inflate(inflater, parent, false)
                AdViewHolder(binding)
            }
            else -> {
                // 콘텐츠용 프록시 뷰홀더 생성 - FrameLayout 사용
                val containerView = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                ContentViewHolder(containerView)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AdViewHolder -> {
                holder.bind(position)
            }
            is ContentViewHolder -> {
                try {
                    // 원본 데이터의 실제 위치 계산
                    val originalPosition = getOriginalPosition(position)

                    // 홀더의 아이템 뷰가 FrameLayout인지 확인
                    val container = holder.itemView as? FrameLayout
                    if (container != null) {
                        // 기존 자식 뷰가 있는지 확인
                        if (container.childCount > 0) {
                            // 첫 번째 자식이 있으면 해당 뷰를 사용
                            val existingChildView = container.getChildAt(0)
                            val existingVh = existingChildView.tag as? AlbumViewHolder<AlbumItem>

                            if (existingVh != null) {
                                // 기존 뷰홀더가 있으면 재활용
                                originalAdapter.onBindViewHolder(existingVh, originalPosition)
                                return
                            } else {
                                // 자식이 있지만 뷰홀더가 없으면 자식 제거
                                container.removeAllViews()
                            }
                        }

                        // 새 뷰홀더 생성
                        val viewType = originalAdapter.getItemViewType(originalPosition)
                        val vh = originalAdapter.createViewHolder(
                            container,
                            viewType
                        ) as AlbumViewHolder<AlbumItem>

                        // 뷰홀더의 아이템 뷰를 컨테이너에 추가
                        container.addView(vh.itemView)

                        // 뷰홀더를 태그로 저장
                        vh.itemView.tag = vh

                        // 데이터 바인딩
                        originalAdapter.onBindViewHolder(vh, originalPosition)
                    }
                } catch (e: Exception) {
                    // 예외 처리 - 로그 출력
                    android.util.Log.e("AdmobContainerAdapter", "Error binding view: ${e.message}", e)
                }
            }
        }
    }

    // 광고 위치인지 확인
    private fun isAdPosition(position: Int): Boolean {
        // 첫 광고 위치 이후부터, 지정된 간격으로 광고 삽입
        if (position == 0) return false // 첫 위치는 광고 없음
        return position >= adPosition && (position - adPosition) % (adInterval + 1) == 0
    }

    // 광고를 고려한 원본 위치 계산
    private fun getOriginalPosition(position: Int): Int {
        if (position < adPosition) return position

        // 현재 위치까지 있는 광고 수 계산
        val adCount = (position - adPosition + adInterval) / (adInterval + 1)
        return position - adCount
    }

    // 광고 위치를 고려한 실제 표시 위치 계산
    private fun getAdjustedPositionForAd(originalPosition: Int): Int {
        if (originalPosition < adPosition) return originalPosition

        // 원본 위치 이전까지의 광고 수 계산
        val adCount = (originalPosition - adPosition + adInterval) / adInterval
        return originalPosition + adCount
    }

    // 전체 아이템 수 (원본 + 광고)
    private fun getCountWithAds(originalCount: Int): Int {
        if (originalCount <= adPosition) return originalCount

        // 원본 개수에 따른 광고 개수 계산
        val adCount = (originalCount - adPosition + adInterval) / adInterval
        return originalCount + adCount
    }

    // 미리 광고 로드 메서드 추가
    fun preloadAds(count: Int = 3) {
        // 현재 표시될 광고 위치들 계산
        val currentItemCount = originalAdapter.itemCount
        val positions = mutableListOf<Int>()

        for (i in adPosition until getCountWithAds(currentItemCount)) {
            if (isAdPosition(i)) {
                positions.add(i)
                if (positions.size >= count) break
            }
        }

        // 각 위치에 대해 광고 미리 로드
        positions.forEach { position ->
            if (!adCache.containsKey(position)) {
                loadAdForPosition(position)
            }
        }
    }

    private fun loadAdForPosition(position: Int) {
        // context가 초기화되지 않았으면 리턴
        if (!::context.isInitialized) return

        val adRequest = AdRequest.Builder().build()

        AdLoader.Builder(context, BuildConfig.AD_MOB_APP_UNIT_NATIVE_TEST_ID)
            .forNativeAd { nativeAd ->
                // 광고를 캐시에 저장
                adCache[position] = nativeAd
                // 현재 화면에 보이는 뷰홀더 갱신
                notifyItemChanged(position)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // 광고 로드 실패 시 처리
                    android.util.Log.e("AdmobContainerAdapter", "Ad failed to load: ${loadAdError.message}")
                }
            })
            .build()
            .loadAd(adRequest)
    }

    // 로드 상태 어댑터와 함께 사용하기 위한 메서드
    fun withLoadStateAdapter(loadStateAdapter: com.tenacy.roadcapture.ui.LoadStateAdapter): RecyclerView.Adapter<*> {
        // 원본 어댑터에 로드 상태 리스너 연결
        originalAdapter.addLoadStateListener { loadStates ->
            loadStateAdapter.loadState = loadStates.append
        }

        // 설정에 따라 ConcatAdapter 반환
        val config = ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .build()

        return ConcatAdapter(config, this, loadStateAdapter)
    }

    // 콘텐츠 아이템 뷰홀더 (프록시)
    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    // 네이티브 광고 뷰홀더
    inner class AdViewHolder(private val binding: ItemNativeAdBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            // 광고 플레이스홀더 보이기 (미리 공간 확보)
//            binding.adPlaceholder.visibility = View.VISIBLE
            binding.adContainer.visibility = View.GONE

            // 캐시된 광고가 있는지 확인
            val cachedAd = adCache[position]
            if (cachedAd != null) {
                // 캐시에서 광고 사용
//                binding.adPlaceholder.visibility = View.GONE
                binding.adContainer.visibility = View.VISIBLE
                populateNativeAdView(cachedAd)
            } else {
                // 캐시된 광고가 없으면 로드
                val adRequest = AdRequest.Builder().build()

                AdLoader.Builder(binding.root.context, BuildConfig.AD_MOB_APP_UNIT_NATIVE_TEST_ID)
                    .forNativeAd { nativeAd ->
                        // 광고를 캐시에 저장
                        adCache[position] = nativeAd

                        // 광고 표시
//                        binding.adPlaceholder.visibility = View.GONE
                        binding.adContainer.visibility = View.VISIBLE
                        populateNativeAdView(nativeAd)
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            // 광고 로드 실패 시 처리
//                            binding.adPlaceholder.visibility = View.GONE
                            binding.adContainer.visibility = View.GONE
                        }
                    })
                    .build()
                    .loadAd(adRequest)
            }
        }

        private fun populateNativeAdView(nativeAd: NativeAd) {
            val nativeAdView = binding.nativeAdView

            // 광고 요소들을 NativeAdView에 연결
            binding.adHeadline.text = nativeAd.headline
            nativeAdView.headlineView = binding.adHeadline

            binding.adBody.text = nativeAd.body
            nativeAdView.bodyView = binding.adBody

            if (nativeAd.callToAction != null) {
                binding.adCallToAction.text = nativeAd.callToAction
                nativeAdView.callToActionView = binding.adCallToAction
                binding.adCallToAction.visibility = View.VISIBLE
            } else {
                binding.adCallToAction.visibility = View.GONE
            }

            val icon = nativeAd.icon
            if (icon != null) {
                binding.adIcon.showImage(
                    url = null,
                    bitmap = null,
                    uri = icon.uri,
                    radius = 4,
                    borderWidth = null,
                    borderColor = null,
                    coverColor = null,
                )
                nativeAdView.iconView = binding.adIcon
                binding.adIcon.visibility = View.VISIBLE
            } else {
                binding.adIcon.visibility = View.GONE
            }

            if (nativeAd.starRating != null) {
                binding.adStars.rating = nativeAd.starRating!!.toFloat()
                nativeAdView.starRatingView = binding.adStars
                binding.adStars.visibility = View.VISIBLE
            } else {
                binding.adStars.visibility = View.GONE
            }

            // 미디어 콘텐츠 설정 (동영상/이미지)
            if (nativeAd.mediaContent != null) {
                binding.adMediaView.mediaContent = nativeAd.mediaContent
                nativeAdView.mediaView = binding.adMediaView
                binding.adMediaView.visibility = View.VISIBLE
            } else {
                binding.adMediaView.visibility = View.GONE
            }

            // 광고 객체를 NativeAdView에 등록
            nativeAdView.setNativeAd(nativeAd)
        }
    }
}