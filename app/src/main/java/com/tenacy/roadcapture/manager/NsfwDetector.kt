package com.tenacy.roadcapture.manager

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsfwDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODEL_FILE = "nsfw_mobile_uint8.onnx"
        private const val IMAGE_SIZE = 224
        private val LABELS = arrayOf("normal", "nsfw")
    }

    // ONNX Runtime 환경 및 세션
    private val ortEnvironment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment()
    }

    private val ortSession: OrtSession by lazy {
        val modelBytes = loadModelFromAssets()
        val sessionOptions = OrtSession.SessionOptions()
        ortEnvironment.createSession(modelBytes, sessionOptions)
    }

    // 모델 파일 로드
    private fun loadModelFromAssets(): ByteArray {
        context.assets.open(MODEL_FILE).use { inputStream ->
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes)
            return bytes
        }
    }

    // 이미지 전처리 (NCHW 형식으로 변환)
    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val buffer = FloatBuffer.allocate(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)

        // 배치 차원 추가, 3 채널, 높이, 너비 (NCHW 형식)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        for (c in 0 until 3) {  // 채널별로 처리 (RGB)
            for (y in 0 until IMAGE_SIZE) {
                for (x in 0 until IMAGE_SIZE) {
                    val pixel = pixels[y * IMAGE_SIZE + x]
                    // RGB 채널 추출 및 [0, 1] 범위로 정규화
                    val value = when (c) {
                        0 -> (pixel shr 16 and 0xFF) / 255.0f  // R
                        1 -> (pixel shr 8 and 0xFF) / 255.0f   // G
                        else -> (pixel and 0xFF) / 255.0f      // B
                    }
                    // NCHW 형식으로 저장: 배치, 채널, 높이, 너비
                    buffer.put(0 * 3 * IMAGE_SIZE * IMAGE_SIZE + c * IMAGE_SIZE * IMAGE_SIZE + y * IMAGE_SIZE + x, value)
                }
            }
        }

        return buffer
    }

    // 소프트맥스 함수
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exp = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }

    // NSFW 감지 결과 데이터 클래스
    data class NSFWResult(
        val label: String,
        val isNSFW: Boolean,
        val confidence: Float,
        val allScores: Map<String, Float>
    )

    // 이미지 분류 함수
    fun detectNSFW(bitmap: Bitmap): NSFWResult {
        try {
            // 이미지 전처리
            val inputBuffer = preprocessImage(bitmap)

            // 입력 텐서 생성
            val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, shape)

            // 모델 입력 이름 가져오기
            val inputName = ortSession.inputNames.iterator().next()

            // 추론 실행
            val inputs = mapOf(inputName to inputTensor)
            val output = ortSession.run(inputs)

            // 결과 처리
            val outputTensor = output.get(0).value as Array<*>
            val logits = (outputTensor[0] as FloatArray)

            // 소프트맥스 적용
            val scores = softmax(logits)

            // 결과 해석
            val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
            val predictedLabel = LABELS[maxIndex]
            val isNSFW = predictedLabel == "nsfw"
            val confidence = scores[maxIndex]

            // 클래스별 확률
            val allScores = LABELS.mapIndexed { index, label ->
                label to scores[index]
            }.toMap()

            return NSFWResult(
                label = predictedLabel,
                isNSFW = isNSFW,
                confidence = confidence,
                allScores = allScores
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // 오류 발생 시 기본값 반환
            return NSFWResult(
                label = "error",
                isNSFW = false,
                confidence = 0f,
                allScores = mapOf("error" to 1f)
            )
        } finally {
            // 리소스 해제는 close() 메서드에서 처리
        }
    }

    // 리소스 해제
    fun close() {
        try {
            ortSession.close()
            ortEnvironment.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}