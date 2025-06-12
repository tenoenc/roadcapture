package com.tenacy.roadcapture.data.firebase.exception

import java.time.LocalDateTime

open class AlbumLockedException(val lockReason: String, val lockedAt: LocalDateTime): RuntimeException()