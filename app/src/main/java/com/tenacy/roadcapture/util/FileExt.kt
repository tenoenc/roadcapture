package com.tenacy.roadcapture.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

suspend fun Context.downloadFile(
    fileUrl: String,
    fileName: String? = null
): File = withContext(Dispatchers.IO) {
    try {
        // 1. 파일명 결정 (제공된 이름 또는 URL에서 추출)
        val finalFileName = fileName ?: run {
            val uri = android.net.Uri.parse(fileUrl)
            uri.lastPathSegment ?: "downloaded_file_${System.currentTimeMillis()}"
        }

        // 2. 임시 파일 생성 (캐시 디렉토리에)
        val outputFile = File(cacheDir, finalFileName)

        // 3. URL 연결 설정
        val url = URL(fileUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 20000  // 20초 연결 타임아웃
        connection.readTimeout = 30000     // 30초 읽기 타임아웃
        connection.doInput = true
        connection.connect()

        // 4. 파일 다운로드
        val inputStream = connection.inputStream
        val outputStream = FileOutputStream(outputFile)

        val buffer = ByteArray(4096)
        var bytesRead: Int

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }

        // 5. 리소스 정리
        outputStream.close()
        inputStream.close()
        connection.disconnect()

        Log.d("FileDownloader", "파일 다운로드 완료: ${outputFile.absolutePath}")
        return@withContext outputFile

    } catch (e: Exception) {
        Log.e("FileDownloader", "파일 다운로드 실패", e)
        throw e
    }
}