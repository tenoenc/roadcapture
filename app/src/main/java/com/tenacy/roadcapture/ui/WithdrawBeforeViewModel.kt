package com.tenacy.roadcapture.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.api.dto.DeleteUserRequest
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.TravelPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.util.RetrofitInstance
import com.tenacy.roadcapture.util.auth
import com.tenacy.roadcapture.util.clearCacheDirectory
import com.tenacy.roadcapture.util.user
import com.tenacy.roadcapture.worker.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class WithdrawBeforeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val memoryDao: MemoryDao,
    private val travelingStateManager: TravelingStateManager,
) : BaseViewModel() {

    init {
        withdraw()
    }

    private fun withdraw() {
        viewModelScope.launch(Dispatchers.IO) {
            flow {
                val userId = UserPref.id

                // 1. 서버에 데이터 삭제 요청 (비동기)
                Log.d("AppInfoViewModel", "Firestore 데이터 삭제 시작")

                // Callable 방식 -> 인증 쉽게 처리, 느림
//                val data = hashMapOf("userId" to userId)
//                val task = functions.getHttpsCallable("deleteUserData").call(data)
//                Tasks.await(task)

                // HTTP 방식 -> 빠름, 인증 별도 구현
                val idToken = user?.getIdToken(false)?.await()?.token ?: throw Exception("토큰을 가져올 수 없습니다.")
                val response = RetrofitInstance.firebaseApi.deleteUserData(
                    authToken = "Bearer $idToken",
                    request = DeleteUserRequest(userId),
                )
                if (!response.isSuccessful) {
                    throw Exception(response.errorBody()?.string())
                }

                val responseBody = response.body()
                Log.d("AppInfoViewModel", "데이터 삭제 요청 성공: ${responseBody?.message}, JobID: ${responseBody?.jobId}")
                Log.d("AppInfoViewModel", "Firestore 데이터 삭제 완료")

                // 2. 여행 중이라면 서비스 및 워커 작업 취소
                Log.d("AppInfoViewModel", "백그라운드 작업 정리 시작")
                travelingStateManager.stopTraveling()
                DeleteAlbumWorker.cancelAll(context)
                UpdateUsernameWorker.cancelWork(context)
                UpdateUserPhotoWorker.cancelWork(context)
                UpdateAlbumPublicWorker.cancelAll(context)
                SubscriptionCheckWorker.cancelAll(context)
                Log.d("AppInfoViewModel", "백그라운드 작업 정리 완료")

                // 3. 로컬 데이터 정리
                Log.d("AppInfoViewModel", "로컬 데이터 정리 시작")

                UserPref.clear()
                TravelPref.clear()
                SubscriptionPref.clear()
                memoryDao.clear()
                locationDao.clear()
                context.clearCacheDirectory()

                Log.d("AppInfoViewModel", "로컬 데이터 정리 완료")

                // 4. Auth 계정 삭제 (이미 재인증했으므로 성공할 것)
                Log.d("AppInfoViewModel", "Auth 계정 삭제 시작")
                val currentUser = auth.currentUser
                currentUser?.delete()?.await()
                Log.d("AppInfoViewModel", "Auth 계정 삭제 완료")

                emit(Unit)
            }
                .catch { exception ->
                    Log.e("AppInfoViewModel", "서비스 탈퇴 실패", exception)
                    viewEvent(WithdrawBeforeViewEvent.Error.Withdraw(exception.message))
                }
                .collect {
                    viewEvent(WithdrawBeforeViewEvent.Complete)
                }
        }
    }

}