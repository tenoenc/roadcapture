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
import com.tenacy.roadcapture.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

val Context.fileProviderAuthority get() = "${packageName}.fileprovider"

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
        throw IllegalArgumentException(getString(R.string.image_decode_error))
    }

    // 이미지 회전 처리 - 공통 함수 사용
    val rotatedBitmap = applyExifOrientation(bitmap, orientation)

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
    return FileProvider.getUriForFile(this, fileProviderAuthority, file)
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

// ----- 추가된 함수들 -----

/**
 * Exif 방향 정보에 따라 이미지 회전/반전 적용 (공통 함수)
 */
private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, true, false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, false, true)
        else -> bitmap
    }
}

/**
 * URI를 Bitmap으로 변환하는 확장 함수
 */
fun Uri.toBitmap(context: Context): Bitmap? {
    return try {
        when {
            // 콘텐츠 URI인 경우
            this.scheme.equals("content", ignoreCase = true) -> {
                val inputStream = context.contentResolver.openInputStream(this)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // Exif 정보에 따라 이미지 회전 처리
                correctImageRotation(context, this, bitmap)
            }

            // 파일 URI인 경우
            this.scheme.equals("file", ignoreCase = true) -> {
                val path = this.path
                if (path != null) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    // Exif 정보에 따라 이미지 회전 처리
                    correctImageRotation(context, this, bitmap)
                } else null
            }

            // 기타 URI 처리
            else -> {
                val inputStream = context.contentResolver.openInputStream(this)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Exif 정보를 기반으로 이미지 회전 처리
 */
private fun correctImageRotation(context: Context, uri: Uri, bitmap: Bitmap?): Bitmap? {
    if (bitmap == null) return null

    var inputStream: InputStream? = null
    try {
        inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val exif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ExifInterface(inputStream)
            } else {
                uri.path?.let { ExifInterface(it) } ?: return bitmap
            }

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            // 공통 회전 함수 사용
            return applyExifOrientation(bitmap, orientation)
        }
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        try {
            inputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    return bitmap
}

/**
 * 메모리 효율적인 방식으로 URI를 Bitmap으로 변환 (대용량 이미지용)
 */
fun Uri.toBitmapEfficiently(context: Context, requiredWidth: Int, requiredHeight: Int): Bitmap? {
    try {
        // 이미지 크기 정보 가져오기
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(this)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }

        // 이미지 스케일 계산
        val (width, height) = options.run { outWidth to outHeight }
        var inSampleSize = 1

        if (height > requiredHeight || width > requiredWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // 요구 사이즈보다 크지 않은 최대 inSampleSize 값 계산
            while ((halfHeight / inSampleSize) >= requiredHeight && (halfWidth / inSampleSize) >= requiredWidth) {
                inSampleSize *= 2
            }
        }

        // 실제 디코딩
        options.apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
        }

        return context.contentResolver.openInputStream(this)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            correctImageRotation(context, this, bitmap)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

suspend fun Uri.resizeCenterCrop(
    context: Context,
    width: Int = 1200,
    height: Int = 630,
    quality: Int = 70,
): Uri = withContext(Dispatchers.IO) {
    try {
        // 원본 이미지 불러오기
        val inputStream = context.contentResolver.openInputStream(this@resizeCenterCrop)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // 리사이징 및 센터 크롭
        val resizedBitmap = originalBitmap.resizeCenterCrop(width, height)

        // 임시 파일에 저장
        val file = File(context.cacheDir, "resized_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { outputStream ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }

        // 비트맵 메모리 해제
        resizedBitmap.recycle()
        originalBitmap.recycle()

        // FileProvider로 URI 생성
        return@withContext FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: Exception) {
        Log.e("ImageResize", "Error resizing image", e)
        throw e
    }
}

/**
 * 이미지를 센터 크롭하고 원하는 크기로 리사이징
 */
private fun Bitmap.resizeCenterCrop(targetWidth: Int, targetHeight: Int): Bitmap {
    val sourceWidth = width
    val sourceHeight = height

    // 원본 비율과 목표 비율 계산
    val sourceRatio = sourceWidth.toFloat() / sourceHeight
    val targetRatio = targetWidth.toFloat() / targetHeight

    // 센터 크롭할 영역 계산
    val srcWidth: Int
    val srcHeight: Int
    val srcX: Int
    val srcY: Int

    if (sourceRatio > targetRatio) {
        // 원본이 더 넓은 경우 - 높이를 맞추고 양옆을 크롭
        srcHeight = sourceHeight
        srcWidth = (sourceHeight * targetRatio).toInt()
        srcY = 0
        srcX = (sourceWidth - srcWidth) / 2
    } else {
        // 원본이 더 좁은 경우 - 너비를 맞추고 위아래를 크롭
        srcWidth = sourceWidth
        srcHeight = (sourceWidth / targetRatio).toInt()
        srcX = 0
        srcY = (sourceHeight - srcHeight) / 2
    }

    // 크롭하고 리사이징
    val croppedBitmap = Bitmap.createBitmap(this, srcX, srcY, srcWidth, srcHeight)

    // 원본과 사이즈가 같으면 바로 반환
    if (srcWidth == targetWidth && srcHeight == targetHeight) {
        return croppedBitmap
    }

    // 크기가 다르면 리사이징
    val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true)

    // 중간 비트맵 메모리 해제
    if (croppedBitmap != scaledBitmap) {
        croppedBitmap.recycle()
    }

    return scaledBitmap
}

fun Context.getContentUriFromUrlContext(urlString: String): Uri? {
    return try {
        // 1. URL로부터 이미지 다운로드
        val url = URL(urlString)
        val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()
        val inputStream = connection.inputStream
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)

        // 2. 파일 생성 및 저장
        val file = File(cacheDir, "image_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        // 3. FileProvider를 통해 content Uri 생성
        FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // AndroidManifest와 일치해야 함
            file
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun Context.getUriFromAsset(assetFileName: String): Uri? {
    return try {
        val inputStream = assets.open(assetFileName)

        // 캐시 디렉토리에 복사
        val outFile = File(cacheDir, assetFileName)
        val outputStream = FileOutputStream(outFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        // FileProvider 통해 content Uri 생성
        FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // AndroidManifest와 일치해야 함
            outFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}