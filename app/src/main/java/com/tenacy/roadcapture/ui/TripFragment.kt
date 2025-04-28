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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.databinding.FragmentTripBinding
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

@AndroidEntryPoint
class TripFragment: BaseFragment(), OnMapReadyCallback {

    private var _binding: FragmentTripBinding? = null
    val binding get() = _binding!!

    private val vm: TripViewModel by viewModels()

    private lateinit var map: GoogleMap
    private var routePolyline: Polyline? = null
    private val photoMarkers = mutableMapOf<Long, com.google.android.gms.maps.model.Marker>()

    private val cameraPermissionListener = object : PermissionListener {

        override fun onPermissionGranted() {
            Log.d("TAG", "카메라 권한 허용됨")
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
            Log.d("TAG", "카메라 권한 거부됨")
            lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("카메라 권한이 없어\n앨범을 만들 수 없습니다", ToastMessageType.Warning)))
                findNavController().popBackStack()
            }
        }
    }

    private val locationPermissionListener = object : PermissionListener {

        @SuppressLint("MissingPermission")
        override fun onPermissionGranted() {
            Log.d("TAG", "onPermissionGranted")
            setupMaps()
        }

        override fun onPermissionDenied(p0: MutableList<String>?) {
            Log.d("TAG", "onPermissionDenied")
            lifecycleScope.launch {
                mainActivity.vm.viewEvent(GlobalViewEvent.Toast(ToastModel("위치 권한이 없어\n앨범을 만들 수 없습니다", ToastMessageType.Warning)))
                findNavController().popBackStack()
            }
        }
    }

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
        setupObservers()
        setupPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        routePolyline?.remove()
        photoMarkers.values.forEach { it.remove() }
        photoMarkers.clear()

        _binding = null
    }

    override fun onMapReady(map: GoogleMap) {
        this@TripFragment.map = map

        moveCameraToCurrentLocation(zoom = 30f)
    }

    private fun setupViews() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupObservers() {
        observeViewEvents()
        observeData()
    }

    @SuppressLint("MissingPermission")
    private fun setupMaps() {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.isMyLocationEnabled = true

        vm.routePoints.value?.let { updateRouteOnMap(it) }
        vm.markers.value?.let { updatePhotoMarkers(it) }

        setupLocationUpdates()
    }

    private fun setupLocationUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            while(isActive) {
                getCurrentLocation()?.let { latLng ->
                    vm.saveCurrentLocation(latLng)
                }
                delay(10000)
            }
        }
    }

    private fun observeData() {
        launchOnLifecycle {
            vm.routePoints.collectLatest { points ->
                points?.let(::updateRouteOnMap)
            }
        }
        launchOnLifecycle {
            vm.markers.collectLatest { markers ->
                markers?.let(::updatePhotoMarkers)
            }
        }
    }

    private fun observeViewEvents() {
        launchOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
                    (event as? TripViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private fun handleViewEvents(event: TripViewEvent) {
        when (event) {
            is TripViewEvent.ResetCameraPosition -> {
                // 북쪽 방향으로 맵 회전
                val cameraPosition = CameraPosition.Builder()
                    .target(map.cameraPosition.target)
                    .zoom(map.cameraPosition.zoom)
                    .bearing(0f)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
            is TripViewEvent.ResetCamera -> {
                moveCameraToCurrentLocation()
            }
            is TripViewEvent.Capture -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val location = getCurrentLocation()
                    // Places API에서 Geocoding API로 변경
                    val placeLocation = requireContext().extractLocationData(location) ?: return@launch
                    withContext(Dispatchers.Main) {
                        findNavController().navigate(TripFragmentDirections.actionTripToCamera(placeLocation))
                    }
                }
            }
        }
    }

    private fun setupPermissions() {
        checkCameraPermission()
        checkLocationPermission()
    }

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

    private fun updateRouteOnMap(routePoints: List<LatLng>) {
        if (!::map.isInitialized || routePoints.isEmpty()) return

        // 기존 Polyline이 있으면 제거
        routePolyline?.remove()

        val polylineOptions = PolylineOptions()
            .addAll(routePoints)
            .width(4f.toPx.toFloat())
            .color(ContextCompat.getColor(requireContext(), R.color.primary))
            .geodesic(true) // 지구 곡률 고려

        routePolyline = map.addPolyline(polylineOptions)
    }

    private fun updatePhotoMarkers(markers: List<Marker>) {
        if (!::map.isInitialized) return

        val currentMarkerIds = photoMarkers.keys.toMutableSet()

        // 사진이 있는 위치만 마커로 표시
        for (location in markers) {
            if (location.photo != null) {
                val markerId = location.id
                val position = LatLng(location.latitude, location.longitude)

                if (markerId in photoMarkers) {
                    // 기존 마커가 있으면 위치만 업데이트 (필요시)
                    // photoMarkers[markerId]?.position = position

                    // 이미 처리한 마커 ID 집합에서 제거
                    currentMarkerIds.remove(markerId)
                } else {
                    // 새 마커 추가
                    val markerOptions = MarkerOptions()
                        .position(position)
                        .title("사진: ${location.photo.id}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))

                    val marker = map.addMarker(markerOptions)
                    if (marker != null) {
                        photoMarkers[markerId] = marker
                    }
                }
            }
        }

        // 더 이상 필요 없는 마커 제거
        for (outdatedId in currentMarkerIds) {
            photoMarkers[outdatedId]?.remove()
            photoMarkers.remove(outdatedId)
        }
    }

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
        val name: String,                 // 장소/명소 이름
        val country: String,              // 국가
        val region: String?,              // 지역/주/도
        val city: String?,                // 도시
        val district: String?,            // 구역/동네
        val street: String?,              // 거리/도로
        val detail: String?,              // 상세주소
        val formattedAddress: String,     // 형식화된 전체 주소
        val coordinates: LatLng           // 좌표
    ): Parcelable
}