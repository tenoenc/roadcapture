package com.tenacy.roadcapture.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.EditText
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.util.toPx

@BindingAdapter("url", "bitmap", "uri", "radius", requireAll = false)
fun ImageView.showImage(url: String?, bitmap: Bitmap?, uri: Uri?, radius: Int?) {
    if (url == null && bitmap == null && uri == null) return

    Glide.with(context)
        .asBitmap()
        .let { request ->
            when {
                url != null -> request.load(url)
                bitmap != null -> request.load(bitmap)
                else -> request.load(uri)
            }
        }
        .transform(buildTransformations(radius))
        .dontAnimate()
        .placeholder(drawable)
        .into(this)
}

fun buildTransformations(radius: Int?) = MultiTransformation(
    buildList {
        add(CenterCrop())
        radius?.takeIf { it > 0 }?.let {
            add(RoundedCorners(it.toPx))
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