package com.tenacy.roadcapture.ui

import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.BuildConfig
import com.tenacy.roadcapture.manager.FreepikNSFWDetector
import com.tenacy.roadcapture.ui.dto.Address
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.Duration
import javax.inject.Inject
import kotlin.time.toKotlinDuration

@HiltViewModel
class NewMemoryBeforeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val freepikNSFWDetector: FreepikNSFWDetector,
): BaseViewModel() {

    private val photoUri: Uri = NewMemoryBeforeFragmentArgs.fromSavedStateHandle(savedStateHandle).photoUri
    private val coordinates: Location = NewMemoryBeforeFragmentArgs.fromSavedStateHandle(savedStateHandle).coordinates

    private val _readyState = MutableSharedFlow<MemoryReadyState>()
    val readyState = _readyState.asSharedFlow()

    init {
        ready()
    }

    private fun ready() {
        viewModelScope.launch(Dispatchers.IO) {
            channelFlow {
                sendWithDelay(MemoryReadyState.Loading)

                // 1. 이미지 압축하기
                sendWithDelay(MemoryReadyState.ProcessingImage)
                val (compressedUri, bitmap) = try {
                    val compressedUri = context.compressImage(photoUri)
                    val bitmap = photoUri.toBitmap(context)!!
                    compressedUri to bitmap
                } catch (exception: Exception) {
                    throw RuntimeException("이미지 처리 중에 문제가 발생했어요", exception)
                }

                // 2. NSFW 감지하기
                sendWithDelay(MemoryReadyState.DetectingNsfw)
                val isNSFW = freepikNSFWDetector.detectNSFW(bitmap).isNSFW
                if(isNSFW) {
                    throw RuntimeException("부적절한 컨텐츠를 감지했어요\n다시 촬영해주세요")
                }
                
                // 3. 위치 정보 얻기
                sendWithDelay(MemoryReadyState.FetchingAddress)
                val excludePatterns = listOf("ISO", "country_code")

                val address = try {
                    val nominatimReverseResponse = RetrofitInstance.locationIqApi.reverse(
                        apiKey = BuildConfig.LOCATION_IQ_ACCESS_TOKEN,
                        lat = coordinates.latitude,
                        lon = coordinates.longitude,
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
                    throw RuntimeException("위치 정보를 불러오는 중에\n문제가 발생했어요")
                }

                sendWithDelay(MemoryReadyState.Completed(compressedUri, address))
            }
                .timeout(Duration.ofMillis(30 * Constants.MILLIS_PER_SECONDS).toKotlinDuration())
                .catch { exception ->
                    if(exception is TimeoutCancellationException) {
                        emit(MemoryReadyState.Error("다시 시도해주세요"))
                    } else {
                        emit(MemoryReadyState.Error(exception.message ?: "문제가 발생했어요"))
                    }
                }
                .collect { state ->
                    _readyState.emit(state)
                }
        }
    }
}

@Parcelize
sealed class MemoryReadyState : Parcelable {
    data object Loading : MemoryReadyState()
    data object ProcessingImage : MemoryReadyState()
    data object DetectingNsfw : MemoryReadyState()
    data object FetchingAddress : MemoryReadyState()
    data class Completed(val photoUri: Uri, val address: Address) : MemoryReadyState()
    data class Error(val message: String) : MemoryReadyState()
}