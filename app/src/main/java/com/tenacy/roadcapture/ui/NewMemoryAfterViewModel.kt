package com.tenacy.roadcapture.ui

import android.location.Location
import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.LocationEntity
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.db.MemoryEntity
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.ui.dto.Address
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.toKotlinDuration

@HiltViewModel
class NewMemoryAfterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resourceProvider: ResourceProvider,
    private val memoryDao: MemoryDao,
    private val locationDao: LocationDao,
): BaseViewModel() {

    private val args =  NewMemoryAfterFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val photoUri: Uri = args.photoUri
    private val coordinates: Location = args.coordinates
    private val placeName: String = args.placeName
    private val content: String = args.content

    private val _readyState = MutableSharedFlow<SaveMemoryState>()
    val readyState = _readyState.asSharedFlow()

    init {
        saveMemory()
    }

    private fun saveMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            channelFlow {
                sendWithDelay(SaveMemoryState.Loading)

                var locationId: Long? = null
                var memoryId: Long? = null
                var userCountUpdated = false

                try {
                    // 1. Firebase 업데이트
                    sendWithDelay(SaveMemoryState.CountMemory)
                    updateFirebaseUserCount()
                    userCountUpdated = true

                    // 2. 위치 정보 얻기
                    sendWithDelay(SaveMemoryState.ReversingGeocoding)
                    val address = getAddressFromCoordinates()

                    // 3. 로컬 DB에 위치 저장
                    sendWithDelay(SaveMemoryState.SavingMemory, duration = 1500L)
                    locationId = saveLocationToLocal(address)

                    // 4. 로컬 DB에 메모리 저장
                    memoryId = saveMemoryToLocal(address, locationId)

                    // 5. 썸네일 설정
                    if(TravelPref.thumbnailMemoryId == null) {
                        TravelPref.thumbnailMemoryId = memoryId
                    }

                    sendWithDelay(SaveMemoryState.Completed(memoryId))

                } catch (exception: Exception) {
                    // 롤백 수행
                    rollbackChanges(locationId, memoryId, userCountUpdated)
                    throw exception
                }
            }
                .timeout(Duration.ofMillis(30 * Constants.MILLIS_PER_SECONDS).toKotlinDuration())
                .catch { exception ->
                    if(exception is TimeoutCancellationException) {
                        emit(SaveMemoryState.Error(resourceProvider.getString(R.string.try_again)))
                    } else {
                        emit(SaveMemoryState.Error(exception.message ?: resourceProvider.getString(R.string.general_error)))
                    }
                }
                .collect { state ->
                    _readyState.emit(state)
                }
        }
    }

    private suspend fun getAddressFromCoordinates(): Address {
        val excludePatterns = listOf("ISO", "country_code")
        return try {
            val language = resourceProvider.getConfigurationContext().resources.configuration.locale.language
            val nominatimReverseResponse = RetrofitInstance.locationIqApi.reverse(
                apiKey = BuildConfig.LOCATION_IQ_ACCESS_TOKEN,
                lat = coordinates.latitude,
                lon = coordinates.longitude,
                language = "$language,en",
            )
            Address(
                country = nominatimReverseResponse.address?.country,
                formattedAddress = nominatimReverseResponse.displayName,
                components = nominatimReverseResponse.address?.otherFields?.entries
                    ?.filter { (key, value) ->
                        !excludePatterns.any { pattern -> key.contains(pattern, ignoreCase = true) }
                                && (!value.containsDigit() || value.containsLetter())
                    }
                    ?.map { it.value }
                    ?.toList()
                    ?.distinct()
                    ?.reversed() ?: throw Exception(),
                coordinates = coordinates,
            )
        } catch (exception: Exception) {
            throw RuntimeException(resourceProvider.getString(R.string.location_loading_error))
        }
    }

    private fun saveLocationToLocal(address: Address): Long {
        val locationEntity = LocationEntity(
            coordinates = address.coordinates,
            createdAt = LocalDateTime.now(),
        )
        return locationDao.insert(locationEntity)
    }

    private suspend fun saveMemoryToLocal(address: Address, locationId: Long): Long {
        val addressTags = listOfNotNull(address.country) + address.components
        val memoryEntity = MemoryEntity(
            placeName = placeName.takeIf { it.isNotBlank() },
            content = content.takeIf { it.isNotBlank() },
            photoUri = photoUri,
            addressTags = addressTags,
            formattedAddress = address.formattedAddress ?: "",
            locationId = locationId,
            createdAt = LocalDateTime.now(),
        )
        return memoryDao.insert(memoryEntity)
    }

    private suspend fun updateFirebaseUserCount() = suspendCancellableCoroutine<Unit> { continuation ->
        val userId = UserPref.id
        val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)

        userRef.update("todayMemoryCount", FieldValue.increment(1))
            .addOnSuccessListener {
                UserPref.todayMemoryCount += 1
                continuation.resumeWith(Result.success(Unit))
            }
            .addOnFailureListener { exception -> continuation.resumeWith(Result.failure(exception)) }
    }

    private suspend fun rollbackChanges(locationId: Long?, memoryId: Long?, userCountUpdated: Boolean) {
        try {
            // 메모리 삭제
            memoryId?.let { memoryDao.deleteById(it) }

            // 위치 삭제
            locationId?.let { locationDao.deleteById(it) }

            // Firebase 카운트 롤백
            if (userCountUpdated) {
                val userId = UserPref.id
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                suspendCancellableCoroutine<Unit> { continuation ->
                    userRef.update("todayMemoryCount", FieldValue.increment(-1))
                        .addOnCompleteListener {
                            UserPref.todayMemoryCount -= 1
                            continuation.resumeWith(Result.success(Unit))
                        }
                }
            }
        } catch (rollbackException: Exception) {
            // 롤백 실패 시 로그만 남김
            println("롤백 실패: ${rollbackException.message}")
        }
    }
}

@Parcelize
sealed class SaveMemoryState : Parcelable {
    data object Loading : SaveMemoryState()
    data object CountMemory : SaveMemoryState()
    data object ReversingGeocoding : SaveMemoryState()
    data object SavingMemory : SaveMemoryState()
    data class Completed(val memoryId: Long) : SaveMemoryState()
    data class Error(val message: String) : SaveMemoryState()
}