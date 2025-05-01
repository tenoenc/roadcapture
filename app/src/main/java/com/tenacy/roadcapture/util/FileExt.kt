package com.tenacy.roadcapture.util

import android.content.Context
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

fun Context.clearCacheDirectory() {
    // 메인 캐시 디렉터리에서 "cropped"로 시작하는 jpg 파일들 삭제
    val cacheDir = cacheDir
    if (cacheDir.exists() && cacheDir.isDirectory) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile &&
                (file.name.startsWith("cropped") || file.name.startsWith("compressed")) &&
                file.name.endsWith(".jpg", ignoreCase = true)
            ) {
                file.delete()
            }
        }
    }
}

val Context.fileProviderAuthority get() = "${packageName}.fileprovider"