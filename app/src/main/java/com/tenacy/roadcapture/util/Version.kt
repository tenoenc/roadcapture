package com.tenacy.roadcapture.util

data class Version(val versionString: String) : Comparable<Version> {
    private val parts: List<Int> by lazy {
        versionString.split(".")
            .map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
    }

    val major: Int get() = parts.getOrElse(0) { 0 }
    val minor: Int get() = parts.getOrElse(1) { 0 }
    val patch: Int get() = parts.getOrElse(2) { 0 }

    override fun compareTo(other: Version): Int {
        val maxLength = maxOf(this.parts.size, other.parts.size)

        for (i in 0 until maxLength) {
            val thisPart = this.parts.getOrElse(i) { 0 }
            val otherPart = other.parts.getOrElse(i) { 0 }

            val comparison = thisPart.compareTo(otherPart)
            if (comparison != 0) return comparison
        }

        return 0
    }

    override fun toString(): String = versionString

    companion object {
        fun parse(versionString: String): Version? {
            return try {
                Version(versionString)
            } catch (e: Exception) {
                null
            }
        }
    }
}
