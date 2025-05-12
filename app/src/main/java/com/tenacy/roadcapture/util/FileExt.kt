package com.tenacy.roadcapture.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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

fun Context.compressImage(
    contentUri: Uri,
    quality: Int = 30,
    authority: String = "${packageName}.fileprovider"
): Uri {
    // Content URI에서 InputStream 가져오기
    val inputStream: InputStream? = contentResolver.openInputStream(contentUri)

    // InputStream에서 Bitmap 생성
    val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream?.close()

    // ByteArrayOutputStream 생성
    val outputStream = ByteArrayOutputStream()

    // Bitmap을 JPEG 형식으로 압축하고 ByteArrayOutputStream에 저장
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

    // 임시 파일 생성
    val fileName = "compressed_${System.currentTimeMillis()}.jpg"
    val file = File(cacheDir, fileName)

    // 압축된 데이터를 파일에 쓰기
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(outputStream.toByteArray())
    fileOutputStream.close()

    // FileProvider를 사용하여 파일의 URI 생성
    return FileProvider.getUriForFile(this, authority, file)
}

val Context.fileProviderAuthority get() = "${packageName}.fileprovider"