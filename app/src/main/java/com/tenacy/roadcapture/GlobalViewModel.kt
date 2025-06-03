package com.tenacy.roadcapture

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tenacy.roadcapture.data.db.LocationDao
import com.tenacy.roadcapture.data.db.MemoryDao
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.TravelStatePref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.ui.BaseViewModel
import com.tenacy.roadcapture.ui.GlobalViewEvent
import com.tenacy.roadcapture.util.clearCacheDirectory
import com.tenacy.roadcapture.worker.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val locationDao: LocationDao,
    private val travelingStateManager: TravelingStateManager,
) : BaseViewModel() {
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            travelingStateManager.stopTraveling()
            DeleteAlbumWorker.cancelAll(context)
            UpdateUsernameWorker.cancelWork(context)
            UpdateUserPhotoWorker.cancelWork(context)
            UpdateAlbumPublicWorker.cancelAll(context)
            SubscriptionCheckWorker.cancelAll(context)

            UserPref.clear()
            TravelStatePref.clear()
            SubscriptionPref.clear()
            memoryDao.clear()
            locationDao.clear()
            context.clearCacheDirectory()
            viewEvent(GlobalViewEvent.Logout)
        }
    }
}