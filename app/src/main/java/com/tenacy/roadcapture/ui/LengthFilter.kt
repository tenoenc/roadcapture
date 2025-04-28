package com.tenacy.roadcapture.ui

import android.text.InputFilter
import android.text.Spanned

class LengthFilter(private val maxLength: Int) : InputFilter {
    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        if (source == null || dest == null) return null

        val futureLength = dest.length - (dend - dstart) + (end - start)
        return if (futureLength > maxLength) {
            // 남은 길이 계산
            val remainingLength = maxLength - (dest.length - (dend - dstart))
            if (remainingLength <= 0) {
                ""
            } else {
                // maxLength까지만 잘라서 반환
                source.subSequence(start, start + remainingLength)
            }
        } else {
            null
        }
    }
}