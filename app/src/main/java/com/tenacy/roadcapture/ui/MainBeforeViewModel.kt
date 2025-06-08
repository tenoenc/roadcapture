package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.SubscriptionPref.linkedAccountExists
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
            val socialUserId = args.socialUserId
            val socialType = args.socialType
            val isExistingUser = args.isExistingUser
            flow {
                auth.signInWithCredential(credential).await()
                val userId = user!!.uid
                val userRef = db.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                if (!isExistingUser) {
                    val username = args.username ?: user!!.displayName ?: "unknown"
                    val defaultProfile = args.defaultProfile!!

                    // 1. Authentication 프로필 업데이트
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()
                    user!!.updateProfile(profileUpdates).await()

                    // 2. 파이어스토어 업데이트
                    val defaultProfilePath  = when(defaultProfile) {
                        is DefaultProfile.Social -> "images/users/$userId/profile.jpg"
                        is DefaultProfile.App -> FirebaseConstants.DEFAULT_PROFILE_PATH
                    }
                    val userData = mapOf(
                        "displayName" to username,
                        "socialUserId" to socialUserId,
                        "provider" to socialType.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "photoUrl" to defaultProfile.url,
                        "photoName" to defaultProfilePath,
                    )
                    userRef.set(userData).await()

                    // 3. 로컬 데이터 업데이트
                    UserPref.id = userId
                    UserPref.socialUserId = socialUserId
                    UserPref.provider = socialType
                    UserPref.displayName = username
                    UserPref.photoUrl = defaultProfile.url
                } else {
                    // 1. 로컬 데이터 업데이트
                    UserPref.id = userId
                    UserPref.socialUserId = socialUserId
                    UserPref.provider = socialType
                    UserPref.displayName = user!!.displayName!!
                    UserPref.photoUrl = user!!.photoUrl.toString()
                }

                val isSubscriptionActive = subscriptionManager.checkSubscriptionStatus()
                Log.d("SubscriptionManager", "구독 여부 : $isSubscriptionActive")

                SubscriptionCheckWorker.enqueuePeriodicWork(context)

                if(isSubscriptionActive) {
                    // 이미 구독 혜택을 받고 있는 계정이 있는지 확인
                    val linkedAccountExists = db.collection(FirebaseConstants.COLLECTION_USERS)
                        .whereNotEqualTo(FieldPath.documentId(), userId)
                        .whereEqualTo("purchaseToken", SubscriptionPref.purchaseToken)
                        .get().await().documents.size > 0
                    SubscriptionPref.linkedAccountExists = linkedAccountExists
                }

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