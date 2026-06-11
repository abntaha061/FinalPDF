package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val size: Long,
    val lastAccessed: Long = System.currentTimeMillis(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isBookmarked: Boolean = false
)

@Entity(tableName = "pdf_page_bookmarks")
data class PdfPageBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfUri: String,
    val pageNumber: Int,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)
