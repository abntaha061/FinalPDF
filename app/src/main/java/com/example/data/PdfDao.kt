package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY lastAccessed DESC")
    fun getAllRecentPdfs(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isBookmarked = 1 ORDER BY lastAccessed DESC")
    fun getBookmarkedPdfs(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE uri = :uri LIMIT 1")
    suspend fun getPdfByUri(uri: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET currentPage = :currentPage, totalPages = :totalPages, lastAccessed = :lastAccessed WHERE uri = :uri")
    suspend fun updatePdfProgress(uri: String, currentPage: Int, totalPages: Int, lastAccessed: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_documents SET isBookmarked = :isBookmarked WHERE uri = :uri")
    suspend fun updatePdfBookmarkState(uri: String, isBookmarked: Boolean)

    @Query("DELETE FROM pdf_documents WHERE uri = :uri")
    suspend fun deletePdf(uri: String)

    // Page level bookmarks
    @Query("SELECT * FROM pdf_page_bookmarks WHERE pdfUri = :pdfUri ORDER BY pageNumber ASC")
    fun getPageBookmarksForPdf(pdfUri: String): Flow<List<PdfPageBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageBookmark(bookmark: PdfPageBookmarkEntity)

    @Query("DELETE FROM pdf_page_bookmarks WHERE pdfUri = :pdfUri AND pageNumber = :pageNumber")
    suspend fun deletePageBookmark(pdfUri: String, pageNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM pdf_page_bookmarks WHERE pdfUri = :pdfUri AND pageNumber = :pageNumber)")
    suspend fun hasPageBookmark(pdfUri: String, pageNumber: Int): Boolean
}
