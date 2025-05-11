package com.tenacy.roadcapture.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.clustering.ClusterManager
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentAlbumBinding
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import com.tenacy.roadcapture.util.consumeOnce
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

@AndroidEntryPoint
class AlbumFragment : BaseFragment(), OnMapReadyCallback, ClusterManager.OnClusterItemClickListener<ClusterMarkerItem> {

    private var _binding: FragmentAlbumBinding? = null
    val binding get() = _binding!!

    private val vm: AlbumViewModel by viewModels()

    private lateinit var map: GoogleMap

    private lateinit var clusterManager: ClusterManager<ClusterMarkerItem>

    private var isClusterManagerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ===== 4. 인터페이스 구현 메서드 그룹 =====
    override fun onMapReady(map: GoogleMap) {
        this@AlbumFragment.map = map

        observeData()
        setupMaps()
        setupClusterManager()
    }

    override fun onClusterItemClick(item: ClusterMarkerItem): Boolean {
        try {
            navigateToMemoryViewer(item)
            return true // 이벤트 소비
        } catch (e: Exception) {
            Log.e("TripFragment", "마커 클릭 처리 오류", e)
            return false
        }
    }

    // ===== 5. 초기 설정 메서드 그룹 =====
    private fun setupFragmentResultListeners() {
        childFragmentManager.setFragmentResultListener(
            RangeSelectBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<RangeSelectBottomSheetFragment.ParamsOut>(RangeSelectBottomSheetFragment.RESULT_ITEMS)
                ?.let {
                    Log.d("TAG", "Positive Button Clicked!")
                    if (it.viewScope == ViewScope.AROUND) {
                        navigateToMemoryViewer(it.items)
                    } else {
                        it.items.getOrNull(0)?.let { navigateToMemoryViewer(it) }
                    }
                }
        }
    }

    private fun setupViews() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupObservers() {
        observeViewEvents()
        observeSavedState()
    }

    @SuppressLint("MissingPermission")
    private fun setupMaps() {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false

        // TODO: 첫 번째 마커 위치로 이동
//        moveCameraTo(zoom = 30f)
    }

    private fun setupClusterManager() {
        if (isClusterManagerInitialized) return

        // 클러스터 매니저 초기화
        clusterManager = ClusterManager(requireContext(), map)

        // 클러스터 매니저 옵션 설정
        clusterManager.setAnimation(true)  // 클러스터 애니메이션 활성화

        // 커스텀 렌더러 생성 및 적용
        clusterManager.renderer = MarkerClusterRenderer(this@AlbumFragment, map, clusterManager)

        // 클러스터 아이템 클릭 리스너 설정
        clusterManager.setOnClusterItemClickListener(this)

        // 클러스터 클릭 리스너 설정
        clusterManager.setOnClusterClickListener { cluster ->
            // 현재 줌 레벨 확인
            val currentZoom = map.cameraPosition.zoom

            // 최대 줌 레벨에 근접했는지 확인 (Google Maps의 최대 줌은 21.0f)
            if (currentZoom >= 20.0f) {  // 거의 최대 줌 상태로 간주
                // 최대 줌 상태에서 클러스터를 클릭한 경우 -> 특별 처리
                showRangeSelectingDialog(cluster.items.toList())
                return@setOnClusterClickListener true
            }

            // 최대 줌이 아닌 상태에서는 기존처럼 확대 계속 진행
            val builder = LatLngBounds.Builder()
            for (item in cluster.items) {
                builder.include(item.position)
            }

            // 해당 위치로 카메라 이동
            val bounds = builder.build()
            val padding = resources.displayMetrics.widthPixels / 6 // 패딩 값

            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                true // 이벤트 소비
            } catch (e: Exception) {
                // 드물게 IllegalStateException 발생 가능
                Log.e("TripFragment", "클러스터 확대 오류", e)
                false
            }
        }

        // 카메라 이동 완료 리스너 설정
        val mapCameraIdleListener = GoogleMap.OnCameraIdleListener {
            // 클러스터 관리 처리
            clusterManager.onCameraIdle()

            // 줌 레벨에 따른 경로 최적화 적용
            vm.routePoints.value?.let { applyRouteOptimization(it) }
        }

