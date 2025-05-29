package com.tenacy.roadcapture.manager

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSFWDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NSFWDetector"
        private const val MODEL_FILE = "nsfw_mobile_uint8.onnx"
        private const val IMAGE_SIZE = 224
        private val LABELS = arrayOf("normal", "nsfw")
    }

    // ONNX Runtime 환경 및 세션을 안전하게 초기화
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false

    // 초기화 함수 추가
    fun initialize() {
        if (isInitialized) return

        try {
            // ONNX Runtime 환경 초기화
            ortEnvironment = OrtEnvironment.getEnvironment()

            // 모델 로드
            val modelBytes = loadModelFromAssets()
            val sessionOptions = OrtSession.SessionOptions()
            // 스레드 수 제한 및 최적화 설정
            sessionOptions.setIntraOpNumThreads(2)

            // 세션 생성
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
            isInitialized = true
            Log.d(TAG, "ONNX Runtime successfully initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ONNX Runtime: ${e.message}", e)
            // 초기화 실패 시 리소스 정리
            close()
        }
    }

    // 모델 파일 로드 (예외 처리 추가)
    private fun loadModelFromAssets(): ByteArray {
        try {
            context.assets.open(MODEL_FILE).use { inputStream ->
                val bytes = ByteArray(inputStream.available())
                inputStream.read(bytes)
                return bytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model from assets: ${e.message}", e)
            throw RuntimeException("Failed to load model: ${e.message}", e)
        }
    }

    // 이미지 전처리 (예외 처리 추가)
    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error preprocessing image: ${e.message}", e)
            throw RuntimeException("Image preprocessing failed: ${e.message}", e)
        }
    }

    // 소프트맥스 함수
    private fun softmax(logits: FloatArray): FloatArray {
        try {
            val max = logits.maxOrNull() ?: 0f
            val exp = logits.map { Math.exp((it - max).toDouble()).toFloat() }
            val sum = exp.sum()
            return exp.map { it / sum }.toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error in softmax calculation: ${e.message}", e)
            // 오류 발생 시 기본값 반환
            return FloatArray(logits.size) { if (it == 0) 1f else 0f }
        }
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
        // 초기화 확인 및 필요시 초기화
        if (!isInitialized) {
            try {
                initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Could not initialize detector: ${e.message}", e)
                return errorResult("initialization_failed")
            }
        }

        // 세션이 여전히 null이면 오류 반환
        if (ortSession == null || ortEnvironment == null) {
            return errorResult("session_not_available")
        }

        var inputTensor: OnnxTensor? = null

        try {
            // 이미지 전처리
            val inputBuffer = preprocessImage(bitmap)

            // 입력 텐서 생성
            val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
            inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, shape)

            // 모델 입력 이름 가져오기
            val inputName = ortSession?.inputNames?.iterator()?.next()
                ?: throw RuntimeException("Failed to get input name")

            // 추론 실행
            val inputs = mapOf(inputName to inputTensor)
            val output = ortSession?.run(inputs)
                ?: throw RuntimeException("Inference failed: session.run returned null")

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
            Log.e(TAG, "Error detecting NSFW content: ${e.message}", e)
            return errorResult(e.javaClass.simpleName)
        } finally {
            // 텐서 리소스 해제
            try {
                inputTensor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing tensor: ${e.message}", e)
            }
        }
    }

    // 오류 결과 생성 헬퍼 메서드
    private fun errorResult(errorType: String): NSFWResult {
        return NSFWResult(
            label = "error:$errorType",
            isNSFW = false,
            confidence = 0f,
            allScores = mapOf("error" to 1f, "normal" to 0f, "nsfw" to 0f)
        )
    }

    // 리소스 해제
    fun close() {
        try {
            ortSession?.close()
            ortSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session: ${e.message}", e)
        }

        try {
            ortEnvironment?.close()
            ortEnvironment = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing environment: ${e.message}", e)
        }

        isInitialized = false
    }
}