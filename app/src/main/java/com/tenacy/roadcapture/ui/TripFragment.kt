package com.tenacy.roadcapture.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.billingclient.api.Purchase
import com.facebook.FacebookSdk.setCacheDir
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.clustering.ClusterManager
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.databinding.FragmentTripBinding
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.manager.SubscriptionManager.SubscriptionPurchaseCallback
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.ui.dto.MemoryViewerArguments
import com.tenacy.roadcapture.util.*
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class TripFragment : BaseFragment(), OnMapReadyCallback, ClusterManager.OnClusterItemClickListener<ClusterMarkerItem>, SubscriptionPurchaseCallback {

    // ===== 1. 속성(Property) 그룹 =====
    private var _binding: FragmentTripBinding? = null
    val binding get() = _binding!!

    val vm: TripViewModel by viewModels()

    private lateinit var map: GoogleMap

    private lateinit var clusterManager: ClusterManager<ClusterMarkerItem>

    private var isClusterManagerInitialized = false

    private val mapReady = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(false)

    @Inject
    lateinit var subscriptionManager: SubscriptionManager

    // ===== 2. 권한 처리 관련 리스너 그룹 =====
    private val cameraPermissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
            repeatOnLifecycle(lifecycleState = Lifecycle.State.CREATED) {
                Log.d("TAG", "카메라 권한 허용됨")
                checkLocationPermission()
            }
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
            repeatOnLifecycle(lifecycleState = Lifecycle.State.CREATED) {
                Log.d("TAG", "카메라 권한 거부됨")
                vm.stopTraveling()
                mainActivity.vm.viewEvent(
                    GlobalViewEvent.Toast(
                        ToastModel(
                            "카메라 권한이 없어\n앨범을 만들 수 없습니다",
                            ToastMessageType.Warning
                        )
                    )
                )
            }
        }
    }

    private val locationPermissionListener = object : PermissionListener {
        @SuppressLint("MissingPermission")
        override fun onPermissionGranted() {
            repeatOnLifecycle(lifecycleState = Lifecycle.State.CREATED) {
                Log.d("TAG", "위치 권한 허용됨")

                permissionGranted.value = true

                if (!vm.initialGuideShown) {
                    vm.startTraveling()
                    showGuideDialog()
                    vm.initialGuideShown = true
                }
            }
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
            repeatOnLifecycle(lifecycleState = Lifecycle.State.CREATED) {
                Log.d("TAG", "위치 권한 거부됨")
                vm.stopTraveling()
                mainActivity.vm.viewEvent(
                    GlobalViewEvent.Toast(
                        ToastModel(
                            "위치 권한이 없어\n앨범을 만들 수 없습니다",
                            ToastMessageType.Warning
                        )
                    )
                )
            }
        }
    }

    // ===== 3. 라이프사이클 메서드 그룹 =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFragmentResultListeners()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTripBinding.inflate(inflater, container, false)
        binding.vm = vm
        binding.lifecycleOwner = this
        return binding.root
    }

    @Inject
    lateinit var locationDummyGenerator: LocationDummyGenerator

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetInitializationState()

        repeatOnLifecycle { vm.fetchData() }

        setupViews()
        setupPermissions()
        setupObservers()
        setupListeners()
