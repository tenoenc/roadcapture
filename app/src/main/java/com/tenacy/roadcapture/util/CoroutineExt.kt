package com.tenacy.roadcapture.util

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay

suspend fun<E> SendChannel<E>.sendWithDelay(element: E, duration: Long = 150L) {
    send(element)
    delay(duration)
}