        // 맵에 리스너 설정
        map.setOnCameraIdleListener(mapCameraIdleListener)
        map.setOnMarkerClickListener(clusterManager)

        isClusterManagerInitialized = true
    }

    // ===== 7. 데이터 관찰 메서드 그룹 =====
    private fun observeData() {
        repeatOnLifecycle {
            vm.routePoints.collectLatest { points ->
                points?.let(::updateRouteOnMap)
            }
        }
        repeatOnLifecycle {
            vm.markers.collectLatest { markers ->
                markers?.let(::updatePhotoMarkers)
            }
        }
    }

    private fun observeSavedState() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_MEMORY_VIEWER) { bundle ->
                if (bundle == null) return@consumeOnce
                val coordinates = bundle.getParcelable<LatLng?>(RESULT_COORDINATES)
                vm.viewEvent(AlbumViewEvent.SetCamera(coordinates))
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? AlbumViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    // ===== 8. 이벤트 처리 메서드 그룹 =====
    private fun handleViewEvents(event: AlbumViewEvent) {
        when (event) {
            is AlbumViewEvent.ResetCameraPosition -> {
                resetCameraPosition()
            }

            is AlbumViewEvent.ZoomIn -> {
                zoomIn()
            }

            is AlbumViewEvent.ZoomOut -> {
                zoomOut()
            }

            is AlbumViewEvent.SetCamera -> {
                moveCameraTo(event.coordinates, zoom = event.zoom ?: map.cameraPosition.zoom)
            }

            is AlbumViewEvent.ShowInfo -> {
                val bottomSheet = AlbumInfoBottomSheetFragment.newInstance(
                    bundle = bundleOf(
                        AlbumInfoBottomSheetFragment.KEY_PARAMS to
                                AlbumInfoBottomSheetFragment.ParamsIn(
                                    album = event.album,
                                    totalMemoryCount = event.totalMemoryCount
                                ),
                    )
                )
                bottomSheet.show(childFragmentManager, AlbumInfoBottomSheetFragment.TAG)
            }

            is AlbumViewEvent.Share -> {

            }

            is AlbumViewEvent.NavigateToStudio -> {

            }

            is AlbumViewEvent.Forbidden -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(event.message, ToastMessageType.Warning)))
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        HomeFragment.KEY_ALBUM,
                        bundleOf(
                            HomeFragment.RESULT_FORBIDDEN to System.currentTimeMillis()
                        )
                    )
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun navigateToMemoryViewer(item: ClusterMarkerItem) {
        lifecycleScope.launch {
            val memories = vm.getMemories()
            val selectedMemoryId = vm.getMemoryIdBy(item)
            val memoryViewerArguments = MemoryViewerArguments(
                selectedMemoryId = selectedMemoryId,
                viewScope = ViewScope.WHOLE,
                memories = memories.map {
                    val coordinates = vm.getCoordinatesByLocationId(it.locationRefId)
                    MemoryViewerArguments.Memory.from(it, coordinates)
                },
            )
            findNavController().navigate(AlbumFragmentDirections.actionAlbumToMemoryViewer(memoryViewerArguments))
        }
    }

    private fun navigateToMemoryViewer(items: List<ClusterMarkerItem>) {
        lifecycleScope.launch {
            val itemsById = items.associateBy { it.id }
            val memories = vm.getMemoriesIn(items)
            val memoryViewerArguments = MemoryViewerArguments(
                viewScope = ViewScope.AROUND,
                memories = memories.map {
                    MemoryViewerArguments.Memory.from(
                        it,
                        itemsById[it.locationRefId]!!.position
                    )
                },
            )
            findNavController().navigate(AlbumFragmentDirections.actionAlbumToMemoryViewer(memoryViewerArguments))
        }
    }

    private fun showRangeSelectingDialog(items: List<ClusterMarkerItem>) {
        lifecycleScope.launch {
            val bottomSheet = RangeSelectBottomSheetFragment.newInstance(
                bundle = bundleOf(
                    RangeSelectBottomSheetFragment.KEY_PARAMS to RangeSelectBottomSheetFragment.ParamsIn(items = items),
                )
            )
            bottomSheet.show(childFragmentManager, RangeSelectBottomSheetFragment.TAG)
        }
    }

    // ===== 9. 지도 컨트롤 메서드 그룹 =====
    @SuppressLint("MissingPermission")
    private fun moveCameraTo(latLng: LatLng? = null, zoom: Float = map.cameraPosition.zoom) {
        latLng?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom)) }
    }

    private fun resetCameraPosition() {
        // 북쪽 방향으로 맵 회전
        val cameraPosition = CameraPosition.Builder()
            .target(map.cameraPosition.target)
            .zoom(map.cameraPosition.zoom)
            .bearing(0f)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun zoomOut() {
        // 현재 줌 레벨에서 1단계 축소
        val currentZoom = map.cameraPosition.zoom
        val newZoom = (currentZoom - 1.0f).coerceAtLeast(2.0f) // 최소 줌 레벨 (2.0f)로 제한
        map.animateCamera(CameraUpdateFactory.zoomTo(newZoom))
    }

    private fun zoomIn() {
        // 현재 줌 레벨에서 1단계 확대
        val currentZoom = map.cameraPosition.zoom
        val newZoom = (currentZoom + 1.0f).coerceAtMost(21.0f) // 최대 줌 레벨 (21.0f)로 제한
        map.animateCamera(CameraUpdateFactory.zoomTo(newZoom))
    }

    // ===== 10. 경로 처리 메서드 그룹 =====
    private fun updateRouteOnMap(routePoints: List<LatLng>) {
        if (!::map.isInitialized || routePoints.isEmpty()) return

        // 초기 렌더링에서도 간소화와 LOD 적용
        applyRouteOptimization(routePoints)
    }

    private fun applyRouteOptimization(routePoints: List<LatLng>) {
        if (!::map.isInitialized || routePoints.isEmpty()) return

        // 현재 줌 레벨에 따른 간소화 수준 결정
        val zoom = map.cameraPosition.zoom
        val simplificationTolerance = when {
            zoom >= 18 -> 5.0   // 매우 가까운 줌: 높은 상세도 (적은 간소화)
            zoom >= 15 -> 10.0  // 가까운 줌: 중간 상세도
            zoom >= 12 -> 20.0  // 중간 줌: 낮은 상세도
            zoom >= 9 -> 50.0   // 먼 줌: 매우 낮은 상세도
            else -> 100.0       // 매우 먼 줌: 극도로 낮은 상세도
        }

        // 백그라운드에서 좌표점 간소화 처리
        lifecycleScope.launch(Dispatchers.Default) {
            // 좌표점 간소화 적용 (Douglas-Peucker 알고리즘)
            val optimizedPoints = PolyUtil.simplify(routePoints, simplificationTolerance)

            Log.d("RouteOptimization", "원본 포인트: ${routePoints.size}, 간소화 후: ${optimizedPoints.size}")

            // UI 스레드에서 경로 업데이트
            withContext(Dispatchers.Main) {
                // 기존 Polyline들이 있으면 제거
                vm.clearRoutePolylines()

                // 포인트가 2개 미만이면 그리지 않음
                if (optimizedPoints.size < 2) return@withContext

                // 그라데이션 색상 배열 - 시작(파란색)에서 끝(빨간색)까지
                val startColor = ContextCompat.getColor(requireContext(), R.color.line_neutral)
                val endColor = ContextCompat.getColor(requireContext(), R.color.line_strong)

                // 각 폴리라인 조각을 생성할 세그먼트 수 결정
                val segmentCount = 20
                val segmentSize = (optimizedPoints.size - 1) / segmentCount.coerceAtMost(optimizedPoints.size - 1)

                // 각 세그먼트마다 폴리라인 생성
                for (i in 0 until optimizedPoints.size - 1 step segmentSize.coerceAtLeast(1)) {
                    val endIdx = (i + segmentSize).coerceAtMost(optimizedPoints.size - 1)
                    val segmentPoints = optimizedPoints.subList(i, endIdx + 1)

                    // 현재 세그먼트의 위치 비율 계산 (0.0 ~ 1.0)
                    val ratio = i.toFloat() / (optimizedPoints.size - 1).coerceAtLeast(1)

                    // 색상 보간
                    val segmentColor = interpolateColor(startColor, endColor, ratio)

                    // 폴리라인 추가 (둥근 모서리 적용)
                    val polylineOptions = PolylineOptions()
                        .addAll(segmentPoints)
                        .width(4f.toPx.toFloat())
                        .color(segmentColor)
                        .geodesic(true)
                        .startCap(RoundCap()) // 시작 부분 둥글게
                        .endCap(RoundCap())   // 끝 부분 둥글게

                    // 폴리라인 생성 및 리스트에 추가
                    vm.addRoutePolyline(map.addPolyline(polylineOptions))

                    // 마지막 세그먼트에 도달했으면 종료
                    if (endIdx >= optimizedPoints.size - 1) break
                }
            }
        }
    }

    // 색상 보간 함수
    private fun interpolateColor(startColor: Int, endColor: Int, ratio: Float): Int {
        val startA = (startColor shr 24) and 0xff
        val startR = (startColor shr 16) and 0xff
        val startG = (startColor shr 8) and 0xff
        val startB = startColor and 0xff

        val endA = (endColor shr 24) and 0xff
        val endR = (endColor shr 16) and 0xff
        val endG = (endColor shr 8) and 0xff
        val endB = endColor and 0xff

        val a = startA + ((endA - startA) * ratio).toInt()
        val r = startR + ((endR - startR) * ratio).toInt()
        val g = startG + ((endG - startG) * ratio).toInt()
        val b = startB + ((endB - startB) * ratio).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ===== 11. 마커 처리 메서드 그룹 =====
    private fun updatePhotoMarkers(markers: List<Marker>) {
        if (!::map.isInitialized || !::clusterManager.isInitialized) return

        val currentMarkerIds = vm.getMarkerIds()
        val updatedItems = mutableSetOf<String>()

        // Process markers with photos
        for (location in markers) {
            if (location.photo != null) {
                val markerId = location.id
                val position = LatLng(location.latitude, location.longitude)
                updatedItems.add(markerId)

                if (!vm.containsMarkerId(markerId)) {
                    // Create new cluster item for new markers
                    createPhotoClusterItem(
                        markerId,
                        position,
                        photoUri = location.photo.photoUri,
                        photoUrl = location.photo.photoUrl
                    )
                }
            }
        }

        // Remove markers that are no longer needed
        val markersToRemove = currentMarkerIds - updatedItems
        for (markerId in markersToRemove) {
            vm.getClusterItem(markerId)?.let { clusterManager.removeItem(it) }
            vm.removeClusterItem(markerId)
        }

        // Update the cluster
        clusterManager.cluster()
    }

    private fun createPhotoClusterItem(
        markerId: String,
        position: LatLng,
        photoUri: Uri? = null,
        photoUrl: String = ""
    ) {
        try {
            // Create cluster item
            val clusterItem =
                ClusterMarkerItem(markerId, position, "사진: $markerId", "", photoUri = photoUri, photoUrl = photoUrl)

            // Add to cluster manager
            clusterManager.addItem(clusterItem)
            vm.addClusterItem(markerId, clusterItem)
        } catch (e: Exception) {
            Log.e("TripFragment", "Error creating photo cluster item", e)
        }
    }

    // ===== 13. 데이터 클래스 및 상수 정의 그룹 =====
    @Parcelize
    data class Address(
        val country: String?,
        val formattedAddress: String?,
        val components: List<String>,
        val coordinates: LatLng
    ) : Parcelable

    companion object {
        const val RESULT_COORDINATES = "coordinates"
        const val KEY_MEMORY_VIEWER = "memory_viewer"
    }
}