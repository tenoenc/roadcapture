package com.tenacy.roadcapture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.tenacy.roadcapture.data.firebase.exception.UpdateRequiredException
import com.tenacy.roadcapture.data.pref.AppPrefs
import com.tenacy.roadcapture.data.pref.WorkPref
import com.tenacy.roadcapture.manager.GoogleAccountManager
import com.tenacy.roadcapture.manager.LocaleManager
import com.tenacy.roadcapture.manager.RewardedAdManager
import com.tenacy.roadcapture.manager.TravelingStateManager
import com.tenacy.roadcapture.service.LocationTrackingService
import com.tenacy.roadcapture.ui.*
import com.tenacy.roadcapture.util.*
import com.tenacy.roadcapture.worker.CreateShareLinkWorker
import com.tenacy.roadcapture.worker.DeleteAlbumWorker
import com.tenacy.roadcapture.worker.UpdateAlbumPublicWorker
import dagger.hilt.android.AndroidEntryPoint
import io.branch.referral.Branch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), DefaultLifecycleObserver {

    val vm: GlobalViewModel by viewModels()

    @Inject
    lateinit var googleAccountManager: GoogleAccountManager

    @Inject
    lateinit var rewardedAdManager: RewardedAdManager

    @Inject
    lateinit var travelingStateManager: TravelingStateManager

    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null

    private var isDeepLinkProcessed = false

    private val branchListener = Branch.BranchReferralInitListener { linkProperties, error ->
        if (error == null) {
            Log.d("BranchSDK", "딥링크 초기화 성공: $linkProperties")

            if (linkProperties != null) {
                val clickedBranchLink = linkProperties.optBoolean("+clicked_branch_link", false)
                val nonBranchLink = linkProperties.optString("+non_branch_link", "")

                if (clickedBranchLink) {
                    val shareId = linkProperties.optString("share_id", "")
                    if (shareId.isNotEmpty()) {
                        Log.d("BranchSDK", "브랜치 링크에서 shareId 발견: $shareId")
                        AppPrefs.pendingDeepLinkShareId = shareId
                        navigateToAlbumSafely()
                    }
                } else if (!nonBranchLink.isNullOrBlank()) {
                    val shareId = Regex("roadcapture://open/albums/([^/?]+)").find(nonBranchLink)?.groupValues?.get(1)
                    if (!shareId.isNullOrBlank()) {
                        Log.d("BranchSDK", "논브랜치 링크에서 shareId 발견: $shareId")
                        AppPrefs.pendingDeepLinkShareId = shareId
                        navigateToAlbumSafely()
                    } else {
                        Log.e("BranchSDK", "논브랜치 링크에서 shareId 추출 오류: $nonBranchLink")
                    }
                }
            }
        } else {
            Log.e("BranchSDK", "Branch 초기화 에러: ${error.message}, 에러 코드: ${error.errorCode}")
        }

        // 딥링크 처리 완료 표시
        isDeepLinkProcessed = true

        // 이제 인텐트 데이터 제거해도 안전
        intent?.data = null
    }

    private fun navigateToAlbumSafely() {
        if(user != null || isMainFragmentInBackStack()) {
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.mainFragment, true)
                .build()
            navController?.navigate(R.id.mainFragment, null, options)
        } else {
            vm.viewEvent(GlobalViewEvent.Toast(ToastModel(getString(R.string.login_album_share_message))))
            navController?.popBackStack(R.id.loginFragment, false)
        }
    }

    private fun isMainFragmentInBackStack(): Boolean {
        // 현재에서 main으로 popUpTo가 가능한지 확인
        return try {
            navController?.getBackStackEntry(R.id.mainFragment) ?: return false
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)

        // Edge-to-Edge 활성화
        enableEdgeToEdge()

        // 상태바 아이콘을 어둡게 설정 (Night Theme 미제공 앱 대응)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        setContentView(R.layout.activity_main)

        setupWindowInsets()
        setupFragmentResultListeners()
        setupObservers()
        setupNavigationListener()
        setupWorkManagerCleaning()

        checkAppUpdate()
        checkTravelingStateOnStartup()

        // 딥링크 데이터가 있고 아직 처리되지 않은 경우에만 초기화
        if (intent?.data != null && !isDeepLinkProcessed) {
            Log.d("BranchSDK", "onCreate - 딥링크 처리 시작: ${intent?.data}")

            try {
                Branch.sessionBuilder(this).withCallback(branchListener).withData(intent?.data).init()
            } catch (e: Exception) {
                Log.d("BranchSDK", "Branch init failed, using reInit: ${e.message}")
                Branch.sessionBuilder(this).withCallback(branchListener).withData(intent?.data).reInit()
            }
        } else {
            Log.d("BranchSDK", "onCreate - 딥링크 처리 건너뛰기 (데이터: ${intent?.data}, 처리됨: $isDeepLinkProcessed)")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent

        if (intent.data != null) {
            Log.d("DeepLink", "onNewIntent 호출됨: ${intent.data}")
            isDeepLinkProcessed = false // 새로운 딥링크이므로 플래그 리셋
            Branch.sessionBuilder(this).withCallback(branchListener).withData(intent.data).reInit()
        } else {
            Log.d("DeepLink", "onNewIntent - 인텐트 데이터 없음")
        }
    }

    private fun setupWindowInsets() {
        val container = findViewById<FragmentContainerView>(R.id.container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            // 안전한 여백 계산
            view.updatePadding(
                left = maxOf(systemInsets.left, displayCutoutInsets.left),
                top = maxOf(systemInsets.top, displayCutoutInsets.top),
                right = maxOf(systemInsets.right, displayCutoutInsets.right),
                bottom = maxOf(systemInsets.bottom, imeInsets.bottom, displayCutoutInsets.bottom)
            )

            windowInsets
        }
    }

    private fun checkAppUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val systemRef = db.collection(FirebaseConstants.COLLECTION_SYSTEMS)
                    .document(FirebaseConstants.DOCUMENT_CONFIG_V2)

                val systemConfig = systemRef.get().await().toSystemConfigV2()

                val version = Version(BuildConfig.VERSION_NAME)
                val minAppVersion = Version(systemConfig.minAppVersion)

                if(version < minAppVersion) {
                    throw UpdateRequiredException()
                }
            } catch (exception: UpdateRequiredException) {
                withContext(Dispatchers.Main) {
                    handleViewEvents(GlobalViewEvent.UpdateRequired)
                }
            }
        }
    }

    private fun setupFragmentResultListeners() {
        supportFragmentManager.setFragmentResultListener(
            UpdateRequiredBottomSheetFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getParcelable<UpdateRequiredBottomSheetFragment.ParamsOut.Positive>(
                UpdateRequiredBottomSheetFragment.KEY_PARAMS_OUT_POSITIVE
            )?.let {
                Log.d("TAG", "Positive Button Clicked!")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=${packageName}")
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun observeUpdateUserPhoto() {
        repeatOnLifecycle {
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosForUniqueWorkFlow(Constants.USER_WORK_NAME_UPDATE_PHOTO)
                .collect {
                    val workInfo = it.takeIf { it.isNotEmpty() }?.first() ?: return@collect

                    if (WorkPref.processedUserPhotoUpdateWorkIds.contains(workInfo.id.toString())) {
                        return@collect
                    }

                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
//                            val userId = workInfo.outputData.getString(UpdateUsernameWorker.KEY_USER_ID)
//                            val username = workInfo.outputData.getString(UpdateUsernameWorker.KEY_USERNAME)

                            // 성공 처리
                            val childVm =
                                getFragmentViewModel<MyAlbumViewModel>(R.id.container, MyAlbumFragment::class.java)
                            childVm?.fetchData()

                            withContext(Dispatchers.Default) {
                                handleViewEvents(
                                    GlobalViewEvent.Toast(
                                        ToastModel(
                                            getString(R.string.profile_photo_change_success),
                                            ToastMessageType.Success
                                        )
                                    )
                                )
                            }

                            WorkPref.addProcessedUserPhotoUpdateWorkId(workInfo.id.toString())

                            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                                ?: navHostFragment?.childFragmentManager?.fragments?.firstOrNull() ?: return@collect

                            (findFragmentByClass(currentFragment, HomeFragment::class.java) as? HomeFragment)?.refreshData(requireNoShimmer = true)
                            (findFragmentByClass(currentFragment, ScrapFragment::class.java) as? ScrapFragment)?.refreshData(requireNoShimmer = true)
                            getFragmentViewModel<MyAlbumViewModel>(R.id.container, MyAlbumFragment::class.java)?.fetchData()
                        }

                        WorkInfo.State.FAILED -> {
//                            val errorMessage = workInfo.outputData.getString(UpdateUsernameWorker.KEY_RESULT_ERROR_MESSAGE)
                            withContext(Dispatchers.Default) {
                                handleViewEvents(
                                    GlobalViewEvent.Toast(
                                        ToastModel(
                                            getString(R.string.profile_photo_change_error),
                                            ToastMessageType.Warning
                                        )
                                    )
                                )
                            }

                            // 처리된 작업 ID 저장
                            WorkPref.addProcessedUserPhotoUpdateWorkId(workInfo.id.toString())
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun observeUpdateUsername() {
        repeatOnLifecycle {
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosForUniqueWorkFlow(Constants.USER_WORK_NAME_UPDATE_NAME)
                .collect {
                    val workInfo = it.takeIf { it.isNotEmpty() }?.first() ?: return@collect

                    if (WorkPref.processedUsernameUpdateWorkIds.contains(workInfo.id.toString())) {
                        return@collect
                    }

                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
//                            val userId = workInfo.outputData.getString(UpdateUsernameWorker.KEY_USER_ID)
//                            val username = workInfo.outputData.getString(UpdateUsernameWorker.KEY_USERNAME)

                            // 성공 처리
                            withContext(Dispatchers.Default) {
                                handleViewEvents(
                                    GlobalViewEvent.Toast(
                                        ToastModel(
                                            getString(R.string.name_change_success),
                                            ToastMessageType.Success
                                        )
                                    )
                                )
                            }

                            WorkPref.addProcessedUsernameUpdateWorkId(workInfo.id.toString())

                            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                                ?: navHostFragment?.childFragmentManager?.fragments?.firstOrNull() ?: return@collect

                            (findFragmentByClass(currentFragment, HomeFragment::class.java) as? HomeFragment)?.refreshData(requireNoShimmer = true)
                            (findFragmentByClass(currentFragment, ScrapFragment::class.java) as? ScrapFragment)?.refreshData(requireNoShimmer = true)
                            getFragmentViewModel<MyAlbumViewModel>(R.id.container, MyAlbumFragment::class.java)?.fetchData()
                        }

                        WorkInfo.State.FAILED -> {
//                            val errorMessage = workInfo.outputData.getString(UpdateUsernameWorker.KEY_RESULT_ERROR_MESSAGE)
                            withContext(Dispatchers.Default) {
                                handleViewEvents(
                                    GlobalViewEvent.Toast(
                                        ToastModel(
                                            getString(R.string.name_change_error),
                                            ToastMessageType.Warning
                                        )
                                    )
                                )
                            }

                            // 처리된 작업 ID 저장
                            WorkPref.addProcessedUsernameUpdateWorkId(workInfo.id.toString())
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun observeCreateShareLink() {
        repeatOnLifecycle {
            // 성공 및 실패 상태의 작업 관찰
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosFlow(
                    WorkQuery.Builder
                        .fromStates(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED))
                        .build()
                )
                .collect { workInfoList ->
                    // 앨범 삭제 작업만 필터링
                    val createShareLinkWorks = workInfoList.filter { workInfo ->
                        val isNotProcessed = !WorkPref.processedShareLinkCreateWorkIds.contains(workInfo.id.toString())
                        val isShareLinkCreateWork = workInfo.tags.any { tag ->
                            tag.startsWith(CreateShareLinkWorker.TAG)
                        }

                        isNotProcessed && isShareLinkCreateWork
                    }

                    // 새로운 작업이 있는지 확인 및 처리
                    if (createShareLinkWorks.isNotEmpty()) {
                        // 작업 상태별로 처리
                        val hasSucceededWork = createShareLinkWorks.any { it.state == WorkInfo.State.SUCCEEDED }
                        val hasFailedWork = createShareLinkWorks.any { it.state == WorkInfo.State.FAILED }

                        // 성공 메시지 (한 번만)
                        if (hasSucceededWork) {
                            createShareLinkWorks.forEach { Log.d("MainActivity", it.toString()) }
                            val workInfo = createShareLinkWorks.first()
                            val shareLink = workInfo.outputData.getString(CreateShareLinkWorker.RESULT_SHARE_LINK) ?: ""

                            // 성공 처리
                            handleViewEvents(GlobalViewEvent.CopyToClipboard(shareLink))

                            handleViewEvents(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        getString(R.string.share_link_create_success),
                                        ToastMessageType.Success
                                    )
                                )
                            )

                            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                                ?: navHostFragment?.childFragmentManager?.fragments?.firstOrNull() ?: return@collect

                            (findFragmentByClass(currentFragment, MyAlbumTabFragment::class.java) as? MyAlbumTabFragment)?.refreshData(includeParent = false)
                        }

                        // 실패 메시지 (한 번만)
                        if (hasFailedWork) {
                            handleViewEvents(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        getString(R.string.share_link_create_error),
                                        ToastMessageType.Warning
                                    )
                                )
                            )
                        }

                        // 처리된 작업 ID 저장
                        createShareLinkWorks.forEach { workInfo ->
                            WorkPref.addProcessedCreateShareLinkWorkId(workInfo.id.toString())
                        }
                    }
                }
        }
    }

    private fun observeUpdateAlbumPublic() {
        repeatOnLifecycle {
            // 성공 및 실패 상태의 작업 관찰
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosFlow(
                    WorkQuery.Builder
                        .fromStates(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED))
                        .build()
                )
                .collect { workInfoList ->
                    // 앨범 삭제 작업만 필터링
                    val updateAlbumPublicWorks = workInfoList.filter { workInfo ->
                        val isNotProcessed = !WorkPref.processedAlbumPublicUpdateWorkIds.contains(workInfo.id.toString())
                        val isAlbumPublicUpdateWork = workInfo.tags.any { tag ->
                            tag.startsWith(UpdateAlbumPublicWorker.TAG)
                        }

                        isNotProcessed && isAlbumPublicUpdateWork
                    }

                    // 새로운 작업이 있는지 확인 및 처리
                    if (updateAlbumPublicWorks.isNotEmpty()) {
                        // 작업 상태별로 처리
                        val hasSucceededWork = updateAlbumPublicWorks.any { it.state == WorkInfo.State.SUCCEEDED }
                        val hasFailedWork = updateAlbumPublicWorks.any { it.state == WorkInfo.State.FAILED }

                        // 성공 메시지 (한 번만)
                        if (hasSucceededWork) {
                            updateAlbumPublicWorks.forEach { Log.d("MainActivity", it.toString()) }
                            val workInfo = updateAlbumPublicWorks.first()
                            val albumId = workInfo.outputData.getString(UpdateAlbumPublicWorker.KEY_ALBUM_ID)
                            val isPublic = workInfo.outputData.getBoolean(UpdateAlbumPublicWorker.KEY_PUBLIC, false)

                            // 성공 처리
                            val publicText = if(isPublic) getString(R.string.visibility_public) else getString(R.string.visibility_private)
                            val `0` = publicText
                            handleViewEvents(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        getString(R.string.album_visibility_change_success, `0`),
                                        ToastMessageType.Success
                                    )
                                )
                            )

                            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                                ?: navHostFragment?.childFragmentManager?.fragments?.firstOrNull() ?: return@collect

                            (findFragmentByClass(currentFragment, HomeFragment::class.java) as? HomeFragment)?.refreshData(requireNoShimmer = true)
                            (findFragmentByClass(currentFragment, ScrapFragment::class.java) as? ScrapFragment)?.refreshData(requireNoShimmer = true)
                            (findFragmentByClass(currentFragment, MyAlbumTabFragment::class.java) as? MyAlbumTabFragment)?.refreshData(includeParent = false)
                        }

                        // 실패 메시지 (한 번만)
                        if (hasFailedWork) {
                            handleViewEvents(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        getString(R.string.visibility_change_error),
                                        ToastMessageType.Warning
                                    )
                                )
                            )
                        }

                        // 처리된 작업 ID 저장
                        updateAlbumPublicWorks.forEach { workInfo ->
                            WorkPref.addProcessedAlbumPublicUpdateWorkId(workInfo.id.toString())
                        }
                    }
                }
        }
    }

    private fun observeDeleteAlbum() {
        repeatOnLifecycle {
            // 성공 및 실패 상태의 작업 관찰
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosFlow(
                    WorkQuery.Builder
                        .fromStates(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED))
                        .build()
                )
                .collect { workInfoList ->
                    // 앨범 삭제 작업만 필터링
                    val albumDeleteWorks = workInfoList.filter { workInfo ->
                        val isNotProcessed = !WorkPref.processedAlbumDeleteWorkIds.contains(workInfo.id.toString())
                        val isAlbumDeleteWork = workInfo.tags.any { tag ->
                            tag.startsWith(DeleteAlbumWorker.TAG)
                        }

                        isNotProcessed && isAlbumDeleteWork
                    }

                    // 새로운 작업이 있는지 확인 및 처리
                    if (albumDeleteWorks.isNotEmpty()) {
                        // 작업 상태별로 처리
                        val hasSucceededWork = albumDeleteWorks.any { it.state == WorkInfo.State.SUCCEEDED }
                        val hasFailedWork = albumDeleteWorks.any { it.state == WorkInfo.State.FAILED }

                        // 성공 메시지 (한 번만)
                        if (hasSucceededWork) {
                            handleViewEvents(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        getString(R.string.album_delete_success),
                                        ToastMessageType.Success
                                    )
                                )
                            )

                            // 데이터 새로고침 (한 번만)
                            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                                ?: navHostFragment?.childFragmentManager?.fragments?.firstOrNull() ?: return@collect
                            (findFragmentByClass(currentFragment, HomeFragment::class.java) as? HomeFragment)?.refreshData(requireNoShimmer = true)
                            (findFragmentByClass(currentFragment, ScrapFragment::class.java) as? ScrapFragment)?.refreshData(requireNoShimmer = true)
                            getFragmentViewModel<MyAlbumViewModel>(R.id.container, MyAlbumFragment::class.java)?.refreshAll()
                        }

                        // 실패 메시지 (한 번만)
                        if (hasFailedWork) {
                            handleViewEvents(
                                GlobalViewEvent.Toast(
                                    ToastModel(
                                        getString(R.string.album_delete_error),
                                        ToastMessageType.Warning
                                    )
                                )
                            )
                        }

                        // 처리된 작업 ID 저장
                        albumDeleteWorks.forEach { workInfo ->
                            WorkPref.addProcessedAlbumDeleteWorkId(workInfo.id.toString())
                        }
                    }
                }

        }
    }

    private fun setupWorkManagerCleaning() {
        // 마지막 정리 시간으로부터 일정 시간(예: 일주일)이 지났는지 확인
        val currentTime = System.currentTimeMillis()
        val lastCleanTime = WorkPref.lastWorkPruneTime
        val cleanInterval = 7 * 24 * 60 * 60 * 1000L // 7일 (밀리초)

        if (currentTime - lastCleanTime > cleanInterval) {
            // 백그라운드 스레드에서 정리 작업 수행
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // WorkManager의 완료된 작업 정리
                    WorkManager.getInstance(applicationContext).pruneWork()

                    // WorkPrefs의 오래된 작업 ID 정리
                    WorkPref.cleanupOldWorkIds()
                } catch (e: Exception) {
                    Log.e("MainActivity", "작업 정리 중 오류 발생", e)
                }
            }
        }
    }

    private fun checkTravelingStateOnStartup() {
        // 앱 시작 시 여행 상태 확인
        if (travelingStateManager.isTraveling.value) {
            val isTripFragmentVisible = navController?.currentDestination?.id == R.id.tripFragment

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

        navController?.addOnDestinationChangedListener(destinationChangedListener!!)
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
        val isTripFragmentVisible = navController?.currentDestination?.id == R.id.tripFragment

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

    override fun onResume() {
        super<AppCompatActivity>.onResume()
        googleAccountManager.forceCheck()
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
            navController?.removeOnDestinationChangedListener(it)
        }
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        super<AppCompatActivity>.onDestroy()
    }

    private fun setupObservers() {
        observeUpdateUsername()
        observeUpdateUserPhoto()
        observeUpdateAlbumPublic()
        observeCreateShareLink()
        observeDeleteAlbum()
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

            is GlobalViewEvent.Logout -> {
                supportFragmentManager.dismissAllDialogs()
                signOut()
            }

            is GlobalViewEvent.UpdateRequired -> {
                val bottomSheet = UpdateRequiredBottomSheetFragment.newInstance()
                bottomSheet.show(supportFragmentManager, UpdateRequiredBottomSheetFragment.TAG)
            }

            is GlobalViewEvent.UnderMaintenance -> {
                val bottomSheet = UnderMaintenanceBottomSheetFragment.newInstance()
                bottomSheet.show(supportFragmentManager, UnderMaintenanceBottomSheetFragment.TAG)
            }
        }
    }

    private fun signOut() {
        Firebase.auth.signOut()

        val navOptions = NavOptions.Builder().setPopUpTo(
            R.id.mainFragment,
            true
        ).build()

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