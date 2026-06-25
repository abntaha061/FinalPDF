package com.example.data

data class PdfBookmark(
    val title: String,
    val pageIdx: Long,
    val children: List<PdfBookmark> = emptyList()
)
