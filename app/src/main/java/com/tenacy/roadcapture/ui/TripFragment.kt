package com.tenacy.roadcapture.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.CameraUpdateFactory.zoomIn
import com.google.android.gms.maps.CameraUpdateFactory.zoomOut
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.clustering.ClusterManager
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.databinding.FragmentTripBinding
import com.tenacy.roadcapture.util.extractLocationData
import com.tenacy.roadcapture.util.mainActivity
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.toPx
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@AndroidEntryPoint
class TripFragment: BaseFragment(), OnMapReadyCallback, ClusterManager.OnClusterItemClickListener<ClusterMarkerItem> {

    // ===== 1. 속성(Property) 그룹 =====
    private var _binding: FragmentTripBinding? = null
    val binding get() = _binding!!

    private val vm: TripViewModel by viewModels()

    private lateinit var map: GoogleMap
    private var routePolylines: MutableList<Polyline> = mutableListOf()

    private lateinit var markerRenderer: MarkerClusterRenderer
    private lateinit var clusterManager: ClusterManager<ClusterMarkerItem>
    private val clusterItems = mutableMapOf<Long, ClusterMarkerItem>()
    private var isClusterManagerInitialized = false

    // ===== 2. 권한 처리 관련 리스너 그룹 =====
    private val cameraPermissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
            Log.d("TAG", "카메라 권한 허용됨")
            checkLocationPermission()
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
            Log.d("TAG", "카메라 권한 거부됨")
            lifecycleScope.launch(Dispatchers.Main) {
                vm.stopTraveling()
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("카메라 권한이 없어\n앨범을 만들 수 없습니다", ToastMessageType.Warning)))
            }
        }
    }

    private val locationPermissionListener = object : PermissionListener {
        @SuppressLint("MissingPermission")
        override fun onPermissionGranted() {
            Log.d("TAG", "onPermissionGranted")
            vm.startTraveling()
            setupMaps()
            setupObservers()
            setupClusterManager()
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
            Log.d("TAG", "onPermissionDenied")
            lifecycleScope.launch(Dispatchers.Main) {
                vm.stopTraveling()
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("위치 권한이 없어\n앨범을 만들 수 없습니다", ToastMessageType.Warning)))
            }
        }
    }

    // ===== 3. 라이프사이클 메서드 그룹 =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTripBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewEvents()
        setupPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ===== 4. 인터페이스 구현 메서드 그룹 =====
    override fun onMapReady(map: GoogleMap) {
        this@TripFragment.map = map
    }

    override fun onClusterItemClick(item: ClusterMarkerItem): Boolean {
        try {
            showImageDetailDialog(item)
            return true // 이벤트 소비
        } catch (e: Exception) {
            Log.e("TripFragment", "마커 클릭 처리 오류", e)
            return false
        }
    }

    // ===== 5. 초기 설정 메서드 그룹 =====
    private fun setupViews() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupObservers() {
        observeData()
        observeSavedState()
    }

    @SuppressLint("MissingPermission")
    private fun setupMaps() {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.isMyLocationEnabled = true

        moveCameraToCurrentLocation(zoom = 30f)

        setupLocationUpdates()
    }

    private fun setupClusterManager() {
        if(isClusterManagerInitialized) return

        // 클러스터 매니저 초기화
        clusterManager = ClusterManager(requireContext(), map)

        // 클러스터 매니저 옵션 설정
        clusterManager.setAnimation(true)  // 클러스터 애니메이션 활성화

        // 커스텀 렌더러 생성 및 적용
        markerRenderer = MarkerClusterRenderer(this@TripFragment, map, clusterManager)
        clusterManager.renderer = markerRenderer

        // 클러스터 아이템 클릭 리스너 설정
        clusterManager.setOnClusterItemClickListener(this)

        // 클러스터 클릭 리스너 설정
        clusterManager.setOnClusterClickListener { cluster ->
            // 현재 줌 레벨 확인
            val currentZoom = map.cameraPosition.zoom

            // 최대 줌 레벨에 근접했는지 확인 (Google Maps의 최대 줌은 21.0f)
            if (currentZoom >= 20.0f) {  // 거의 최대 줌 상태로 간주
                // 최대 줌 상태에서 클러스터를 클릭한 경우 -> 특별 처리
                showClusterItemsGallery(cluster.items.toList())
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

    private fun setupLocationUpdates() {
        repeatOnLifecycle {
            while(isActive) {
                getCurrentLocation()?.let { latLng ->
                    vm.saveCurrentLocation(latLng)
                }
                delay(10 * 1000)
            }
        }
        repeatOnLifecycle {
            while(isActive) {
                vm.updateDurationText()
                delay(60 * 1000)
            }
        }
    }

    private fun setupPermissions() {
        checkCameraPermission()
    }

    // ===== 6. 권한 처리 메서드 그룹 =====
    private fun checkLocationPermission() =
        TedPermission.create()
            .setPermissionListener(locationPermissionListener)
            .setPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .setGotoSettingButton(true)
            .setDeniedTitle("위치 권한 필요")
            .setDeniedMessage("지도에 현재 위치를 표시하기 위해 위치 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
            .setDeniedCloseButtonText("취소")
            .setGotoSettingButtonText("설정")
            .check()

    private fun checkCameraPermission() =
        TedPermission.create()
            .setPermissionListener(cameraPermissionListener)
            .setPermissions(Manifest.permission.CAMERA)
            .setGotoSettingButton(true)
            .setDeniedTitle("카메라 권한 필요")
            .setDeniedMessage("사진을 촬영하기 위해 카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
            .setDeniedCloseButtonText("취소")
            .setGotoSettingButtonText("설정")
            .check()

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
        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            findNavController().currentBackStackEntry?.savedStateHandle
                ?.getStateFlow<Long?>(KEY_MEMORY_ID, null)?.collect { memoryId ->
                    if (memoryId == null) return@collect
                    vm.fetchData()
                }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? TripViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    // ===== 8. 이벤트 처리 메서드 그룹 =====
    private fun handleViewEvents(event: TripViewEvent) {
        when (event) {
            is TripViewEvent.ResetCameraPosition -> {
                resetCameraPosition()
            }
            is TripViewEvent.ResetCamera -> {
                moveCameraToCurrentLocation()
            }
            is TripViewEvent.Capture -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val location = getCurrentLocation()
                    val placeLocation = requireContext().extractLocationData(location) ?: return@launch
                    withContext(Dispatchers.Main) {
                        findNavController().navigate(TripFragmentDirections.actionTripToCamera(placeLocation))
                    }
                }
            }
            is TripViewEvent.Back -> {
                findNavController().popBackStack()
            }
            is TripViewEvent.ZoomIn -> {
                zoomIn()
            }
            is TripViewEvent.ZoomOut -> {
                zoomOut()
            }
            is TripViewEvent.ShowGuide -> {

            }
            is TripViewEvent.ShowSubscription -> {

            }
            is TripViewEvent.ShowNextBefore -> {

            }
            is TripViewEvent.ShowDeleteBefore -> {

            }
        }
    }

    // 클러스터 내 이미지들을 갤러리 형태로 보여주는 함수
    private fun showClusterItemsGallery(items: List<ClusterMarkerItem>) {
        lifecycleScope.launch {
            // 사용자에게 정보 표시
            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                ToastModel("이 위치에 ${items.size}개의 사진이 있습니다", ToastMessageType.Info)
            ))

            // 여기서 바텀시트 또는 다이얼로그로 해당 위치의 모든 사진을 표시
            // 예: showImageGalleryBottomSheet(items)
        }
    }

    // ===== 9. 지도 컨트롤 메서드 그룹 =====
    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(): LatLng? {
        if (!::map.isInitialized) return null

        val locationManager = mainActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val criteria = Criteria()
        val provider = locationManager.getBestProvider(criteria, true)
        val location = provider?.let(locationManager::getLastKnownLocation)

        return location?.let { LatLng(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private fun moveCameraToCurrentLocation(zoom: Float = map.cameraPosition.zoom) {
        val latLng = getCurrentLocation()
        if (latLng != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        }
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
                routePolylines.forEach { it.remove() }
                routePolylines.clear()

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
                    routePolylines.add(map.addPolyline(polylineOptions))

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

        val currentMarkerIds = clusterItems.keys.toMutableSet()
        val updatedItems = mutableSetOf<Long>()

        // Process markers with photos
        for (location in markers) {
            if (location.photo != null) {
                val markerId = location.id
                val position = LatLng(location.latitude, location.longitude)
                updatedItems.add(markerId)

                if (markerId !in clusterItems) {
                    // Create new cluster item for new markers
                    createPhotoClusterItem(markerId, position, location.photo.photoUri)
                }
            }
        }

        // Remove markers that are no longer needed
        val markersToRemove = currentMarkerIds - updatedItems
        for (markerId in markersToRemove) {
            clusterItems[markerId]?.let { clusterManager.removeItem(it) }
            clusterItems.remove(markerId)
        }

        // Update the cluster
        clusterManager.cluster()
    }

    private fun createPhotoClusterItem(markerId: Long, position: LatLng, photoUri: Uri) {
        try {
            // Create cluster item
            val clusterItem = ClusterMarkerItem(markerId, position, "사진: $markerId", "", photoUri)

            // Add to cluster manager
            clusterManager.addItem(clusterItem)
            clusterItems[markerId] = clusterItem
        } catch (e: Exception) {
            Log.e("TripFragment", "Error creating photo cluster item", e)
        }
    }

    private fun showImageDetailDialog(item: ClusterMarkerItem) {
        lifecycleScope.launch {
            mainActivity.vm.viewEvent(GlobalViewEvent.Toast(
                ToastModel("사진 ID: ${item.id} 클릭됨", ToastMessageType.Info)
            ))
        }
    }

    // ===== 12. 데이터 클래스 및 상수 정의 그룹 =====
    @Parcelize
    data class Marker(
        val id: Long,
        val latitude: Double,
        val longitude: Double,
        val createdAt: LocalDateTime,
        val photo: Photo? = null,
    ): Parcelable {

        @Parcelize
        data class Photo(
            val id: Long,
            val photoUri: Uri,
        ): Parcelable

        companion object {
            fun of(dto: MemoryWithLocation) = Marker(
                dto.location.id,
                dto.location.latitude,
                dto.location.longitude,
                dto.location.createdAt,
                Photo(
                    dto.memory.id,
                    dto.memory.photoUri,
                )
            )

            fun of(dto: LocationEntity) = Marker(
                dto.id,
                dto.latitude,
                dto.longitude,
                dto.createdAt,
            )
        }
    }

    @Parcelize
    data class PlaceLocation(
        val placeId: String,
        val name: String?,                 // 장소/명소 이름
        val country: String,              // 국가
        val region: String?,              // 지역/주/도
        val city: String?,                // 도시
        val district: String?,            // 구역/동네
        val street: String?,              // 거리/도로
        val detail: String?,              // 상세주소
        val formattedAddress: String,     // 형식화된 전체 주소
        val coordinates: LatLng           // 좌표
    ): Parcelable

    companion object {
        const val KEY_MEMORY_ID = "memory_id"
    }
}