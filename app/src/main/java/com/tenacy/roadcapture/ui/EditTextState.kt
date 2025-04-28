package com.tenacy.roadcapture.ui

sealed class EditTextState {
    data object Normal : EditTextState()
    data object Focused : EditTextState()
    data object Error : EditTextState()
}