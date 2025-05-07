package com.tenacy.roadcapture.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.databinding.ItemPhotoBinding

class PhotoSliderAdapter(
    private val photoUris: List<Uri> = emptyList(),
    private val photoUrls: List<String> = emptyList(),
) : RecyclerView.Adapter<PhotoSliderAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photoUrl: String) {
            binding.url = photoUrl
        }
        fun bind(photoUri: Uri) {
            binding.uri = photoUri
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        when {
            photoUris.isNotEmpty() -> photoUris.getOrNull(position)?.let { holder.bind(it) }
            photoUrls.isNotEmpty() -> photoUrls.getOrNull(position)?.let { holder.bind(it) }
        }
    }

    override fun getItemCount(): Int = when {
        photoUris.isNotEmpty() -> photoUris.size
        photoUrls.isNotEmpty() -> photoUrls.size
        else -> 0
    }
}