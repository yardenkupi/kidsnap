package com.childfilter.app.data

data class LogEntry(
    val timestamp: Long,
    val type: String,
    val details: String,
    val childName: String? = null
)
