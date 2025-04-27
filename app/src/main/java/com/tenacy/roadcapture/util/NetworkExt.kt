package com.tenacy.roadcapture.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable.cancel
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> Task<T>.awaitResult(): Result<T> = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(Result.success(result)) { }
    }
    addOnFailureListener { exception ->
        continuation.resume(Result.failure(exception)) { }
    }
    addOnCanceledListener {
        continuation.cancel()
    }

    // 태스크가 취소될 때 코루틴도 취소되도록 설정
    continuation.invokeOnCancellation {
        if (isComplete) return@invokeOnCancellation
        cancel()
    }
}