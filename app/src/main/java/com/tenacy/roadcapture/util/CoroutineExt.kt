package com.tenacy.roadcapture.util

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay

suspend fun<E> SendChannel<E>.sendWithDelay(element: E, duration: Long = 150L) {
    send(element)
    delay(duration)
}

suspend fun <T : Any?> SavedStateHandle.consumeOnce(
    key: String,
    processor: suspend (T) -> Unit
) {
    val value = get<T>(key)
    if (value != null) {
        processor(value)
        set(key, null)
    }
}