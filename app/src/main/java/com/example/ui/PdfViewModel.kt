package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PdfDocumentEntity
import com.example.data.PdfPageBookmarkEntity
import com.example.data.PdfRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PdfViewModel(private val repository: PdfRepository) : ViewModel() {

    // Document States
    val recentDocuments: StateFlow<List<PdfDocumentEntity>> = repository.allRecentPdfs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<PdfDocumentEntity>> = repository.bookmarkedPdfs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _isSwipeHorizontal = MutableStateFlow(false)
    val isSwipeHorizontal: StateFlow<Boolean> = _isSwipeHorizontal.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(true)
    val isToolbarVisible: StateFlow<Boolean> = _isToolbarVisible.asStateFlow()

    fun toggleToolbarVisibility() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun setToolbarVisibility(visible: Boolean) {
        _isToolbarVisible.value = visible
    }

    // Active Reader States
    private val _selectedUri = MutableStateFlow<String?>(null)
    val selectedUri: StateFlow<String?> = _selectedUri.asStateFlow()

    private val _currentDocument = MutableStateFlow<PdfDocumentEntity?>(null)
    val currentDocument: StateFlow<PdfDocumentEntity?> = _currentDocument.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _isViewerLoading = MutableStateFlow(false)
    val isViewerLoading: StateFlow<Boolean> = _isViewerLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activePageBookmarks: StateFlow<List<PdfPageBookmarkEntity>> = _selectedUri
        .flatMapLatest { uri ->
            if (uri != null) repository.getPageBookmarksForPdf(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    fun toggleSwipeHorizontal() {
        _isSwipeHorizontal.value = !_isSwipeHorizontal.value
    }

    fun selectDocument(context: Context, uri: Uri) {
        _selectedUri.value = uri.toString()
        _isViewerLoading.value = true
        
        viewModelScope.launch {
            // Take persistable permission if possible
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if it's not a persistent URI
            }

            val metadata = getUriMetadata(context, uri)
            var doc = repository.getPdfByUri(uri.toString())
            
            if (doc == null) {
                doc = PdfDocumentEntity(
                    uri = uri.toString(),
                    name = metadata.first,
                    size = metadata.second,
                    currentPage = 0,
                    totalPages = 0
                )
                repository.insertPdf(doc)
            } else {
                // Update access timestamp
                doc = doc.copy(lastAccessed = System.currentTimeMillis())
                repository.insertPdf(doc)
                _currentPage.value = doc.currentPage
            }
            
            _currentDocument.value = doc
            checkIfCurrentPageIsBookmarked()
        }
    }

    fun updateProgress(uri: String, page: Int, total: Int) {
        _currentPage.value = page
        _totalPages.value = total
        viewModelScope.launch {
            repository.updatePdfProgress(uri, page, total)
            _currentDocument.value = _currentDocument.value?.copy(currentPage = page, totalPages = total)
            checkIfCurrentPageIsBookmarked()
        }
    }

    fun toggleFavorite(uri: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updatePdfBookmarkState(uri, isFavorite)
            if (_currentDocument.value?.uri == uri) {
                _currentDocument.value = _currentDocument.value?.copy(isBookmarked = isFavorite)
            }
        }
    }

    fun deleteDocument(uri: String) {
        viewModelScope.launch {
            repository.deletePdf(uri)
            if (_selectedUri.value == uri) {
                closeDocument()
            }
        }
    }

    fun closeDocument() {
        _selectedUri.value = null
        _currentDocument.value = null
        _currentPage.value = 0
        _totalPages.value = 0
        _isCurrentPageBookmarked.value = false
        _isViewerLoading.value = false
    }

    fun toggleCurrentPageBookmark() {
        val uri = _selectedUri.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            val exists = repository.hasPageBookmark(uri, page)
            if (exists) {
                repository.deletePageBookmark(uri, page)
                _isCurrentPageBookmarked.value = false
            } else {
                val bookmark = PdfPageBookmarkEntity(
                    pdfUri = uri,
                    pageNumber = page,
                    label = "Page ${page + 1}"
                )
                repository.insertPageBookmark(bookmark)
                _isCurrentPageBookmarked.value = true
            }
        }
    }

    fun deletePageBookmark(pdfUri: String, pageNumber: Int) {
        viewModelScope.launch {
            repository.deletePageBookmark(pdfUri, pageNumber)
            if (_selectedUri.value == pdfUri && _currentPage.value == pageNumber) {
                _isCurrentPageBookmarked.value = false
            }
        }
    }

    fun jumpToPage(pageNumber: Int) {
        _currentPage.value = pageNumber
        checkIfCurrentPageIsBookmarked()
    }

    fun setViewerLoading(isLoading: Boolean) {
        _isViewerLoading.value = isLoading
    }

    fun setTotalPages(total: Int) {
        _totalPages.value = total
        val uri = _selectedUri.value
        if (uri != null) {
            viewModelScope.launch {
                repository.updatePdfProgress(uri, _currentPage.value, total)
            }
        }
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        val uri = _selectedUri.value
        if (uri != null) {
            viewModelScope.launch {
                repository.updatePdfProgress(uri, page, _totalPages.value)
            }
            checkIfCurrentPageIsBookmarked()
        }
    }

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    fun setError(error: String?) {
        _errorState.value = error
    }

    private fun checkIfCurrentPageIsBookmarked() {
        val uri = _selectedUri.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            _isCurrentPageBookmarked.value = repository.hasPageBookmark(uri, page)
        }
    }

    private fun getUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "Document.pdf"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "Document.pdf"
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            uri.lastPathSegment?.let { name = it }
        }
        if (!name.lowercase().endsWith(".pdf")) {
            name += ".pdf"
        }
        return Pair(name, size)
    }
}

class PdfViewModelFactory(private val repository: PdfRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PdfViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PdfViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
