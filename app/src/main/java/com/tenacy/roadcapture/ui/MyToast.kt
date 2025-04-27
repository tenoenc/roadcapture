package com.tenacy.roadcapture.ui

import android.content.Context
import android.os.Parcelable
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.ContextCompat
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