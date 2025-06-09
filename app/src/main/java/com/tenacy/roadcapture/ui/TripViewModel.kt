package com.tenacy.roadcapture.ui

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.Polyline
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryWithLocation
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.data.pref.UserPref.todayMemoryCount
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.service.LocationProcessor
import com.tenacy.roadcapture.ui.dto.Marker
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class TripViewModel @Inject constructor(
    subscriptionManager: SubscriptionManager,
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val memoryDao: MemoryDao,
    private val travelingStateManager: TravelingStateManager,
    val locationProcessor: LocationProcessor,
) : BaseViewModel() {

    val isSubscriptionActive: StateFlow<Boolean> = subscriptionManager.isSubscriptionActive
        .map { it && !SubscriptionPref.linkedAccountExists }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SubscriptionPref.isSubscriptionActive
        )

    var initialGuideShown = false

    private val routePolylines = mutableListOf<Polyline>()
    private val clusterItems = mutableMapOf<String, ClusterMarkerItem>()

    private val _locations = MutableStateFlow<List<LocationEntity>>(emptyList())
    private val _memories = MutableStateFlow<List<MemoryWithLocation>?>(null)

    private val _durationText = MutableStateFlow<String?>(null)
    val durationText = _durationText.asStateFlow()

    val saveEnabled = _memories.map {
        val memoryCount = it?.size ?: 0
        memoryCount > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val memoryLoaded = combine(_memories, _durationText) { memories, durationText -> memories != null && durationText != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val memoryCountText = combine(_memories, isSubscriptionActive) { memories, _ ->
        val currentMemorySize = memories?.size ?: 0
        val maxMemorySize = Constants.MEMORY_MAX_SIZE
        "$currentMemorySize / $maxMemorySize"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val exceedLimitedMemorySize = combine(_memories, isSubscriptionActive) { memories, _ ->
        if(memories == null) return@combine false
        memories.size >= Constants.MEMORY_MAX_SIZE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _todayMemoryCount = MutableStateFlow<Long?>(null)

    val todayMemoryCountText = combine(_todayMemoryCount, isSubscriptionActive) { todayMemoryCount, _ ->
        val currentMemorySize = todayMemoryCount ?: 0
        val maxMemorySize = SubscriptionValues.todayMemoryMaxSize
        val `0` = currentMemorySize.toInt()
        val `1` = maxMemorySize
        context.getString(R.string.memory_count, `0`, `1`)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val exceedTodayLimitedMemorySize = combine(_todayMemoryCount, isSubscriptionActive) { todayMemoryCount, _ ->
        if(todayMemoryCount == null) return@combine false
        todayMemoryCount >= SubscriptionValues.todayMemoryMaxSize
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val markers = combine(_locations, _memories) { locations, memories ->
        val memoryByLocationId = memories?.associateBy { it.location.id } ?: emptyMap()

        locations.map { location ->
            val memory = memoryByLocationId[location.id]
            if (memory != null) {
                Marker.of(memory)
            } else {
                Marker.of(location)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val routePoints = _locations.map { locations ->
        locations.sortedBy { it.createdAt }
            .map { it.coordinates }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun fetchData() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val todayMemoryCount = if (_todayMemoryCount.value == null) {
                    val userId = UserPref.id
                    val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                    userRef.get().await().toUser().todayMemoryCount.also { UserPref.todayMemoryCount = it }
                } else {
                    UserPref.todayMemoryCount
                }

                _todayMemoryCount.update { todayMemoryCount }

                val locations = locationDao.selectAll()
                val memories = memoryDao.selectAll()
                // 마지막 저장 위치 설정
                if (locations.isNotEmpty()) {
                    val last = locations.maxByOrNull { it.createdAt }
                    last?.let {
                        val coordinates = it.coordinates
                        TravelPref.setLastSavedLocation(coordinates)
                    }
                }
                emit(memories to locations)
            }
                .catch { exception ->
                    Log.e("TripViewModel", "에러", exception)
                    viewEvent(TripViewEvent.Error.Fetch(exception.message))
                }
                .collect { (memories, locations) ->
                    _memories.emit(memories)
                    _locations.emit(locations)
                }
        }
    }

    fun getMemories(): List<MemoryWithLocation> = _memories.value ?: emptyList()

    fun getMemoriesIn(items: List<ClusterMarkerItem>): List<MemoryWithLocation> {
        val currentMemories = _memories.value ?: emptyList()
        val associateBy = currentMemories.associateBy { it.location.id.toString() }
        return items.mapNotNull { associateBy[it.id] }
    }

    fun getMemoryIdBy(item: ClusterMarkerItem): String {
        val currentMemories = _memories.value ?: emptyList()
        val memoryIdsByLocationId = currentMemories.associate { it.location.id.toString() to it.memory.id }
        return memoryIdsByLocationId[item.id]!!.toString()
    }

    fun startTraveling() {
        // 여행 최초 시점이라면 서비스에 영향 가능성이 있는 위치 정보 초기화
        if(!TravelPref.isTraveling) {
            viewModelScope.launch(Dispatchers.IO) {
                memoryDao.clear()
                locationDao.clear()
            }
        }

        // TravelStatePref.startTravel()이 내부적으로 createdAt(travelStartedAt)을 설정함
        travelingStateManager.startTraveling()

        updateDurationText()
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.SetCamera(zoom = 30f))
        }
    }


    fun stopTraveling() {
        travelingStateManager.stopTraveling()

        viewModelScope.launch(Dispatchers.IO) {
            TravelPref.clear()
            // clearLocations 메서드가 현재 LocationProcessor에는 없지만,
            // 필요하다면 추가할 수 있습니다. 아니면 기존 코드를 유지해도 됩니다.
            memoryDao.clear()
            locationDao.clear()
            context.clearCacheDirectory()

            viewEvent(TripViewEvent.Back)
        }
    }

    fun updateDurationText() {
        _durationText.update {
            val (days, hours, minutes) = getDurationFormattedText(TravelPref.createdAt, LocalDateTime.now().toTimestamp())
            val sb = StringBuilder()

            if(days > 0) {
                val `0` = days.toInt()
                sb.append(context.getString(R.string.time_days, `0`))
            }

            if(hours > 0) {
                val `0` = hours.toInt()
                sb.append(context.getString(R.string.time_hours, `0`))
            }

            if(minutes >= 0) {
                val `0` = minutes.toInt()
                sb.append(context.getString(R.string.time_minutes, `0`))
            }

            sb.toString()
        }
    }

    fun saveLocation(location: Location, insertable: Boolean = true) {
        if (!insertable) {
            TravelPref.setLastSavedLocation(location)
            return
        }

        if (!locationProcessor.isLocationQualityAcceptable(location)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // LocationProcessor에 위치 처리 요청
            val savedEntity = locationProcessor.processLocation(location)

            // 저장 성공 시 직접 _locations 업데이트
            if (savedEntity != null) {
                _locations.update { currentList ->
                    currentList.toMutableList().apply {
                        add(savedEntity)
                    }
                }
            }
        }
    }

    fun clearRoutePolylines() {
        routePolylines.forEach { it.remove() }
        routePolylines.clear()
    }

    fun addRoutePolyline(polyline: Polyline) {
        routePolylines.add(polyline)
    }

    fun getMarkerIds() = clusterItems.keys.toSet()

    fun containsMarkerId(markerId: String) = markerId in clusterItems

    fun getClusterItem(markerId: String) = clusterItems[markerId]

    fun addClusterItem(markerId: String, clusterItem: ClusterMarkerItem) {
        clusterItems[markerId] = clusterItem
    }

    fun removeClusterItem(markerId: String) {
        clusterItems.remove(markerId)
    }

    fun onResetCameraPositionClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ResetCameraPosition)
        }
    }

    fun onResetCameraClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.SetCamera())
        }
    }

    fun onZoomInClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ZoomIn)
        }
    }

    fun onZoomOutClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ZoomOut)
        }
    }

    fun onDurationClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowGuide)
        }
    }

    fun onMemoryCountClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowSubscription)
        }
    }

    fun onCheckClick() {
        // 한 달이 지나면 여행 중단 (워커로도 확인 필요)
        if(TravelPref.isOverOneMonth()) {
            stopTraveling()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowAfter)
        }
    }

    fun onDeleteClick() {
        viewModelScope.launch(Dispatchers.Default) {
            viewEvent(TripViewEvent.ShowStopBefore)
        }
    }
}