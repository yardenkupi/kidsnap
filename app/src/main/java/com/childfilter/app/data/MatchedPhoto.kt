package com.childfilter.app.data

import android.net.Uri

data class MatchedPhoto(
    val uri: Uri,
    val timestamp: Long,
    val similarity: Float
)
