package com.tenacy.roadcapture.ui

import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import com.tenacy.roadcapture.R
import com.tenacy.roadcapture.databinding.ToastBinding
import com.tenacy.roadcapture.util.toPx
import kotlinx.parcelize.Parcelize

object MyToast {

    private fun create(context: Context, message: String, type: ToastMessageType, yOffset: Int): Toast {
        val inflater = LayoutInflater.from(context)
        val binding: ToastBinding =
            DataBindingUtil.inflate(inflater, R.layout.toast, null, false)

        binding.type = type
        binding.message = message

        binding.cardToastContainer.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))

        return Toast(context).apply {
            setGravity(Gravity.TOP or Gravity.CENTER, 0, yOffset.toPx)
            duration = Toast.LENGTH_SHORT
            view = binding.root
        }
    }

    fun info(context: Context, message: String, yOffset: Int = 60): Toast {
        return create(context, message, ToastMessageType.Info, yOffset)
    }

    fun warn(context: Context, message: String, yOffset: Int = 60): Toast {
        return create(context, message, ToastMessageType.Warning, yOffset)
    }

    fun success(context: Context, message: String, yOffset: Int = 60): Toast {
        return create(context, message, ToastMessageType.Success, yOffset)
    }
}

@BindingAdapter("type_backgroundColor")
fun View.setTypedBackgroundColor(type: ToastMessageType?) {
    if(type == null) return
    when(type) {
        is ToastMessageType.Info -> setBackgroundColor(Color.parseColor("#B3FCFEFF"))
        is ToastMessageType.Warning -> setBackgroundColor(Color.parseColor("#B3FF7A5C"))
        is ToastMessageType.Success -> setBackgroundColor(Color.parseColor("#B380F0D4"))
    }
}

@BindingAdapter("type_textColor")
fun TextView.setTypedTextColor(type: ToastMessageType?) {
    if(type == null) return
    when(type) {
        is ToastMessageType.Info -> setTextColor(ContextCompat.getColor(context, R.color.label_normal))
        is ToastMessageType.Warning -> setTextColor(ContextCompat.getColor(context, R.color.background_normal))
        is ToastMessageType.Success -> setTextColor(ContextCompat.getColor(context, R.color.label_normal))
    }
}

@Parcelize
data class ToastModel(
    val message: String,
    val type: ToastMessageType = ToastMessageType.Info,
): Parcelable

@Parcelize
sealed class ToastMessageType: Parcelable {
    data object Success : ToastMessageType()
    data object Info : ToastMessageType()
    data object Warning : ToastMessageType()
}