package com.tenacy.roadcapture.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tenacy.roadcapture.databinding.ItemPhotoBinding

class PhotoSliderAdapter(
    private val photoUris: List<Uri>,
) : RecyclerView.Adapter<PhotoSliderAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photoUri: Uri) {
            binding.uri = photoUri
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder =
        PhotoViewHolder(ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUris[position])
    }

    override fun getItemCount(): Int = photoUris.size
}