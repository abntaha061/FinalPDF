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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(highlights: List<HighlightEntity>)
}

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE fileUri = :uri ORDER BY date DESC")
    fun getSessionsByUri(uri: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions")
    fun getTotalReadingTime(): Flow<Long>

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAll()
}

@Dao
interface OcrResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ocrResult: OcrResultEntity)

    @Query("SELECT * FROM ocr_results WHERE fileUri = :uri LIMIT 1")
    suspend fun getByFileUri(uri: String): OcrResultEntity?

    @Query("SELECT * FROM ocr_results")
    fun getAll(): Flow<List<OcrResultEntity>>

    @Query("DELETE FROM ocr_results WHERE fileUri = :uri")
    suspend fun deleteByFileUri(uri: String)

    @Query("DELETE FROM ocr_results")
    suspend fun deleteAll()
}

@Dao
interface AudioBookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audioBookmark: AudioBookmarkEntity)

    @Delete
    suspend fun delete(audioBookmark: AudioBookmarkEntity)

    @Query("SELECT * FROM audio_bookmarks WHERE fileUri = :uri ORDER BY pageNumber ASC")
    fun getByUri(uri: String): Flow<List<AudioBookmarkEntity>>

    @Query("DELETE FROM audio_bookmarks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM audio_bookmarks")
    suspend fun deleteAll()
}

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity)

    @Delete
    suspend fun delete(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE id = :id OR parentId = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM comments WHERE fileUri = :uri ORDER BY createdAt ASC")
    fun getByUri(uri: String): Flow<List<CommentEntity>>

    @Query("DELETE FROM comments")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<CommentEntity>)
}



