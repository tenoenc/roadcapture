package com.tenacy.roadcapture.ui

/**
 * 이벤트를 나타내는 LiveData를 통해 데이터를 노출할 때 사용하는 래퍼 클래스
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // 외부에서는 읽기만 가능하고 쓰기는 불가능하도록 설정

    /**
     * 이미 처리된 적이 없으면 데이터를 반환하고, 처리된 것으로 표시함
     * 이미 처리된 경우 null을 반환
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * 처리 여부와 관계없이 데이터를 반환
     */
    fun peekContent(): T = content
}