package com.tenacy.roadcapture.ui

import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.tenacy.roadcapture.ui.TripFragment.Address
import com.tenacy.roadcapture.util.RetrofitInstance
import com.tenacy.roadcapture.util.containsDigit
import com.tenacy.roadcapture.util.containsLetter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class LoadingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
): BaseViewModel() {

    private val photoUri: Uri = LoadingFragmentArgs.fromSavedStateHandle(savedStateHandle).photoUri
    private val coordinates: LatLng = LoadingFragmentArgs.fromSavedStateHandle(savedStateHandle).coordinates

    private val _fetchState = MutableStateFlow<AddressFetchState>(AddressFetchState.Initial)
    val fetchState: StateFlow<AddressFetchState> = _fetchState

    init {
        fetchAddress()
    }

    private fun fetchAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                emit(AddressFetchState.Loading)

                val excludePatterns = listOf("ISO", "country_code")
                val nominatimReverseResponse = RetrofitInstance.nominatimApi.reverse(
                    lat = coordinates.latitude,
                    lon = coordinates.longitude,
                )
                val address = Address(
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
                        ?.reversed() ?: emptyList(),
                    coordinates = coordinates,
                )

                emit(AddressFetchState.Completed(photoUri, address))
            }
                .catch { exception ->
                    emit(AddressFetchState.Error(exception.message ?: "알 수 없는 오류 발생"))
                }
                .collect { state ->
                    _fetchState.value = state
                }
        }
    }
}

@Parcelize
sealed class AddressFetchState : Parcelable {
    data object Initial : AddressFetchState()
    data object Loading : AddressFetchState()
    data class Completed(val photoUri: Uri, val address: Address) : AddressFetchState()
    data class Error(val message: String) : AddressFetchState()
}