/*
        binding.abcdefg.setQuickTapListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "더미 경로 데이터 생성 중...", Toast.LENGTH_SHORT).show()
                    }

                    val startTime = System.currentTimeMillis()

                    // 10개 경로, 각 300포인트 = 총 3000개 포인트 생성
                    val pathsList = locationDummyGenerator.generateDummyPaths(
                        count = 10, // 10개 경로
                        pathCount = 300 // 각 경로당 300개 포인트
                    )

                    val totalPoints = pathsList.sumOf { it.size }
                    val endTime = System.currentTimeMillis()
                    val duration = (endTime - startTime) / 1000.0

                    withContext(Dispatchers.Main) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "${pathsList.size}개 경로, 총 ${totalPoints}개 포인트 생성 완료 (${duration}초 소요)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "더미 경로 데이터 생성 실패", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "경로 데이터 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
*/
    }

    override fun onDestroyView() {
        super.onDestroyView()
        subscriptionManager.setPurchaseCallback(null)
        _binding = null
    }

    // ===== 4. 인터페이스 구현 메서드 그룹 =====
    override fun onMapReady(map: GoogleMap) {
        this@TripFragment.map = map
        mapReady.value = true
    }

    override fun onClusterItemClick(item: ClusterMarkerItem): Boolean {
        try {
            navigateToModifiableMemoryViewer(item)
            return true // 이벤트 소비
        } catch (e: Exception) {
            Log.e("TripFragment", "마커 클릭 처리 오류", e)
            return false
        }
    }

    override fun onSubscriptionPurchaseCompleted(purchase: Purchase) {
        val bottomSheet = SubscribeAfterBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, SubscribeAfterBottomSheetFragment.TAG)
    }

    override fun onSubscriptionPurchaseFailed(errorCode: Int, errorMessage: String) {

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
                        navigateToModifiableMemoryViewer(it.items)
                    } else {
                        it.items.getOrNull(0)?.let { navigateToModifiableMemoryViewer(it) }
                    }
                }
        }
        childFragmentManager.setFragmentResultListener(
            TripGuideBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<TripGuideBottomSheetFragment.ParamsOut.ShowSubscription>(TripGuideBottomSheetFragment.KEY_PARAMS_OUT_SHOW_SUBSCRIPTION)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                val linkedAccountExists = SubscriptionPref.linkedAccountExists
                when {
                    linkedAccountExists -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("다른 계정에서 이미 혜택을 받고 있어요", ToastMessageType.Info)))
                    }
                    else -> {
                        showSubscriptionDialog()
                    }
                }
            }
        }
        childFragmentManager.setFragmentResultListener(
            SubscriptionBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<SubscriptionBottomSheetFragment.ParamsOut.Positive>(SubscriptionBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                // 정기구독
                subscriptionManager.setPurchaseCallback(this)
                subscriptionManager.subscribe(mainActivity)
            }
        }
        childFragmentManager.setFragmentResultListener(
            TripAfterBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<TripAfterBottomSheetFragment.ParamsOut.Positive>(TripAfterBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                findNavController().navigate(TripFragmentDirections.actionTripToNewAlbum())
            }
        }
        childFragmentManager.setFragmentResultListener(
            TripStopBeforeBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<TripStopBeforeBottomSheetFragment.ParamsOut.Positive>(TripStopBeforeBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE)?.let {
                Log.d("TAG", "Positive Button Clicked!")
                viewLifecycleOwner.lifecycleScope.launch {
                    vm.stopTraveling()
                    mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("앨범을 삭제했어요", ToastMessageType.Success)))
                }
            }
        }
    }

    private fun setupViews() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupListeners() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(1000L)
                binding.fabTripCapture.setSafeClickListener {
                    val location = getCurrentLocation()
                    if (location == null) {
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(
                                    "위치 정보가 없기 때문에 사진을 찍을 수 없어요",
                                    ToastMessageType.Warning
                                )
                            )
                        )
                        return@setSafeClickListener
                    }
                    captureImageWithCameraApp()
                }
            }
            binding.fabTripCapture.setOnClickListener(null)
        }
    }

    private fun setupObservers() {
        observeViewEvents()
        observeSavedState()
        observeInitializationState()
    }

    @SuppressLint("MissingPermission")
    private fun setupMaps() {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.isMyLocationEnabled = true

        setupLocationUpdates()
    }

    private fun setupClusterManager() {
        if (isClusterManagerInitialized) return

        // 클러스터 매니저 초기화
        clusterManager = ClusterManager(requireContext(), map)

        // 클러스터 매니저 옵션 설정
        clusterManager.setAnimation(true)  // 클러스터 애니메이션 활성화

        // 커스텀 렌더러 생성 및 적용
        clusterManager.renderer = MarkerClusterRenderer(this@TripFragment, map, clusterManager)

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
            val paddingTop = 169.toPx // 패딩 값
            val paddingBottom = 133.toPx
            val paddingStart = 92.toPx
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

    // TripFragment.kt의 setupLocationUpdates() 함수 수정
    private fun setupLocationUpdates() {
        // 위치 업데이트 시작
        repeatOnLifecycle {
            // FusedLocationProviderClient 생성
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

            // LocationRequest 설정 (서비스와 동일하게)
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                Constants.TRACKING_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(Constants.TRACKING_INTERVAL)
                setIntervalMillis(Constants.TRACKING_INTERVAL)
                setWaitForAccurateLocation(true)
                setMinUpdateDistanceMeters(Constants.MIN_DISTANCE_TO_SAVE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                }
                setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            }.build()

            // 위치 콜백 설정
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // 가장 정확한 위치 선택
                    var bestLocation: Location? = null
                    for (location in locationResult.locations) {
                        if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                            bestLocation = location
                        }
                    }

                    // 최종 선택된 위치 처리
                    bestLocation?.let(vm::saveLocation)
                }
            }

            try {
                // 위치 업데이트 시작
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )

                Log.d("TripFragment", "위치 업데이트 시작됨")

                // STARTED 상태를 벗어날 때까지 유지
                try {
                    awaitCancellation() // STARTED 상태가 끝날 때까지 기다림
                } finally {
                    // 위치 업데이트 중지 (STARTED 상태를 벗어날 때)
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    Log.d("TripFragment", "위치 업데이트 중지됨")
                }
            } catch (e: SecurityException) {
                Log.e("TripFragment", "위치 권한이 없습니다", e)
            } catch (e: Exception) {
                Log.e("TripFragment", "위치 업데이트 시작 실패", e)
            }
        }

        // 위치 업데이트와 별도로 여행 기간 업데이트
        repeatOnLifecycle {
            while (isActive) {
                vm.updateDurationText()
                delay(60 * 1000)
            }
        }
    }

    private fun setupPermissions() {
        checkCameraPermission()
    }

    // ===== 6. 권한 처리 메서드 그룹 =====
    private fun checkLocationPermission() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Android 10 이상에서는 백그라운드 위치 권한도 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        TedPermission.create()
            .setPermissionListener(locationPermissionListener)
            .setPermissions(*permissions.toTypedArray())
            .setGotoSettingButton(true)
            .setDeniedTitle("위치 권한 필요")
            .setDeniedMessage("지도에 현재 위치를 표시하고 백그라운드에서 경로를 기록하기 위해 위치 권한이 필요합니다. 설정에서 위치 권한을 항상 허용으로 선택해주세요.")
            .setDeniedCloseButtonText("취소")
            .setGotoSettingButtonText("설정")
            .check()
    }

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
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle

        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_NEW_MEMORY) { bundle ->
                if (bundle == null) return@consumeOnce
                val memoryId = bundle.getLong(RESULT_MEMORY_ID)
                val coordinates = bundle.getParcelable<Location>(RESULT_COORDINATES)
                coordinates?.let { vm.saveLocation(it, false) }
            }
        }
        repeatOnLifecycle(lifecycleState = Lifecycle.State.RESUMED) {
            savedStateHandle?.consumeOnce<Bundle?>(KEY_MODIFIABLE_MEMORY_VIEWER) { bundle ->
                if (bundle == null) return@consumeOnce
                val coordinates = bundle.getParcelable<Location?>(RESULT_COORDINATES)
                val removed = bundle.getBoolean(RESULT_MEMORY_DELETED, false)
                vm.viewEvent(TripViewEvent.SetCamera(coordinates))
            }
        }
    }

    private fun observeViewEvents() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? TripViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun observeInitializationState() {
        repeatOnLifecycle {
            mapReady.combine(permissionGranted) { mapReady, permissionGranted ->
                mapReady && permissionGranted
            }.collectLatest { isReady ->
                if (isReady) {
                    observeData()
                    setupMaps()
                    setupClusterManager()
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

            is TripViewEvent.SetCamera -> {
                moveCameraTo(location = event.coordinates, zoom = event.zoom ?: map.cameraPosition.zoom)
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
                showGuideDialog()
            }

            is TripViewEvent.ShowSubscription -> {
                val isSubscriptionActive = SubscriptionPref.isSubscriptionActive
                val linkedAccountExists = SubscriptionPref.linkedAccountExists
                when {
                    linkedAccountExists -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("다른 계정에서 이미 혜택을 받고 있어요", ToastMessageType.Info)))
                    }
                    isSubscriptionActive -> {
                        mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("프리미엄 플랜을 구독 중이에요", ToastMessageType.Info)))
                    }
                    else -> {
                        showSubscriptionDialog()
                    }
                }
            }

            is TripViewEvent.ShowAfter -> {
                showTripAfterDialog()
            }

            is TripViewEvent.ShowStopBefore -> {
                showTripStopBeforeDialog()
            }
            
            is TripViewEvent.Error -> {
                when(event) {
                    is TripViewEvent.Error.Fetch -> {}
                }
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("문제가 발생했어요", ToastMessageType.Warning)))
                findNavController().popBackStack()
            }
        }
    }

    private fun navigateToModifiableMemoryViewer(item: ClusterMarkerItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val memories = vm.getMemories()
            val selectedMemoryId = vm.getMemoryIdBy(item)
            val memoryViewerArguments = MemoryViewerArguments(
                selectedMemoryId = selectedMemoryId,
                viewScope = ViewScope.WHOLE,
                memories = memories.map(MemoryViewerArguments.Memory::of),
            )
            findNavController().navigate(TripFragmentDirections.actionTripToModifiableMemoryViewer(memoryViewerArguments))
        }
    }

    private fun navigateToModifiableMemoryViewer(items: List<ClusterMarkerItem>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val memories = vm.getMemoriesIn(items)
            val memoryViewerArguments = MemoryViewerArguments(
                viewScope = ViewScope.AROUND,
                memories = memories.map(MemoryViewerArguments.Memory::of),
            )
            findNavController().navigate(TripFragmentDirections.actionTripToModifiableMemoryViewer(memoryViewerArguments))
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

    private fun showGuideDialog() {
        val bottomSheet = TripGuideBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, TripGuideBottomSheetFragment.TAG)
    }

    private fun showSubscriptionDialog() {
        val bottomSheet = SubscriptionBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, SubscriptionBottomSheetFragment.TAG)
    }

    private fun showTripAfterDialog() {
        val bottomSheet = TripAfterBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, TripAfterBottomSheetFragment.TAG)
    }

    private fun showTripStopBeforeDialog() {
        val bottomSheet = TripStopBeforeBottomSheetFragment.newInstance()
        bottomSheet.show(childFragmentManager, TripStopBeforeBottomSheetFragment.TAG)
    }

    // ===== 9. 지도 컨트롤 메서드 그룹 =====
    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(): Location? {
        if (!::map.isInitialized) return null

        val locationManager = mainActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val criteria = Criteria()
        val provider = locationManager.getBestProvider(criteria, true)
        val location = provider?.let(locationManager::getLastKnownLocation)

        return location
    }

    @SuppressLint("MissingPermission")
    private fun moveCameraTo(location: Location? = null, zoom: Float = map.cameraPosition.zoom) {
        (location ?: getCurrentLocation())?.let {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), zoom))
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
                    createPhotoClusterItem(markerId, position, location.photo.photoUri)
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
                ClusterMarkerItem(markerId, position, "사진: $markerId", "", photoUri = photoUri, photoUrl = photoUrl)

            // Add to cluster manager
            clusterManager.addItem(clusterItem)
            vm.addClusterItem(markerId, clusterItem)
        } catch (e: Exception) {
            Log.e("TripFragment", "Error creating photo cluster item", e)
        }
    }

    // ===== 12. 카메라 처리 메서드 그룹 =====
    // 임시 이미지 파일과 Uri를 저장할 변수
    private var tempPhotoFile: File? = null
    private var tempPhotoUri: Uri? = null

    private fun captureImageWithCameraApp() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                tempPhotoFile = createTempImageFile()

                tempPhotoUri = getUriForFile(tempPhotoFile!!)

                withContext(Dispatchers.Main) {
                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri)
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                    try {
                        cameraLauncher.launch(takePictureIntent)
                    } catch (e: Exception) {
                        Log.e("TripFragment", "카메라 앱 실행 오류", e)
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(
                                    "카메라 앱을 실행할 수 없어요",
                                    ToastMessageType.Warning
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TripFragment", "임시 파일 생성 오류", e)
            }
        }
    }

    // 내부 저장소에 임시 이미지 파일 생성
    private fun createTempImageFile(): File {
        val timeStamp = LocalDateTime.now().toString().replace(":", "-")

        // 외부 저장소 대신 내부 캐시 디렉토리 사용
        val storageDir = requireContext().cacheDir
        Log.d("TripFragment", "저장 디렉토리 경로: ${storageDir?.absolutePath}")

        val file = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
        Log.d("TripFragment", "임시 파일 경로: ${file.absolutePath}")
        return file
    }

    private fun getUriForFile(file: File): Uri {
        try {
            return FileProvider.getUriForFile(
                requireContext(),
                requireContext().fileProviderAuthority,
                file
            )
        } catch (e: IllegalArgumentException) {
            Log.e("TripFragment", "FileProvider 오류: ${e.message}", e)
            Log.e("TripFragment", "파일 경로: ${file.absolutePath}")
            Log.e("TripFragment", "파일 존재 여부: ${file.exists()}")
            Log.e("TripFragment", "권한: ${requireContext().fileProviderAuthority}")

            throw e
        }
    }

    // 카메라 앱에서 돌아왔을 때의 결과 처리
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                tempPhotoUri?.let { sourceUri ->
                    try {
                        // 내부 캐시 디렉토리에 크롭된 이미지를 저장할 파일 생성
                        val croppedFile = createTempImageFile()
                        val croppedUri = getUriForFile(croppedFile)

                        // uCrop 시작
                        cropLauncher.launch(Triple(sourceUri, croppedUri, getCurrentLocation()))
                    } catch (e: Exception) {
                        Log.e("TripFragment", "uCrop 시작 오류", e)
                        e.printStackTrace()
                        mainActivity.vm.viewEvent(
                            GlobalViewEvent.Toast(
                                ToastModel(
                                    "이미지 편집을 시작할 수 없습니다",
                                    ToastMessageType.Warning
                                )
                            )
                        )
                    }
                } ?: run {
                    Log.e("TripFragment", "카메라 결과 처리 오류: 소스 Uri가 null입니다")
                }
            }
        }
    }

    // Triple<소스Uri, 대상Uri, 위치> 형태로 데이터 전달
    private val cropLauncher =
        registerForActivityResult(object : ActivityResultContract<Triple<Uri, Uri, Location?>, Pair<Uri?, Location?>>() {
            override fun createIntent(context: Context, input: Triple<Uri, Uri, Location?>): Intent {
                val options = UCrop.Options().apply {
                    setCompressionQuality(30)
                    setToolbarTitle("이미지 편집")
                    setToolbarColor(ContextCompat.getColor(context, R.color.label_normal))
                    setStatusBarColor(ContextCompat.getColor(context, R.color.label_normal))
                    setToolbarWidgetColor(ContextCompat.getColor(context, R.color.background_normal))
                    setRootViewBackgroundColor(ContextCompat.getColor(context, R.color.label_normal))
                    // 저장 경로를 캐시 디렉토리로 설정
                    setCacheDir(context.cacheDir)
                }

                return UCrop.of(input.first, input.second)
                    .withAspectRatio(1f, 1f)
                    .withOptions(options)
                    .getIntent(context)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Pair<Uri?, Location?> {
                return if (resultCode == Activity.RESULT_OK && intent != null) {
                    val uri = UCrop.getOutput(intent)
                    Pair(uri, getCurrentLocation())
                } else {
                    if (resultCode == UCrop.RESULT_ERROR && intent != null) {
                        val error = UCrop.getError(intent)
                        Log.e("TripFragment", "uCrop 오류", error)
                    }
                    Pair(null, null)
                }
            }
        }) { result ->
            tempPhotoFile?.delete()
            tempPhotoFile = null
            tempPhotoUri = null

            val (uri, coordinates) = result

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                if (uri == null) {
                    mainActivity.vm.viewEvent(
                        GlobalViewEvent.Toast(
                            ToastModel(
                                "이미지 편집 중 오류가 발생했습니다",
                                ToastMessageType.Warning
                            )
                        )
                    )
                    return@launch
                }

                if (coordinates == null) {
                    mainActivity.vm.viewEvent(
                        GlobalViewEvent.Toast(
                            ToastModel(
                                "현재 위치를 확인할 수 없어요",
                                ToastMessageType.Warning
                            )
                        )
                    )
                    return@launch
                }

                findNavController().navigate(TripFragmentDirections.actionTripToLoading(uri, coordinates))
            }
        }

    // ===== 13. 상태 초기화 메서드 그룹 =====
    private fun resetInitializationState() {
        mapReady.value = false
        permissionGranted.value = false
    }

    // ===== 14. 데이터 클래스 및 상수 정의 그룹 =====
    companion object {
        const val KEY_NEW_MEMORY = "new_memory"
        const val KEY_MODIFIABLE_MEMORY_VIEWER = "modifiable_memory_viewer"
        const val RESULT_MEMORY_ID = "memory_id"
        const val RESULT_COORDINATES = "coordinates"
        const val RESULT_MEMORY_DELETED = "memory_deleted"
    }
}