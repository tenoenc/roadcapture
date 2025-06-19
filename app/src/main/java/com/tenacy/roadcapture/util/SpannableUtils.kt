package com.tenacy.roadcapture.util

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.TextView

object SpannableUtils {

    fun setClickableText(
        context: Context,
        textView: TextView,
        fullText: String,
        clickableParts: List<ClickablePart>
    ) {
        val spannableString = SpannableString(fullText)

        // 각 클릭 가능한 부분에 대해 스팬 설정
        for (part in clickableParts) {
            // 검색 시작 위치부터 텍스트 검색
            val fromIndex = part.startIndex.coerceAtLeast(0)
            val startIndex = fullText.indexOf(part.text, fromIndex)

            // 해당 텍스트가 원본에 없으면 다음으로 넘어감
            if (startIndex == -1) continue

            val endIndex = startIndex + part.text.length

            // ClickableSpan 생성
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    part.onClickListener.invoke(context)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = part.textColor
                    ds.isUnderlineText = part.isUnderlined
                }
            }

            // 스팬 적용
            spannableString.setSpan(
                clickableSpan,
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 색상 스팬 추가
            spannableString.setSpan(
                ForegroundColorSpan(part.textColor),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 밑줄 스팬 (필요한 경우)
            if (part.isUnderlined) {
                spannableString.setSpan(
                    UnderlineSpan(),
                    startIndex,
                    endIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // TextView에 스팬 설정
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    data class ClickablePart(
        val text: String,
        val textColor: Int,
        val isUnderlined: Boolean = false,
        val startIndex: Int = 0, // 이 위치부터 text를 검색
        val onClickListener: (Context) -> Unit = {},
    )
}