package com.tenacy.roadcapture.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job

abstract class BaseViewHolder<in T>(itemView: View): RecyclerView.ViewHolder(itemView) {
    open var job: Job? = null
    abstract fun bind(position: Int, item: T)
}