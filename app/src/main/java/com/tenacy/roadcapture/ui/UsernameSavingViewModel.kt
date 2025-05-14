package com.tenacy.roadcapture.ui

import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.UserProfileChangeRequest
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.user
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class UsernameSavingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
): BaseViewModel() {

    private val username: String = UsernameSavingFragmentArgs.fromSavedStateHandle(savedStateHandle).username

    private val _saveState = MutableStateFlow<UsernameSaveState>(UsernameSaveState.Initial)
    val saveState: StateFlow<UsernameSaveState> = _saveState

    init {
        updateUsername()
    }

    private fun updateUsername() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                emit(UsernameSaveState.Loading)

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(username)
                    .build()

                user?.updateProfile(profileUpdates)?.await()

                val userRef = db.collection("users").document(user!!.uid)
                val albumRefs = db.collection("albums")
                    .whereEqualTo("userRef", userRef)
                    .get().await().documents.map { it.reference }
                
                db.runTransaction { transaction ->
                    for (albumRef in albumRefs) {
                        transaction.update(albumRef, "userDisplayName", username)
                    }
                    transaction.update(userRef, "displayName", username)
                }.await()

                emit(UsernameSaveState.Completed)
            }
                .catch { exception ->
                    Log.e("UsernameSavingViewModel", "에러", exception)
                    emit(UsernameSaveState.Error(exception.message ?: "알 수 없는 오류 발생"))
                }
                .collect { state ->
                    _saveState.value = state
                }
        }
    }
}

@Parcelize
sealed class UsernameSaveState : Parcelable {
    data object Initial : UsernameSaveState()
    data object Loading : UsernameSaveState()
    data object Completed : UsernameSaveState()
    data class Error(val message: String) : UsernameSaveState()
}