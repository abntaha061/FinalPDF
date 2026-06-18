package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val sizeBytes: Long,
    val totalPages: Int,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val currentPage: Int = 0,
    val isBookmarked: Boolean = false,
    val isFavorite: Boolean = false
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val pageNumber: Int,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val pageNumber: Int,
    val selectedText: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val durationSeconds: Long,
    val pagesRead: Int,
    val date: Long
)

@Entity(tableName = "ocr_results")
data class OcrResultEntity(
    @PrimaryKey val fileUri: String,
    val extractedText: String,   // full text all pages concatenated
    val pageTexts: String,       // JSON array of per-page text
    val language: String,
    val createdAt: Long
)

@Entity(tableName = "audio_bookmarks")
data class AudioBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val pageNumber: Int,
    val audioPath: String,    // path to .aac file
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUri: String,
    val pageNumber: Int,
    val positionX: Float,
    val positionY: Float,
    val parentId: Int? = null,  // null = root comment, else reply
    val authorName: String = "أنا", // "أنا" by default
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)


