package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class ProfileUploadProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {

    private val uri = ProfileUploadProgressFragmentArgs.fromSavedStateHandle(savedStateHandle).uri

    private val _saveState = MutableSharedFlow<ProfileSaveState>()
    val saveState = _saveState.asSharedFlow()

    init {
        updateProfileImage()
    }

    fun updateProfileImage() {
        viewModelScope.launch(Dispatchers.IO) {
            channelFlow {
                sendWithDelay(ProfileSaveState.Loading)
                val userId = auth.currentUser!!.uid
                val userRef = db.collection("users")
                    .document(userId)

                sendWithDelay(ProfileSaveState.FetchingData)
                val albumRefs = db.collection("albums")
                    .whereEqualTo("userRef", userRef).getAllReferences()

                sendWithDelay(ProfileSaveState.CompressingImage)
                val compressedUri = context.compressImage(uri)

                sendWithDelay(ProfileSaveState.UploadingImage)
                val storagePath = "images/users/$userId/profile.jpg"
                val imageUrl = context.uploadImageToStorage(compressedUri, storagePath)

                sendWithDelay(ProfileSaveState.SavingToFirestore)

                val allOperations = mutableListOf<BatchOperation>()
                albumRefs.forEach {
                    allOperations.add(UpdateDocumentOperation(it, mapOf("userPhotoUrl" to imageUrl)))
                }
                allOperations.add(UpdateDocumentOperation(userRef, mapOf("photoName" to storagePath)))
                allOperations.add(UpdateDocumentOperation(userRef, mapOf("photoUrl" to imageUrl)))
                db.executeInBatches(allOperations)

                sendWithDelay(ProfileSaveState.Completed)
            }
                .catch { exception ->
                    Log.e("ProfileUploadProgressViewModel", "에러", exception)
                    emit(ProfileSaveState.Error(exception.message ?: "알 수 없는 오류 발생"))
                }
                .collect {
                    _saveState.emit(it)
                }
        }
    }
}

@Parcelize
sealed class ProfileSaveState : Parcelable {
    data object Loading : ProfileSaveState()
    data object FetchingData : ProfileSaveState()
    data object CompressingImage : ProfileSaveState()
    data object UploadingImage : ProfileSaveState()
    data object SavingToFirestore : ProfileSaveState()
    data object Completed : ProfileSaveState()
    data class Error(val message: String) : ProfileSaveState()
}