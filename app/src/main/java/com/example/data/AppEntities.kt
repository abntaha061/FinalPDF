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
    val isBookmarked: Boolean = false
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
