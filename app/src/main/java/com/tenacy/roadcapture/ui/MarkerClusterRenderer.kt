package com.tenacy.roadcapture.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.MarkerBinding
import com.tenacy.roadcapture.util.toPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class ClusterMarkerItem(
    val id: String,
    private val position: LatLng,
    private val title: String,
    private val snippet: String,
    val photoUri: Uri? = null,
    val photoUrl: String,
) : ClusterItem, Parcelable {
    @IgnoredOnParcel
    val photoKey = photoUri?.toString() ?: photoUrl

    override fun getPosition(): LatLng = position
    override fun getTitle(): String = title
    override fun getSnippet(): String = snippet
}

class MarkerClusterRenderer(
    private val fragment: Fragment,
    map: GoogleMap,
    clusterManager: ClusterManager<ClusterMarkerItem>,
) : DefaultClusterRenderer<ClusterMarkerItem>(fragment.requireContext(), map, clusterManager) {

    // 마커 비트맵 캐시
    private val markerBitmapCache = mutableMapOf<String, BitmapDescriptor?>()

    // 클러스터 비트맵 캐시 (크기별)
    private val clusterBitmapCache = mutableMapOf<Int, BitmapDescriptor>()

    // 마커 작업 추적
    private val markerJobs = mutableMapOf<String, Job>()

    // 클러스터 마커 관리 (클러스터 ID -> 마커)
    private val clusterMarkers = mutableMapOf<String, Marker>()

    // 클러스터가 생성되고 있는지 추적
    private var isClusteringInProgress = false

    init {
        // 클러스터링 최소 크기 설정 (숫자가 작을수록 더 적극적으로 클러스터링)
        minClusterSize = 2  // 최소 2개 이상의 마커가 가까이 있으면 클러스터링

        // 클러스터 알고리즘 조정
        clusterManager.algorithm.maxDistanceBetweenClusteredItems = 50f.toPx

        // 먼저 자주 사용되는 클러스터 아이콘을 미리 생성
        fragment.lifecycleScope.launch {
            // 일반적인 클러스터 크기에 대한 아이콘 미리 생성 (2-10)
            for (i in 2..10) {
                createClusterMarkerBitmap(i)?.let { bitmap ->
                    clusterBitmapCache[i] = bitmap
                }
            }
        }
    }

    override fun onBeforeClusterItemRendered(item: ClusterMarkerItem, markerOptions: MarkerOptions) {
        // 캐시된 비트맵 있는지 확인
        val cachedBitmap = markerBitmapCache[item.photoKey]

        if (cachedBitmap != null) {
            // 캐시된 비트맵 사용
            markerOptions.icon(cachedBitmap)
            markerOptions.title(item.title)
        } else {
            // 기본 마커 사용 (로딩 중)
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))

            // 아직 로딩 중이 아니면 커스텀 마커 로딩 시작
            if (!markerJobs.containsKey(item.id)) {
                val job = fragment.lifecycleScope.launch {
                    try {
                        val requestBuilder = item.photoUri?.let { createRequestBuilder(it) } ?: createRequestBuilder(item.photoUrl)
                        val icon = createCustomMarkerBitmap(requestBuilder)
                        markerBitmapCache[item.photoKey] = icon

                        // 비트맵 로딩 후 마커 업데이트
                        withContext(Dispatchers.Main) {
                            getMarker(item)?.setIcon(icon)
                        }
                    } catch (e: Exception) {
                        Log.e("MarkerClusterRenderer", "마커 렌더링 오류", e)
                        markerBitmapCache[item.photoKey] = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } finally {
                        markerJobs.remove(item.id)
                    }
                }
                markerJobs[item.id] = job
            }
        }
    }

    override fun onBeforeClusterRendered(cluster: Cluster<ClusterMarkerItem>, markerOptions: MarkerOptions) {
        // 클러스터링 진행 중일 때는 클러스터 마커를 처음에 숨김
        if (isClusteringInProgress) {
            markerOptions.visible(false)
        }

        // 캐시된 클러스터 아이콘이 있는지 확인
        val clusterSize = cluster.size
        val cachedBitmap = clusterBitmapCache[clusterSize]

        if (cachedBitmap != null) {
            // 캐시된 클러스터 아이콘 사용
            markerOptions.icon(cachedBitmap)
        } else {
            // 없으면 즉시 생성 시도 (동기적으로)
            fragment.lifecycleScope.launch(Dispatchers.Main.immediate) {
                try {
                    val bitmap = createClusterMarkerBitmap(clusterSize)
                    bitmap?.let {
                        // 캐시에 저장
                        clusterBitmapCache[clusterSize] = it

                        // 현재 클러스터 마커에 적용
                        val clusterId = getClusterId(cluster)
                        clusterMarkers[clusterId]?.setIcon(it)
                    }
                } catch (e: Exception) {
                    Log.e("MarkerClusterRenderer", "클러스터 렌더링 오류", e)
                }
            }
        }
    }

    override fun onClusterRendered(cluster: Cluster<ClusterMarkerItem>, marker: Marker) {
        super.onClusterRendered(cluster, marker)

        // 클러스터 마커 저장 (나중에 표시하기 위해)
        val clusterId = getClusterId(cluster)
        clusterMarkers[clusterId] = marker

        // 캐시된 클러스터 아이콘이 있는지 확인하고 즉시 적용
        val clusterSize = cluster.size
        clusterBitmapCache[clusterSize]?.let { cachedIcon ->
            marker.setIcon(cachedIcon)
        }
    }

    // 클러스터 ID 생성
    private fun getClusterId(cluster: Cluster<ClusterMarkerItem>): String {
        // 클러스터 중심점으로 ID 생성
        val position = cluster.position
        return "${position.latitude},${position.longitude},${cluster.size}"
    }

    override fun onClustersChanged(clusters: Set<Cluster<ClusterMarkerItem>>) {
        // 클러스터링 시작 시 상태 업데이트
        isClusteringInProgress = true

        // 마커가 움직이는 애니메이션 완료 후 클러스터 마커 표시를 위해
        // 애니메이션 리스너 설정이 필요함

        // 애니메이션 완료될 때까지 약간의 지연 후 클러스터 마커 표시
        fragment.lifecycleScope.launch {
//            delay(ANIMATION_DELAY)  // 애니메이션 완료 대기

            withContext(Dispatchers.Main) {
                // 모든 클러스터 마커 표시
                for ((clusterId, marker) in clusterMarkers) {
                    // 마커가 아직 유효한지 확인
                    try {
                        // 클러스터 크기 추출
                        val parts = clusterId.split(",")
                        if (parts.size >= 3) {
                            val clusterSize = parts[2].toIntOrNull() ?: 2

                            // 캐시된 아이콘이 있는지 확인하고 적용
                            clusterBitmapCache[clusterSize]?.let { cachedIcon ->
                                marker.setIcon(cachedIcon)
                            }
                        }

                        // 마커 표시
                        marker.isVisible = true
                    } catch (e: Exception) {
                        // 마커가 이미 제거되었거나 다른 오류
                        Log.e("MarkerClusterRenderer", "클러스터 마커 업데이트 오류", e)
                    }
                }

                // 클러스터링 상태 초기화
                isClusteringInProgress = false
            }
        }

        super.onClustersChanged(clusters)
    }

    override fun onClusterItemRendered(clusterItem: ClusterMarkerItem, marker: Marker) {
        super.onClusterItemRendered(clusterItem, marker)

        // 캐시된 비트맵이 있다면 마커 업데이트
        markerBitmapCache[clusterItem.photoKey]?.let { bitmap ->
            marker.setIcon(bitmap)
        }
    }

    override fun shouldRenderAsCluster(cluster: Cluster<ClusterMarkerItem>): Boolean {
        // 클러스터 크기가 2 이상이면 클러스터로 렌더링
        return cluster.size >= 2
    }

    // 커스텀 마커 비트맵 생성
    private suspend fun createRequestBuilder(uri: Uri) =
        Glide.with(fragment.requireContext())
            .asBitmap()
            .load(uri)

    private suspend fun createRequestBuilder(url: String) =
        Glide.with(fragment.requireContext())
            .asBitmap()
            .load(url)

    private suspend fun createCustomMarkerBitmap(requestBuilder: RequestBuilder<Bitmap>): BitmapDescriptor? {
        // 커스텀 레이아웃 인플레이트
        val markerBinding = MarkerBinding.inflate(LayoutInflater.from(fragment.requireContext()))
        val markerView = markerBinding.root

        return withContext(Dispatchers.IO) {
            try {
                // 백그라운드에서 이미지 로드
                val bitmap = requestBuilder
                    .transform(buildTransformations(100))
                    .submit(240, 240)
                    .get()

                // UI 스레드에서 이미지 설정
                withContext(Dispatchers.Main) {
                    markerBinding.imgMarker.setImageBitmap(bitmap)

                    // 측정 및 레이아웃
                    markerView.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)

                    // 비트맵 생성
                    val resultBitmap = Bitmap.createBitmap(
                        markerView.measuredWidth,
                        markerView.measuredHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    markerView.draw(Canvas(resultBitmap))

                    // BitmapDescriptor로 변환
                    BitmapDescriptorFactory.fromBitmap(resultBitmap)
                }
            } catch (e: Exception) {
                Log.e("MarkerClusterRenderer", "커스텀 마커 생성 오류", e)
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            }
        }
    }

    // 클러스터 마커 비트맵 생성
    private suspend fun createClusterMarkerBitmap(clusterSize: Int): BitmapDescriptor? {
        return withContext(Dispatchers.Default) {
            try {
                // 클러스터 배경 색상
                val backgroundColor = ContextCompat.getColor(fragment.requireContext(), R.color.primary)

                // 클러스터 마커 크기 (클러스터 크기에 따라 달라질 수 있음)
                val size = 50.toPx

                // 비트맵 생성
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // 배경 원 그리기
                val paint = Paint()
                paint.color = backgroundColor
                paint.isAntiAlias = true
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

                // 테두리 그리기
                paint.color = ContextCompat.getColor(fragment.requireContext(), R.color.background_normal)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)

                // 텍스트 그리기
                paint.color = ContextCompat.getColor(fragment.requireContext(), R.color.background_normal)
                paint.style = Paint.Style.FILL
                paint.textSize = 18.toPx.toFloat()
                paint.textAlign = Paint.Align.CENTER

                // 텍스트 위치 계산 (세로 중앙)
                val textHeight = paint.descent() - paint.ascent()
                val textOffset = textHeight / 2 - paint.descent()

                // 클러스터 크기 텍스트 그리기
                canvas.drawText(
                    clusterSize.toString(),
                    size / 2f,
                    size / 2f + textOffset,
                    paint
                )

                BitmapDescriptorFactory.fromBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("MarkerClusterRenderer", "클러스터 마커 생성 오류", e)
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            }
        }
    }
}