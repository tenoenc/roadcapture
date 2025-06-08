package com.tenacy.roadcapture.util

import com.tenacy.roadcapture.BuildConfig

object FirebaseConstants {
    const val DEFAULT_PROFILE_PATH = "images/default_profile.jpg"
    const val DEFAULT_PROFILE_URL = "https://firebasestorage.googleapis.com/v0/b/roadcapture-457911.firebasestorage.app/o/images%2Fdefault_profile.jpg?alt=media&token=3c26d0bf-47d1-48f5-b37c-71826eab75a7"

    val COLLECTION_USERS = /*if(BuildConfig.DEBUG) "dev-users" else */"users"
    val COLLECTION_ALBUMS = /*if(BuildConfig.DEBUG) "dev-albums" else */"albums"
    val COLLECTION_MEMORIES = /*if(BuildConfig.DEBUG) "dev-memories" else */"memories"
    val COLLECTION_LOCATIONS = /*if(BuildConfig.DEBUG) "dev-locations" else */"locations"
    val COLLECTION_SCRAPS = /*if(BuildConfig.DEBUG) "dev-scraps" else */"scraps"
    val COLLECTION_REPORTS = /*if(BuildConfig.DEBUG) "dev-reports" else */"reports"
}