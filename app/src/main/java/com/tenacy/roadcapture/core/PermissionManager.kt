package com.tenacy.roadcapture.core

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class PermissionManager @Inject constructor(
    private val activity: Activity
) {
    companion object {
        const val PERMISSIONS_REQUEST_CODE = 123
        const val RECORD_PERMISSION_REQUEST_CODE = 1000
        const val REQUEST_LOCATION_SETTINGS = 1001

        val DEVICE_INFO_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    fun hasWriteSettingsPermission(): Boolean =
        Settings.System.canWrite(activity)

    fun requestWriteSettingsPermission() {
        if (!hasWriteSettingsPermission()) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun requestAudioPermission() {
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkDeviceInfoPermissions(): List<String> =
        DEVICE_INFO_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

    fun requestDeviceInfoPermissions() {
        val notGrantedPermissions = checkDeviceInfoPermissions()

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                notGrantedPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    fun hasAllDeviceInfoPermissions(): Boolean =
        checkDeviceInfoPermissions().isEmpty()

    fun shouldShowPermissionRationale(): Boolean =
        DEVICE_INFO_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

    fun shouldShowAudioPermissionRationale(): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO
        )

    fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(this)
        }
    }

    fun checkLocationSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .build()

        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        LocationServices.getSettingsClient(activity)
            .checkLocationSettings(locationSettingsRequest)
            .addOnFailureListener { exception ->
                if ((exception as ApiException).statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                    try {
                        val resolvable = exception as ResolvableApiException
                        resolvable.startResolutionForResult(activity, REQUEST_LOCATION_SETTINGS)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("DeviceInfo", "Error getting location settings resolution", e)
                    }
                }
            }
    }
}