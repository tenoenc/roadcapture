package com.tenacy.roadcapture.ui

import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Parcelable
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.manager.FreepikNSFWDetector
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

    private val _readyState = MutableSharedFlow<NsfwDetectionState>()
    val readyState = _readyState.asSharedFlow()

    init {
        ready()
    }

    private fun ready() {
        viewModelScope.launch(Dispatchers.IO) {
            channelFlow {
                sendWithDelay(NsfwDetectionState.Loading)

                // 1. 이미지 압축하기
                sendWithDelay(NsfwDetectionState.ProcessingImage)
                val (compressedUri, bitmap) = try {
                    val compressedUri = context.compressImage(photoUri)
                    val bitmap = photoUri.toBitmap(context)!!
                    compressedUri to bitmap
                } catch (exception: Exception) {
                    throw RuntimeException(context.getString(R.string.image_processing_error), exception)
                }

                // 2. NSFW 감지하기
                sendWithDelay(NsfwDetectionState.DetectingNsfw)
                val isNSFW = freepikNSFWDetector.detectNSFW(bitmap).isNSFW
                if(isNSFW) {
                    throw RuntimeException(context.getString(R.string.inappropriate_content))
                }

                sendWithDelay(NsfwDetectionState.Completed(compressedUri))
            }
                .timeout(Duration.ofMillis(30 * Constants.MILLIS_PER_SECONDS).toKotlinDuration())
                .catch { exception ->
                    if(exception is TimeoutCancellationException) {
                        emit(NsfwDetectionState.Error(ContextCompat.getString(context, R.string.try_again)))
                    } else {
                        emit(NsfwDetectionState.Error(exception.message ?: context.getString(R.string.general_error)))
                    }
                }
                .collect { state ->
                    _readyState.emit(state)
                }
        }
    }
}

@Parcelize
sealed class NsfwDetectionState : Parcelable {
    data object Loading : NsfwDetectionState()
    data object ProcessingImage : NsfwDetectionState()
    data object DetectingNsfw : NsfwDetectionState()
    data class Completed(val photoUri: Uri) : NsfwDetectionState()
    data class Error(val message: String) : NsfwDetectionState()
}