package com.tenacy.roadcapture.ui.dto

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Timezone(
    val id: Int,
    val value: String,
    val abbr: String,
    val offset: Double,
    val isdst: Boolean,
    val text: String,
    val flag: String,
    val key: String,
    val utc: List<String>,
): Parcelable

data class SearchableTimezone(
    val id: Int,
    val originalValue: String,
    val originalText: String,
    val flag: String,
    val localizedName: String,
    val utcText: String,
    val allLocalizedNames: Map<String, String> = emptyMap(),
    val isFiltered: Boolean = true,
    val isSelected: Boolean = false,
) {
    fun matchesQuery(query: String): Boolean {
        val normalizedQuery = query.lowercase().trim()
        return originalValue.lowercase().contains(normalizedQuery) ||
                originalText.lowercase().contains(normalizedQuery) ||
                localizedName.lowercase().contains(normalizedQuery) ||
                allLocalizedNames.values.any { it.lowercase().contains(normalizedQuery) }
    }
}