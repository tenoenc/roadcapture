package com.tenacy.roadcapture.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

fun detectInappropriateContent(originalBitmap: Bitmap, callback: (Boolean) -> Unit) {
    // 이미지 리사이징 (1024x1024 이하로 유지하는 것이 권장됨)
    val resizedBitmap = resizeForMlKit(originalBitmap)

    val image = InputImage.fromBitmap(resizedBitmap, 0)

    // 객체 감지 옵션 설정
    val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()

    val objectDetector = ObjectDetection.getClient(options)

    objectDetector.process(image)
        .addOnSuccessListener { objects ->
            var containsInappropriate = false

            for (detectedObject in objects) {
                for (label in detectedObject.labels) {
                    // 여기서 부적절한 콘텐츠 관련 라벨을 확인
                    if (isInappropriateLabel(label.text, label.confidence)) {
                        containsInappropriate = true
                        break
                    }
                }
                if (containsInappropriate) break
            }

            // 원본 비트맵이 아닌 경우 메모리 해제
            if (resizedBitmap !== originalBitmap) {
                resizedBitmap.recycle()
            }

            callback(containsInappropriate)
        }
        .addOnFailureListener { e ->
            // 원본 비트맵이 아닌 경우 메모리 해제
            if (resizedBitmap !== originalBitmap) {
                resizedBitmap.recycle()
            }

            // 에러 처리
            callback(false) // 실패 시 기본적으로 안전하다고 가정
        }
}

/**
 * ML Kit 분석을 위한 이미지 리사이징 함수
 */
private fun resizeForMlKit(bitmap: Bitmap, maxDimension: Int = 1024): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    // 이미 적절한 크기면 그대로 반환
    if (width <= maxDimension && height <= maxDimension) {
        return bitmap
    }

    // 비율 계산
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int

    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt()
    }

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private fun isInappropriateLabel(labelText: String, confidence: Float): Boolean {
    val sensitiveLabels = listOf("swimwear", "underwear", "adult content")
    val confidenceThreshold = 0.7f
    return labelText in sensitiveLabels && confidence > confidenceThreshold
}