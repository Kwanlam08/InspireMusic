package com.applemusic.clone.model

data class DiaryAiLog(
    val id: String,
    val modeKey: String,
    val modeLabel: String,
    val summaryKey: String,
    val summaryLabel: String,
    val summaryText: String,
    val prompt: String,
    val result: String,
    val personalNote: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
