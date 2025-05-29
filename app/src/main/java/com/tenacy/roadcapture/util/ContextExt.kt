package com.tenacy.roadcapture.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*

val Fragment.viewLifeCycleOwnerOrNull get() = try {
    viewLifecycleOwner
} catch (exception: IllegalStateException) {
    null
}

fun Fragment.repeatOnLifecycle(dispatcher: CoroutineDispatcher = Dispatchers.Main, lifecycleState: Lifecycle.State = Lifecycle.State.STARTED, block: suspend CoroutineScope.() -> Unit): Job? {
    return viewLifeCycleOwnerOrNull?.lifecycleScope?.launch(dispatcher) {
        viewLifecycleOwner.repeatOnLifecycle(lifecycleState) {
            block()
        }
    }
}

fun AppCompatActivity.repeatOnLifecycle(dispatcher: CoroutineDispatcher = Dispatchers.Main, lifecycleState: Lifecycle.State = Lifecycle.State.STARTED, block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(dispatcher) {
        this@repeatOnLifecycle.repeatOnLifecycle(lifecycleState) {
            block()
        }
    }
}