package com.tenacy.roadcapture

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.tenacy.roadcapture.ui.GlobalViewEvent
import com.tenacy.roadcapture.ui.MyToast
import com.tenacy.roadcapture.ui.ToastMessageType
import com.tenacy.roadcapture.util.currentFragment
import com.tenacy.roadcapture.util.repeatOnLifecycle
import com.tenacy.roadcapture.util.navController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    val vm: GlobalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupObservers()
        setupPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        navController.handleDeepLink(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

//        when (requestCode) {
//            PermissionManager.PERMISSIONS_REQUEST_CODE -> {
//                if (!grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
//                    MyToast.warn(this, "일부 기능이 제한될 수 있습니다.").show()
//                }
//            }
//
//            PermissionManager.RECORD_PERMISSION_REQUEST_CODE -> {
//                if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
//                    MyToast.warn(this, "음성 관련 기능이 제한됩니다.").show()
//                }
//            }
//        }
    }

    private fun setupObservers() {
        observeViewEventState()
    }

    private fun observeViewEventState() {
        repeatOnLifecycle {
            vm.viewEvent.collect {
                it?.getContentIfNotHandled()?.let { event ->
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
        }
    }

    fun signOut() {
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

    private fun setupPermissions() {}

    private fun FragmentManager.dismissAllDialogs() {
        fragments.forEach { fragment ->
            (fragment as? DialogFragment)?.dismissAllowingStateLoss()
            fragment.childFragmentManager.dismissAllDialogs()
        }
    }
}