package com.tenacy.roadcapture.ui.dto

import java.util.UUID

sealed class AlbumItemWithAds {
    /**
     * 앨범 데이터를 담는 아이템
     */
    sealed class Album(
        open val id: String,
        open val value: com.tenacy.roadcapture.ui.dto.Album,
        open val onItemClick: () -> Unit = {},
    ) : AlbumItemWithAds() {

        data class General(
            override val value: com.tenacy.roadcapture.ui.dto.Album,
            override val onItemClick: () -> Unit = {},
            val onProfileClick: () -> Unit = {},
            val onLongClick: (String) -> Unit = {},
        ): Album(value.id, value, onItemClick)

        data class User(
            override val value: com.tenacy.roadcapture.ui.dto.Album,
            override val onItemClick: () -> Unit = {},
            val onMoreClick: (com.tenacy.roadcapture.ui.dto.Album) -> Unit = {},
        ): Album(value.id, value, onItemClick)
    }

    /**
     * 광고 아이템
     */
    data class Ad(
        val id: String = UUID.randomUUID().toString(),
        val position: Int = -1
    ) : AlbumItemWithAds()
}