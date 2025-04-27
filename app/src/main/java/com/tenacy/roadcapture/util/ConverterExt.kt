package com.tenacy.roadcapture.util

import android.content.res.Resources
import android.util.TypedValue

val Number.toPx
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

val Number.toDp
    get() = this.toFloat() / Resources.getSystem().displayMetrics.density