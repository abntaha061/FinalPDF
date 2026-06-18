package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    private val recentFileDao: RecentFileDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val readingSessionDao: ReadingSessionDao,
    private val ocrResultDao: OcrResultDao,
    private val audioBookmarkDao: AudioBookmarkDao,
    private val commentDao: CommentDao
) {
    val allRecentPdfs: Flow<List<RecentFileEntity>> = recentFileDao.getAll()
    
    // Comments
    fun getCommentsForPdf(fileUri: String): Flow<List<CommentEntity>> {
        return commentDao.getByUri(fileUri)
    }

    suspend fun insertComment(comment: CommentEntity) = withContext(Dispatchers.IO) {
        commentDao.insert(comment)
    }

    suspend fun deleteComment(commentId: Int) = withContext(Dispatchers.IO) {
        commentDao.deleteById(commentId)
    }

    suspend fun clearAllComments() = withContext(Dispatchers.IO) {
        commentDao.deleteAll()
    }

    suspend fun insertComments(comments: List<CommentEntity>) = withContext(Dispatchers.IO) {
        commentDao.insertAll(comments)
    }

    
    // Reading Sessions
    val allReadingSessions: Flow<List<ReadingSessionEntity>> = readingSessionDao.getAllSessions()
    val totalReadingTime: Flow<Long> = readingSessionDao.getTotalReadingTime()

    fun getReadingSessionsForUri(fileUri: String): Flow<List<ReadingSessionEntity>> {
        return readingSessionDao.getSessionsByUri(fileUri)
    }

    suspend fun insertReadingSession(session: ReadingSessionEntity) = withContext(Dispatchers.IO) {
        readingSessionDao.insert(session)
    }

    suspend fun clearAllReadingSessions() = withContext(Dispatchers.IO) {
        readingSessionDao.deleteAll()
    }
    
    fun getFilteredPdfs(
        minSize: Long,
        maxSize: Long,
        minPages: Int,
        maxPages: Int,
        minDate: Long
    ): Flow<List<RecentFileEntity>> {
        return recentFileDao.getFiltered(minSize, maxSize, minPages, maxPages, minDate)
    }
    
    val bookmarkedPdfs: Flow<List<RecentFileEntity>> = recentFileDao.getAll().map { list ->
        list.filter { it.isBookmarked }
    }

    suspend fun getPdfByUri(uri: String): RecentFileEntity? = withContext(Dispatchers.IO) {
        recentFileDao.getByUri(uri)
    }

    suspend fun insertPdf(pdf: RecentFileEntity) = withContext(Dispatchers.IO) {
        recentFileDao.insert(pdf)
    }

    suspend fun updatePdfProgress(uri: String, currentPage: Int, totalPages: Int) = withContext(Dispatchers.IO) {
        recentFileDao.updateProgress(uri, currentPage, totalPages, System.currentTimeMillis())
    }

    suspend fun updatePdfBookmarkState(uri: String, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        recentFileDao.updateBookmarkState(uri, isBookmarked)
    }

    suspend fun deletePdf(uri: String) = withContext(Dispatchers.IO) {
        recentFileDao.getByUri(uri)?.let {
            recentFileDao.delete(it)
        }
    }

    // Page level bookmarks
    fun getPageBookmarksForPdf(pdfUri: String): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getByUri(pdfUri)
    }

    suspend fun insertPageBookmark(bookmark: BookmarkEntity) = withContext(Dispatchers.IO) {
        bookmarkDao.insert(bookmark)
    }

    suspend fun deletePageBookmark(pdfUri: String, pageNumber: Int) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteByPage(pdfUri, pageNumber)
    }

    suspend fun hasPageBookmark(pdfUri: String, pageNumber: Int): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.hasBookmark(pdfUri, pageNumber)
    }

    // Highlights
    fun getHighlightsForPdf(fileUri: String): Flow<List<HighlightEntity>> {
        return highlightDao.getByUri(fileUri)
    }

    suspend fun insertHighlight(highlight: HighlightEntity) = withContext(Dispatchers.IO) {
        highlightDao.insert(highlight)
    }

    suspend fun insertHighlights(highlights: List<HighlightEntity>) = withContext(Dispatchers.IO) {
        highlightDao.insertAll(highlights)
    }

    suspend fun deleteHighlight(id: Int) = withContext(Dispatchers.IO) {
        highlightDao.deleteById(id)
    }

    suspend fun clearAllRecentFiles() = withContext(Dispatchers.IO) {
        recentFileDao.deleteAll()
    }

    suspend fun clearAllBookmarks() = withContext(Dispatchers.IO) {
        bookmarkDao.deleteAll()
    }

    suspend fun clearAllHighlights() = withContext(Dispatchers.IO) {
        highlightDao.deleteAll()
    }

    suspend fun getOcrResultByUri(uri: String): OcrResultEntity? = withContext(Dispatchers.IO) {
        ocrResultDao.getByFileUri(uri)
    }

    suspend fun insertOcrResult(ocrResult: OcrResultEntity) = withContext(Dispatchers.IO) {
        ocrResultDao.insert(ocrResult)
    }

    suspend fun deleteOcrResult(uri: String) = withContext(Dispatchers.IO) {
        ocrResultDao.deleteByFileUri(uri)
    }

    // Audio Bookmarks
    fun getAudioBookmarksForPdf(pdfUri: String): Flow<List<AudioBookmarkEntity>> {
        return audioBookmarkDao.getByUri(pdfUri)
    }

    suspend fun insertAudioBookmark(audioBookmark: AudioBookmarkEntity) = withContext(Dispatchers.IO) {
        audioBookmarkDao.insert(audioBookmark)
    }

    suspend fun deleteAudioBookmark(id: Int) = withContext(Dispatchers.IO) {
        audioBookmarkDao.deleteById(id)
    }

    suspend fun clearAllAudioBookmarks() = withContext(Dispatchers.IO) {
        audioBookmarkDao.deleteAll()
    }
}
