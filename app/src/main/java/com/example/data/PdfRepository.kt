package com.example.data

import kotlinx.coroutines.flow.Flow

class PdfRepository(private val pdfDao: PdfDao) {
    val allRecentPdfs: Flow<List<PdfDocumentEntity>> = pdfDao.getAllRecentPdfs()
    val bookmarkedPdfs: Flow<List<PdfDocumentEntity>> = pdfDao.getBookmarkedPdfs()

    suspend fun getPdfByUri(uri: String): PdfDocumentEntity? {
        return pdfDao.getPdfByUri(uri)
    }

    suspend fun insertPdf(pdf: PdfDocumentEntity) {
        pdfDao.insertPdf(pdf)
    }

    suspend fun updatePdfProgress(uri: String, currentPage: Int, totalPages: Int) {
        pdfDao.updatePdfProgress(uri, currentPage, totalPages)
    }

    suspend fun updatePdfBookmarkState(uri: String, isBookmarked: Boolean) {
        pdfDao.updatePdfBookmarkState(uri, isBookmarked)
    }

    suspend fun deletePdf(uri: String) {
        pdfDao.deletePdf(uri)
    }

    // Page level bookmarks
    fun getPageBookmarksForPdf(pdfUri: String): Flow<List<PdfPageBookmarkEntity>> {
        return pdfDao.getPageBookmarksForPdf(pdfUri)
    }

    suspend fun insertPageBookmark(bookmark: PdfPageBookmarkEntity) {
        pdfDao.insertPageBookmark(bookmark)
    }

    suspend fun deletePageBookmark(pdfUri: String, pageNumber: Int) {
        pdfDao.deletePageBookmark(pdfUri, pageNumber)
    }

    suspend fun hasPageBookmark(pdfUri: String, pageNumber: Int): Boolean {
        return pdfDao.hasPageBookmark(pdfUri, pageNumber)
    }
}
