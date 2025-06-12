package com.tenacy.roadcapture.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.clustering.ClusterManager
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.FragmentAlbumBinding
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            bundle.getParcelable<RangeSelectBottomSheetFragment.ParamsOut.View>(RangeSelectBottomSheetFragment.KEY_PARAMS_OUT_VIEW)
                ?.let {
                    Log.d("TAG", "Positive Button Clicked!")
                    if (it.viewScope == ViewScope.AROUND) {
                        navigateToMemoryViewer(it.items)
                    } else {
                        it.items.getOrNull(0)?.let { navigateToMemoryViewer(it) }
                    }
                }

        }

        childFragmentManager.setFragmentResultListener(
            ReportBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<ReportBottomSheetFragment.ParamsOut.Report>(ReportBottomSheetFragment.KEY_PARAMS_OUT_REPORT)
                ?.let {
                    Log.d("TAG", "Positive Button Clicked!")
                    vm.report(it.albumId, it.reason)
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
            val paddingTop = 133.toPx // 패딩 값
            val paddingBottom = 192.toPx
            val paddingStart = 52.toPx
            val paddingEnd = 52.toPx

            try {
                map.setPadding(paddingStart, paddingTop, paddingEnd, paddingBottom)
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
                map.setPadding(0, 0, 0, 0)
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
                val coordinates = bundle.getParcelable<Location?>(RESULT_COORDINATES)
                vm.viewEvent(AlbumViewEvent.SetCamera(coordinates))
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    handleViewEvents(event)
                }
            }
        }
    }

    // ===== 8. 이벤트 처리 메서드 그룹 =====
    private fun handleViewEvents(event: ViewEvent) {
        // [VALIDATE_SYSTEM_CONFIG]
        if(event is CommonSystemViewEvent) {
            handleCommonSystemViewEvents(event)
            return
        }
        
        if(event is AlbumViewEvent) {
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
                            AlbumInfoBottomSheetFragment.KEY_PARAMS_IN to
                                    AlbumInfoBottomSheetFragment.ParamsIn(
                                        album = event.album,
                                        totalMemoryCount = event.totalMemoryCount
                                    ),
                        )
                    )
                    bottomSheet.show(childFragmentManager, AlbumInfoBottomSheetFragment.TAG)
                }

                is AlbumViewEvent.Share -> {
                    if(event.link.isNullOrBlank()) {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.share_link_not_exist), ToastMessageType.Warning)))
                        return
                    }
                    shareLink(event.link)
                }

                is AlbumViewEvent.NavigateToStudio -> {

                }

                is AlbumViewEvent.Forbidden -> {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(event.message, ToastMessageType.Warning)))
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        HomeFragment.KEY_ALBUM,
                        bundleOf(
                            HomeFragment.RESULT_FORBIDDEN to System.currentTimeMillis()
                        )
                    )
                    findNavController().popBackStack()
                }

                is AlbumViewEvent.ShowReport -> {
                    val bottomSheet = ReportBottomSheetFragment.newInstance(
                        bundleOf(
                            ReportBottomSheetFragment.KEY_PARAMS_IN to ReportBottomSheetFragment.ParamsIn(event.albumId)
                        )
                    )
                    bottomSheet.show(childFragmentManager, ReportBottomSheetFragment.TAG)
                }

                is AlbumViewEvent.ReportComplete -> {
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel(requireContext().getString(R.string.report_submit_success), ToastMessageType.Success)))
                }
            }

        }
    }

    private fun shareLink(link: String, title: String = requireContext().getString(R.string.album_share)) {
        // 공유할 텍스트 메시지 (제목과 링크 포함)
        val shareText = "$link"

        // 공유 인텐트 생성
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        // 공유 앱 선택 다이얼로그 표시
        mainActivity.startActivity(Intent.createChooser(shareIntent, title))
    }

    private fun navigateToMemoryViewer(item: ClusterMarkerItem) {
        viewLifecycleOwner.lifecycleScope.launch {
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
        viewLifecycleOwner.lifecycleScope.launch {
            val memories = vm.getMemoriesIn(items)
            val memoryViewerArguments = MemoryViewerArguments(
                viewScope = ViewScope.AROUND,
                memories = memories.map {
                    val coordinates = vm.getCoordinatesByLocationId(it.locationRefId)
                    MemoryViewerArguments.Memory.from(it, coordinates)
                },
            )
            findNavController().navigate(AlbumFragmentDirections.actionAlbumToMemoryViewer(memoryViewerArguments))
        }
    }

    private fun showRangeSelectingDialog(items: List<ClusterMarkerItem>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bottomSheet = RangeSelectBottomSheetFragment.newInstance(
                bundle = bundleOf(
                    RangeSelectBottomSheetFragment.KEY_PARAMS_IN to RangeSelectBottomSheetFragment.ParamsIn(items = items),
                )
            )
            bottomSheet.show(childFragmentManager, RangeSelectBottomSheetFragment.TAG)
        }
    }

    // ===== 9. 지도 컨트롤 메서드 그룹 =====
    @SuppressLint("MissingPermission")
    private fun moveCameraTo(location: Location? = null, zoom: Float = map.cameraPosition.zoom) {
        location?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), zoom)) }
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
    private fun updateRouteOnMap(routePoints: List<Location>) {
        if (!::map.isInitialized || routePoints.isEmpty()) return

        // 초기 렌더링에서도 간소화와 LOD 적용
        applyRouteOptimization(routePoints)
    }

    private fun applyRouteOptimization(routePoints: List<Location>) {
        if (!::map.isInitialized || routePoints.isEmpty()) return

        // 현재 줌 레벨에 따른 간소화 수준 결정
        val zoom = map.cameraPosition.zoom
        val simplificationTolerance = when {
            zoom >= 18 -> 2.0    // 매우 가까운 줌
            zoom >= 15 -> 10.0   // 가까운 줌
            zoom >= 12 -> 50.0   // 중간 줌
            zoom >= 9 -> 250.0   // 먼 줌
            else -> 750.0        // 매우 먼 줌
        }

        // 백그라운드에서 좌표점 간소화 처리
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            // 좌표점 간소화 적용 (Douglas-Peucker 알고리즘)
            val optimizedPoints = PolyUtil.simplify(routePoints.map(Location::toLatLng), simplificationTolerance)

            Log.d("RouteOptimization", "원본 포인트: ${routePoints.size}, 간소화 후: ${optimizedPoints.size}")

            // UI 스레드에서 경로 업데이트
            withContext(Dispatchers.Main) {
                // 기존 Polyline들이 있으면 제거
                vm.clearRoutePolylines()

                // 포인트가 2개 미만이면 그리지 않음
                if (optimizedPoints.size < 2) return@withContext

                // 그라데이션 색상 배열 - 시작(파란색)에서 끝(빨간색)까지
//                val startColor = ContextCompat.getColor(requireContext(), R.color.line_neutral)
                val startColor = Color.parseColor("#00857D")
                val endColor = ContextCompat.getColor(requireContext(), R.color.primary_normal)

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

                    val backgroundPolylineOptions = PolylineOptions()
                        .addAll(segmentPoints)
                        .width(9f.toPx.toFloat()) // 원래 선보다 넓게 설정 (0.5px 추가)
                        .color(ContextCompat.getColor(requireContext(), R.color.label_normal))
                        .geodesic(true)
                        .startCap(RoundCap())
                        .endCap(RoundCap())
                        .zIndex(1f) // 레이어 순서 설정 (더 낮은 z-index가 아래에 그려짐)

                    val backgroundPolyline = map.addPolyline(backgroundPolylineOptions)
                    vm.addRoutePolyline(backgroundPolyline)

                    val foregroundPolylineOptions = PolylineOptions()
                        .addAll(segmentPoints)
                        .width(8f.toPx.toFloat()) // 원래 설정한 크기 유지
                        .color(segmentColor) // 원래 색상 그대로 유지
                        .geodesic(true)
                        .startCap(RoundCap())
                        .endCap(RoundCap())
                        .zIndex(2f) // 더 높은 z-index로 위에 그려지도록 설정

                    val foregroundPolyline = map.addPolyline(foregroundPolylineOptions)
                    vm.addRoutePolyline(foregroundPolyline)

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
                val position = location.coordinates
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
        position: Location,
        photoUri: Uri? = null,
        photoUrl: String = ""
    ) {
        try {
            // Create cluster item
            val clusterItem =
                ClusterMarkerItem(markerId, position, "Photo: $markerId", "", photoUri = photoUri, photoUrl = photoUrl)

            // Add to cluster manager
            clusterManager.addItem(clusterItem)
            vm.addClusterItem(markerId, clusterItem)
        } catch (e: Exception) {
            Log.e("TripFragment", "Error creating photo cluster item", e)
        }
    }

    companion object {
        const val RESULT_COORDINATES = "coordinates"
        const val KEY_MEMORY_VIEWER = "memory_viewer"
    }
}