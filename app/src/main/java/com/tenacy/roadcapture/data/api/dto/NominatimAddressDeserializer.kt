package com.tenacy.roadcapture.data.api.dto

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class NominatimAddressDeserializer : JsonDeserializer<NominatimAddress> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): NominatimAddress {
        val jsonObject = json.asJsonObject
        val otherFields = LinkedHashMap<String, String>()

        // 기본 필드 추출
        val country = jsonObject.get("country")?.asString ?: ""

        // 모든 필드를 순회하며 지정된 필드 외의 것들을 otherFields에 저장
        jsonObject.entrySet().forEach { (key, value) ->
            if (key != "country" && value.isJsonPrimitive) {
                otherFields[key] = value.asString
            }
        }

        return NominatimAddress(country, otherFields)
    }
}