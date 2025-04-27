package com.tenacy.roadcapture.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Fragment.launchOnLifecycle(dispatcher: CoroutineDispatcher = Dispatchers.Main, lifecycleState: Lifecycle.State = Lifecycle.State.STARTED, block: suspend CoroutineScope.() -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch(dispatcher) {
        viewLifecycleOwner.repeatOnLifecycle(lifecycleState) {
            block()
        }
    }
}

fun AppCompatActivity.launchOnLifecycle(dispatcher: CoroutineDispatcher = Dispatchers.Main, lifecycleState: Lifecycle.State = Lifecycle.State.STARTED, block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(dispatcher) {
        repeatOnLifecycle(lifecycleState) {
            block()
        }
    }
}