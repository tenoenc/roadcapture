package com.tenacy.roadcapture.data.firebase.dto

import org.json.JSONObject

data class SearchResponse(
    val hits: List<JSONObject>,
    val page: Int,
    val nbHits: Int,
    val nbPages: Int
) {
    companion object {
        fun fromJson(jsonObject: JSONObject): SearchResponse {
            val hitsArray = jsonObject.getJSONArray("hits")
            val hits = List(hitsArray.length()) { i -> hitsArray.getJSONObject(i) }

            return SearchResponse(
                hits = hits,
                page = jsonObject.optInt("page", 0),
                nbHits = jsonObject.optInt("nbHits", 0),
                nbPages = jsonObject.optInt("nbPages", 0)
            )
        }
    }
}