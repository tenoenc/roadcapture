package com.tenacy.roadcapture.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
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

    // EXIF 방향 정보 확인
    val exifOrientation = try {
        val exifInterface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            inputStream?.let { ExifInterface(it) }
        } else {
            contentUri.path?.let { ExifInterface(it) }
        }

        val orientation = exifInterface?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        inputStream?.close()

        // 새로운 InputStream 열기 (이전 것은 닫혔으므로)
        contentResolver.openInputStream(contentUri)?.let { newInputStream ->
            val bitmap = BitmapFactory.decodeStream(newInputStream)
            newInputStream.close()
            Pair(bitmap, orientation)
        } ?: Pair(null, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        Log.e("CompressImage", "EXIF 읽기 오류: ${e.message}")
        // EXIF 읽기 실패 시 기본 처리
        contentResolver.openInputStream(contentUri)?.let { newInputStream ->
            val bitmap = BitmapFactory.decodeStream(newInputStream)
            newInputStream.close()
            Pair(bitmap, ExifInterface.ORIENTATION_NORMAL)
        } ?: Pair(null, ExifInterface.ORIENTATION_NORMAL)
    }

    val (bitmap, orientation) = exifOrientation
    if (bitmap == null) {
        throw IllegalArgumentException("이미지를 디코딩할 수 없습니다")
    }

    // 이미지 회전 처리
    val rotatedBitmap = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, true, false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, false, true)
        else -> bitmap
    }

    // ByteArrayOutputStream 생성
    val outputStream = ByteArrayOutputStream()

    // Bitmap을 JPEG 형식으로 압축하고 ByteArrayOutputStream에 저장
    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

    // 원본 비트맵과 회전된 비트맵이 다른 경우, 원본 비트맵 해제
    if (rotatedBitmap !== bitmap) {
        bitmap.recycle()
    }

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

// 비트맵 회전 함수
private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// 비트맵 좌우/상하 반전 함수
private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
    val matrix = Matrix()
    matrix.preScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

val Context.fileProviderAuthority get() = "${packageName}.fileprovider"