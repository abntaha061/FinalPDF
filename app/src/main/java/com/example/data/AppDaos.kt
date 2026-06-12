package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recentFile: RecentFileEntity)

    @Delete
    suspend fun delete(recentFile: RecentFileEntity)

    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC")
    fun getAll(): Flow<List<RecentFileEntity>>

    @Query("""
      SELECT * FROM recent_files 
      WHERE sizeBytes BETWEEN :minSize AND :maxSize
      AND totalPages BETWEEN :minPages AND :maxPages
      AND lastOpenedAt >= :minDate
      ORDER BY lastOpenedAt DESC
    """)
    fun getFiltered(
        minSize: Long,
        maxSize: Long,
        minPages: Int,
        maxPages: Int,
        minDate: Long
    ): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): RecentFileEntity?

    @Query("UPDATE recent_files SET currentPage = :currentPage, totalPages = :totalPages, lastOpenedAt = :lastOpenedAt WHERE uri = :uri")
    suspend fun updateProgress(uri: String, currentPage: Int, totalPages: Int, lastOpenedAt: Long)

    @Query("UPDATE recent_files SET isBookmarked = :isBookmarked WHERE uri = :uri")
    suspend fun updateBookmarkState(uri: String, isBookmarked: Boolean)

    @Query("DELETE FROM recent_files")
    suspend fun deleteAll()
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE fileUri = :uri ORDER BY pageNumber ASC")
    fun getByUri(uri: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE fileUri = :fileUri AND pageNumber = :pageNumber")
    suspend fun deleteByPage(fileUri: String, pageNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE fileUri = :fileUri AND pageNumber = :pageNumber)")
    suspend fun hasBookmark(fileUri: String, pageNumber: Int): Boolean

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity)

    @Delete
    suspend fun delete(highlight: HighlightEntity)

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC")
    fun getAll(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE fileUri = :uri ORDER BY createdAt DESC")
    fun getByUri(uri: String): Flow<List<HighlightEntity>>

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM highlights")
    suspend fun deleteAll()
}
