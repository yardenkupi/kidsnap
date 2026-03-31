package com.childfilter.app.data

data class ChildProfile(
    val id: String,
    val name: String,
    val embedding: FloatArray,
    val photoUri: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChildProfile) return false
        return id == other.id && name == other.name &&
                embedding.contentEquals(other.embedding) && photoUri == other.photoUri
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (photoUri?.hashCode() ?: 0)
        return result
    }
}
