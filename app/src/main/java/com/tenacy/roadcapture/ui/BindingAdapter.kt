package com.tenacy.roadcapture.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.widget.EditText
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.util.toPx

@BindingAdapter("url", "bitmap", "uri", "radius", "borderWidth", "borderColor", requireAll = false)
fun ImageView.showImage(
    url: String?,
    bitmap: Bitmap?,
    uri: Uri?,
    radius: Int?,
    borderWidth: Int?,
    borderColor: Int?
) {
    if (url == null && bitmap == null && uri == null) return

    // 둥근 모서리가 있는 drawable 생성
    val shapeDrawable = GradientDrawable()
    shapeDrawable.shape = GradientDrawable.RECTANGLE
    shapeDrawable.cornerRadius = radius?.toFloat() ?: 0f // 픽셀 값
    shapeDrawable.color = ContextCompat.getColorStateList(context, R.color.fill_assistive) // 배경 색상

    Glide.with(context)
        .asBitmap()
        .let { request ->
            when {
                url != null -> request.load(url)
                bitmap != null -> request.load(bitmap)
                else -> request.load(uri)
            }
        }
        .placeholder(shapeDrawable)
        .transform(buildTransformations(radius, borderWidth, borderColor))
        .dontAnimate()
        .into(this)
}

fun buildTransformations(
    radius: Int? = null,
    borderWidth: Int? = null,
    borderColor: Int? = null
) = MultiTransformation(
    buildList {
        add(CenterCrop())
        radius?.takeIf { it > 0 }?.let {
            add(RoundedCorners(it))
        }
        borderWidth?.takeIf { it > 0 }?.let { width ->
            val color = borderColor ?: R.color.background_normal // 기본 테두리 색상
            // BorderedRoundedTransformation은 직접 만들어야 합니다 - 아래 제공
            add(BorderedRoundedTransformation(radius ?: 0, width.toPx, color))
        }
    }
)

@BindingAdapter("state")
fun EditText.setState(state: EditTextState?) {
    if(state == null) return
    background = when (state) {
        is EditTextState.Focused -> {
            ContextCompat.getDrawable(context, R.drawable.input_focused)
        }
        is EditTextState.Error -> {
            ContextCompat.getDrawable(context, R.drawable.input_error)
        }
        is EditTextState.Normal -> {
            ContextCompat.getDrawable(context, R.drawable.input_normal)
        }
    }
}