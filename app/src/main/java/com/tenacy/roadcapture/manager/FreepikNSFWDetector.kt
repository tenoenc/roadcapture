package com.tenacy.roadcapture.manager

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class FreepikNSFWDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val MODEL_FILENAME = "freepik_nsfw_detector.onnx"
        private const val IMAGE_SIZE = 448
        private const val NSFW_THRESHOLD = 0.5f

        // 레이블 정의
        private val LABELS = arrayOf("neutral", "low", "medium", "high")

        // ImageNet 정규화 파라미터
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    init {
        prepareModel()
    }

    private fun prepareModel() {
        try {
            // 모델 파일이 내부 저장소에 없다면 assets에서 복사
            val modelFile = File(context.filesDir, MODEL_FILENAME)

            if (!modelFile.exists()) {
                context.assets.open(MODEL_FILENAME).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // 세션 옵션 설정 (최적화)
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setInterOpNumThreads(2)
            sessionOptions.setIntraOpNumThreads(2)

            // ONNX Runtime 세션 생성
            session = ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // 이미지 전처리
    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        // 이미지 크기 조정
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)

        // 입력 텐서용 FloatBuffer 생성 (NCHW 형식: 1 x 3 x 448 x 448)
        val floatBuffer = FloatBuffer.allocate(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)

        // 비트맵을 픽셀 배열로 변환
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        // 채널별 처리 (NCHW 형식)
        for (channel in 0 until 3) {
            for (y in 0 until IMAGE_SIZE) {
                for (x in 0 until IMAGE_SIZE) {
                    val pixel = pixels[y * IMAGE_SIZE + x]

                    // RGB 채널별 값 추출 (Android에서는 ARGB 형식)
                    val value = when(channel) {
                        0 -> (pixel shr 16 and 0xFF) / 255.0f // R
                        1 -> (pixel shr 8 and 0xFF) / 255.0f  // G
                        else -> (pixel and 0xFF) / 255.0f     // B
                    }

                    // ImageNet 정규화 적용
                    val normalizedValue = (value - MEAN[channel]) / STD[channel]

                    // NCHW 형식으로 인덱싱
                    val index = channel * IMAGE_SIZE * IMAGE_SIZE + y * IMAGE_SIZE + x
                    floatBuffer.put(index, normalizedValue)
                }
            }
        }

        return floatBuffer
    }

    // 소프트맥스
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expValues = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sumExp = expValues.sum()

        return expValues.map { it / sumExp }.toFloatArray()
    }

    // 누적 확률 계산
    private fun calculateCumulativeProbs(probs: FloatArray): Map<String, Float> {
        val output = mutableMapOf<String, Float>()
        var dangerCumSum = 0f

        for (j in probs.indices.reversed()) {
            dangerCumSum += probs[j]
            if (j == 0) {
                dangerCumSum = probs[j]
            }
            output[LABELS[j]] = dangerCumSum
        }

        return output
    }

    data class NSFWDetectionResult(
        val isNSFW: Boolean,
        val confidenceScores: Map<String, Float>,
        val rawProbabilities: Map<String, Float>,
        val highestCategory: String,
        val highestConfidence: Float
    )

    // NSFW 감지
    suspend fun detectNSFW(bitmap: Bitmap): NSFWDetectionResult = withContext(Dispatchers.Default) {
        require(session != null) { "ONNX 모델이 초기화되지 않았습니다" }

        try {
            // 이미지 전처리
            val inputBuffer = preprocess(bitmap)

            // 입력 텐서 생성
            val shape = longArrayOf(1, 3, IMAGE_SIZE.toLong(), IMAGE_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, shape)

            // 모델 실행
            val inputs = mapOf("input" to inputTensor)
            val output = session!!.run(inputs)

            // 결과 처리
            val logits = (output.get(0).value as Array<*>)[0] as FloatArray
            val rawProbs = softmax(logits)

            // 원시 확률 맵 생성
            val rawProbabilities = LABELS.mapIndexed { index, label ->
                label to rawProbs[index]
            }.toMap()

            // 누적 확률 계산
            val cumulativeProbs = calculateCumulativeProbs(rawProbs)

            // 가장 높은 카테고리 찾기
            val highestEntry = rawProbabilities.maxByOrNull { it.value }!!

            // NSFW 여부 판단 (neutral이 아닌 다른 레벨이 threshold 이상이면 NSFW로 간주)
            val isNSFW = cumulativeProbs.entries
                .filter { it.key != "neutral" }
                .any { it.value >= NSFW_THRESHOLD }

            // 리소스 해제
            inputTensor.close()
            output.close()

            NSFWDetectionResult(
                isNSFW = isNSFW,
                confidenceScores = cumulativeProbs,
                rawProbabilities = rawProbabilities,
                highestCategory = highestEntry.key,
                highestConfidence = highestEntry.value
            )

        } catch (e: Exception) {
            e.printStackTrace()

            // 오류 발생 시 기본값 반환
            NSFWDetectionResult(
                isNSFW = false,
                confidenceScores = emptyMap(),
                rawProbabilities = emptyMap(),
                highestCategory = "neutral",
                highestConfidence = 1.0f
            )
        }
    }

    fun close() {
        try {
            session?.close()
            ortEnvironment.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}