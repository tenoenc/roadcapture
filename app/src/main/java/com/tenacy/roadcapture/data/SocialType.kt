package com.tenacy.roadcapture.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class SocialType(val name: String): Parcelable {
    @Parcelize
    data object Kakao : SocialType("kakao")
    @Parcelize
    data object Naver: SocialType("naver")
    @Parcelize
    data object Facebook: SocialType("facebook")
    @Parcelize
    data object Google: SocialType("google")

    companion object {
        fun of(name: String) =
            when (name) {
                Kakao.name -> Kakao
                Facebook.name -> Facebook
                Naver.name -> Naver
                else -> Google
            }
    }
}