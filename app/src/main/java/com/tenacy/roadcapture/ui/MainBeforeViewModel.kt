package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.SubscriptionManager
import com.tenacy.roadcapture.util.FirebaseConstants
import com.tenacy.roadcapture.util.auth
import com.tenacy.roadcapture.util.db
import com.tenacy.roadcapture.util.user
import com.tenacy.roadcapture.worker.SubscriptionCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MainBeforeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val subscriptionManager: SubscriptionManager,
): BaseViewModel() {

    init {
        sign()
    }

    private fun sign() {
        viewModelScope.launch(Dispatchers.IO) {
            val args = MainBeforeFragmentArgs.fromSavedStateHandle(savedStateHandle)
            val credential = args.authCredential
            val socialType = args.socialType
            val isExistingUser = args.isExistingUser
            flow {
                auth.signInWithCredential(credential).await()
                val userId = user!!.uid
                val userRef = db.collection("users").document(userId)
                if (!isExistingUser) {
                    val socialUserId = args.socialUserId!!
                    val username = args.username ?: user!!.displayName ?: "unknown"

                    // 1. Authentication 프로필 업데이트
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    user!!.updateProfile(profileUpdates).await()

                    // 2. 파이어스토어 업데이트
                    val photoPath = /*"images/users/$userId/profile.jpg"*/ FirebaseConstants.DEFAULT_PROFILE_PATH
                    val photoUrl = /*setDefaultProfileImage(photoPath)*/ FirebaseConstants.DEFAULT_PROFILE_URL

                    val userData = mapOf(
                        "displayName" to username,
                        "socialUserId" to socialUserId,
                        "provider" to socialType.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "photoUrl" to photoUrl,
                        "photoName" to photoPath,
                    )
                    userRef.set(userData).await()

                    // 3. 로컬 데이터 업데이트
                    UserPref.id = userId
                    UserPref.provider = socialType
                    SubscriptionPref.isSubscriptionActive = false
                    UserPref.displayName = username
                    UserPref.photoUrl = photoUrl
                } else {
                    // 1. 로컬 데이터 업데이트
                    UserPref.id = userId
                    UserPref.provider = socialType
                    UserPref.displayName = user!!.displayName!!
                    UserPref.photoUrl = user!!.photoUrl.toString()
                }

                val subscriptionState = subscriptionManager.checkSubscriptionStatusSuspend()
                Log.d("SubscriptionManager", subscriptionState.toString())

                if(isExistingUser) {
                    SubscriptionPref.isSubscriptionActive = subscriptionState.isActive
                }

                SubscriptionCheckWorker.enqueuePeriodicWork(context)

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("MainBeforeViewModel", "에러", exception)
                    viewEvent(MainBeforeViewEvent.Error.Login(exception.message))
                }
                .collect {
                    viewEvent(MainBeforeViewEvent.Complete)
                }
        }
    }
}