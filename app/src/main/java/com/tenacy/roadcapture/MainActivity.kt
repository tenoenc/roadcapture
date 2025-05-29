package com.tenacy.roadcapture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.tenacy.roadcapture.data.pref.SubscriptionPref
import com.tenacy.roadcapture.data.pref.UserPref
import com.tenacy.roadcapture.manager.RewardedAdManager
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.service.LocationTrackingService
import com.tenacy.roadcapture.ui.GlobalViewEvent
import com.tenacy.roadcapture.ui.MyToast
import com.tenacy.roadcapture.ui.ToastMessageType
import com.tenacy.roadcapture.util.currentFragment
import com.tenacy.roadcapture.util.navController
import com.tenacy.roadcapture.util.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), DefaultLifecycleObserver {

    val vm: GlobalViewModel by viewModels()

    @Inject
    lateinit var rewardedAdManager: RewardedAdManager

    @Inject
    lateinit var travelingStateManager: TravelingStateManager

    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        setContentView(R.layout.activity_main)
        setupObservers()
        setupNavigationListener()
        checkTravelingStateOnStartup()  // 추가
    }

    private fun checkTravelingStateOnStartup() {
        // 앱 시작 시 여행 상태 확인
        if (travelingStateManager.isTraveling.value) {
            val isTripFragmentVisible = navController.currentDestination?.id == R.id.tripFragment

            if (!isTripFragmentVisible && !LocationTrackingService.isServiceRunning()) {
                // 여행 중이지만 TripFragment가 아니고 서비스도 실행 중이지 않으면 서비스 시작
                travelingStateManager.startLocationTrackingService()
                Log.d("MainActivity", "앱 시작 시 여행 상태 확인 - 서비스 시작")
            }
        }
    }

    private fun setupNavigationListener() {
        destinationChangedListener = NavController.OnDestinationChangedListener { _, destination, _ ->
            handleDestinationChange(destination.id)
        }

        navController.addOnDestinationChangedListener(destinationChangedListener!!)
    }

    private fun handleDestinationChange(destinationId: Int) {
        val isTripFragmentVisible = destinationId == R.id.tripFragment

        // 앱이 포어그라운드에 있고 여행 중일 때만 처리
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (travelingStateManager.isTraveling.value) {
                if (isTripFragmentVisible && LocationTrackingService.isServiceRunning()) {
                    // TripFragment로 이동 시 서비스 중지
                    travelingStateManager.stopLocationTrackingService()
                    Log.d("MainActivity", "TripFragment 표시: 위치 추적 서비스 중지")
                } else if (!isTripFragmentVisible && !LocationTrackingService.isServiceRunning()) {
                    // TripFragment에서 벗어날 때 서비스 시작
                    travelingStateManager.startLocationTrackingService()
                    Log.d("MainActivity", "TripFragment 벗어남: 위치 추적 서비스 시작")
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)

        // 포어그라운드 복귀 시 상태 확인
        val isTripFragmentVisible = navController.currentDestination?.id == R.id.tripFragment

        if (travelingStateManager.isTraveling.value) {
            if (isTripFragmentVisible) {
                // TripFragment 표시 중이면 서비스 중지
                travelingStateManager.stopLocationTrackingService()
                Log.d("MainActivity", "포어그라운드 복귀 - TripFragment 표시: 서비스 중지")
            } else {
                // TripFragment가 아니면 서비스 시작
                travelingStateManager.startLocationTrackingService()
                Log.d("MainActivity", "포어그라운드 복귀 - TripFragment 미표시: 서비스 시작")
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStop(owner)

        // 백그라운드로 갈 때 여행 중이면 무조건 서비스 시작
        if (travelingStateManager.isTraveling.value) {
            travelingStateManager.startLocationTrackingService()
            Log.d("MainActivity", "백그라운드 전환: 서비스 시작")
        }
    }

    override fun onDestroy() {
        destinationChangedListener?.let {
            navController.removeOnDestinationChangedListener(it)
        }
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        super<AppCompatActivity>.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navController.handleDeepLink(intent)
    }

    private fun setupObservers() {
        observeViewEventState()
    }

    private fun observeViewEventState() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it.getContentIfNotHandled()?.let { event ->
                    (event as? GlobalViewEvent)?.let { handleViewEvents(it) }
                }
            }
        }
    }

    private suspend fun handleViewEvents(event: GlobalViewEvent) = withContext(Dispatchers.Main) {
        when (event) {
            is GlobalViewEvent.GlobalNavigateToLogin -> {
                supportFragmentManager.dismissAllDialogs()
                signOut()
            }

            is GlobalViewEvent.Toast -> {
                when (event.toast.type) {
                    is ToastMessageType.Info -> MyToast.info(this@MainActivity, event.toast.message).show()
                    is ToastMessageType.Success -> MyToast.success(this@MainActivity, event.toast.message).show()
                    is ToastMessageType.Warning -> MyToast.warn(this@MainActivity, event.toast.message).show()
                }
            }

            is GlobalViewEvent.CopyToClipboard -> {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("text", event.text)
                clipboardManager.setPrimaryClip(clipData)
            }
        }
    }

    fun signOut() {
        Firebase.auth.signOut()
        val navOptions = NavOptions.Builder().setPopUpTo(
            R.id.mainFragment,
            true
        ).build()

        UserPref.clear()
        SubscriptionPref.clearSubscription()

        currentFragment?.findNavController()?.run {
            navigate(
                R.id.loginFragment,
                null,
                navOptions
            )
        }
    }

    private fun FragmentManager.dismissAllDialogs() {
        fragments.forEach { fragment ->
            (fragment as? DialogFragment)?.dismissAllowingStateLoss()
            fragment.childFragmentManager.dismissAllDialogs()
        }
    }
}