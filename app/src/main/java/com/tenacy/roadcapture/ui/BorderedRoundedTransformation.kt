package com.tenacy.roadcapture.ui

import android.graphics.*
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class BorderedRoundedTransformation(
    private val radius: Int,
    private val borderWidth: Int,
    private val borderColor: Int
) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val bitmap = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true

        // 배경 그리기
        paint.color = Color.TRANSPARENT
        canvas.drawRect(0f, 0f, outWidth.toFloat(), outHeight.toFloat(), paint)

        // 이미지 그리기
        val rect = RectF(
            borderWidth.toFloat(),
            borderWidth.toFloat(),
            outWidth.toFloat() - borderWidth,
            outHeight.toFloat() - borderWidth
        )

        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), paint)

        // 소스 이미지 그리기
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(toTransform, null, rect, paint)

        // 테두리 그리기
        paint.xfermode = null
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidth.toFloat()

        // 테두리를 그릴 위치 (strokeWidth/2 만큼 안쪽으로)
        val borderRect = RectF(
            borderWidth / 2f,
            borderWidth / 2f,
            outWidth.toFloat() - borderWidth / 2f,
            outHeight.toFloat() - borderWidth / 2f
        )
        canvas.drawRoundRect(borderRect, radius.toFloat(), radius.toFloat(), paint)

        return bitmap
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BorderedRoundedTransformation

        if (radius != other.radius) return false
        if (borderWidth != other.borderWidth) return false
        if (borderColor != other.borderColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = radius
        result = 31 * result + borderWidth
        result = 31 * result + borderColor
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(("BorderedRoundedTransformation(radius=$radius,borderWidth=$borderWidth,borderColor=$borderColor)").toByteArray())
    }
}