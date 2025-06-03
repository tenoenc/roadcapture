package com.tenacy.roadcapture.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.util.toPx
import kotlinx.coroutines.*

@BindingAdapter("url", "bitmap", "uri", "src", "radius", "borderWidth", "borderColor", "coverColor", requireAll = false)
fun ImageView.showImage(
    url: String?,
    bitmap: Bitmap?,
    uri: Uri?,
    src: Int?,
    radius: Int?,
    borderWidth: Int?,
    borderColor: Int?,
    coverColor: Int?
) {
    if(src != null) {
        setImageResource(src)
        return
    }

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
                else -> request.load(uri!!)
            }
        }
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(shapeDrawable)
        .transform(buildTransformations(radius, borderWidth, borderColor, coverColor))
        .dontAnimate()
        .into(this)
}

fun buildTransformations(
    radius: Int? = null,
    borderWidth: Int? = null,
    borderColor: Int? = null,
    coverColor: Int? = null,
) = MultiTransformation(
    buildList {
        add(CenterCrop())
        radius?.takeIf { it > 0 }?.let {
            add(RoundedCorners(it.toPx))
        }
        borderWidth?.takeIf { it > 0 }?.let { width ->
            val color = borderColor ?: R.color.background_normal // 기본 테두리 색상
            // BorderedRoundedTransformation은 직접 만들어야 합니다 - 아래 제공
            add(BorderedRoundedTransformation(radius ?: 0, width.toPx, color))
        }
        coverColor?.takeIf { it > 0 }?.let { color ->
            add(OverlayTransformation(color))
        }
    }
)

@BindingAdapter("custom_tint")
fun ImageView.setCustomTint(@ColorInt color: Int) {
    if(color != 0) {
        imageTintList = ColorStateList.valueOf(color)
    } else {
        imageTintList = null
    }
}

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

@BindingAdapter("selected")
fun View.setSelected(selected: Boolean?) {
    selected?.let { setSelectedIncludingChildren(it) }
}

fun View.setSelectedIncludingChildren(selected: Boolean) {
    isSelected = selected
    if (this is ViewGroup) {
        children.forEach { child ->
            child.setSelectedIncludingChildren(selected)
        }
    }
}

@BindingAdapter("selectable", "singleSelection")
fun ViewGroup.enableSelection(selectable: Boolean?, singleSelection: Boolean?) {
    if(selectable != true) return

    when (singleSelection) {
        true -> setupSelectionContainer(true)   // 단일 선택
        false, null -> setupSelectionContainer(false)  // 다중 선택
    }
}

fun ViewGroup.setupSelectionContainer(
    singleSelection: Boolean = true,
    onSelectionChanged: ((List<Int>) -> Unit)? = null
) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        child.setSafeClickListener { clickedView ->
            if (singleSelection) {
                for (j in 0 until childCount) {
                    val otherChild = getChildAt(j)
                    if (otherChild != clickedView) {
                        otherChild.setSelectedIncludingChildren(false)
                    }
                }
                clickedView.setSelectedIncludingChildren(true)
            } else {
                clickedView.setSelectedIncludingChildren(!clickedView.isSelected)
            }

            // 선택 변경 콜백 호출
            onSelectionChanged?.invoke(getSelectedIndices())
        }
    }
}

// 선택된 모든 뷰들을 찾는 확장 함수 (다중 선택용)
fun ViewGroup.getSelectedViews(): List<View> {
    val selectedViews = mutableListOf<View>()
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child.isSelected) {
            selectedViews.add(child)
        }
    }
    return selectedViews
}

// 선택된 모든 인덱스를 찾는 확장 함수 (다중 선택용)
fun ViewGroup.getSelectedIndices(): List<Int> {
    val selectedIndices = mutableListOf<Int>()
    for (i in 0 until childCount) {
        if (getChildAt(i).isSelected) {
            selectedIndices.add(i)
        }
    }
    return selectedIndices
}

@BindingAdapter("debounceTime", "safeClick", requireAll = false)
fun View.setSafeClickListener(debounceTime: Long? = null, clickListener: View.OnClickListener?) {
    clickListener ?: return

    // 각 뷰마다 별도의 Job을 관리
    val scope = CoroutineScope(Dispatchers.Main)
    var clickJob: Job? = null

    setOnClickListener { view ->
        if (clickJob?.isActive != true) {
            clickJob = scope.launch {
                clickListener.onClick(view)
                delay(debounceTime?.takeIf { it > 0L } ?: 600L)
            }
        }
    }
}