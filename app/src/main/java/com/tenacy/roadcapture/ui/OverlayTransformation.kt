package com.tenacy.roadcapture.ui

import android.graphics.*
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.charset.Charset
import java.security.MessageDigest

class OverlayTransformation(private val overlayColor: Int) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val result = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 원본 이미지 그리기
        canvas.drawBitmap(toTransform, 0f, 0f, null)

        // 알파 채널이 있는 색상 오버레이 적용
        val paint = Paint().apply {
            color = overlayColor
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }

        // SRC_ATOP 모드는 원본의 알파 채널을 보존하면서 색상 적용
        canvas.drawRect(0f, 0f, toTransform.width.toFloat(), toTransform.height.toFloat(), paint)

        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(("overlay_" + overlayColor).toByteArray(CHARSET))
    }

    companion object {
        private val CHARSET = Charset.forName("UTF-8")
        private const val ID = "com.your.package.OverlayTransformation"
    }
}