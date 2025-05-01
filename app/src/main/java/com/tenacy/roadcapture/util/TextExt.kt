package com.tenacy.roadcapture.util

fun String.containsDigit(): Boolean {
    return this.any { it.isDigit() }
}

fun String.containsLetter(): Boolean {
    return this.any { it.isLetter() }
}

fun String.containsSpecialChar(): Boolean {
    return this.any {
        !it.isLetterOrDigit() && !it.isWhitespace()
    }
}