@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HighlightEntity
import com.example.data.RecentFileEntity
import com.example.data.BookmarkEntity
import com.example.data.PdfRepository
import com.example.util.PdfPrefetchManager
import com.shockwave.pdfium.PdfDocument // 🌟 الحل السحري لمنع انهيار KSP 🌟
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_reader_settings")

private val NIGHT_MODE_KEY = booleanPreferencesKey("is_night_mode")
private val READING_MODE_KEY = stringPreferencesKey("reading_mode")
private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

private val DEFAULT_READING_MODE_KEY = stringPreferencesKey("default_reading_mode")
private val PRIMARY_COLOR_KEY = stringPreferencesKey("primary_color")
private val UI_FONT_SIZE_KEY = floatPreferencesKey("ui_font_size")
private val AUTO_SAVE_POSITION_KEY = booleanPreferencesKey("auto_save_position")
private val SHOW_PAGE_INDICATOR_KEY = booleanPreferencesKey("show_page_indicator")
private val PAGE_SPACING_KEY = floatPreferencesKey("page_spacing")
private val SCROLL_SPEED_KEY = floatPreferencesKey("scroll_speed")
private val LINK_OPEN_MODE_KEY = stringPreferencesKey("link_open_mode")
private val AUTO_PLAY_AUDIO_KEY = booleanPreferencesKey("auto_play_audio")
private val AUDIO_VOLUME_KEY = floatPreferencesKey("audio_volume")
private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")

private val SORT_MODE_KEY = stringPreferencesKey("sort_mode")
private val FILTER_MIN_SIZE_KEY = floatPreferencesKey("filter_min_size")
private val FILTER_MAX_SIZE_KEY = floatPreferencesKey("filter_max_size")
private val FILTER_MIN_PAGES_KEY = intPreferencesKey("filter_min_pages")
private val FILTER_MAX_PAGES_KEY = intPreferencesKey("filter_max_pages")
private val FILTER_DATE_RANGE_KEY = stringPreferencesKey("filter_date_range")

private val READING_SCROLL_MODE_KEY = stringPreferencesKey("reading_scroll_mode")
private val FIT_MODE_KEY = stringPreferencesKey("fit_mode")
private val DOUBLE_PAGE_KEY = booleanPreferencesKey("double_page_mode")

// كلاسات صريحة لحماية المترجم من الفشل
data class FilterParams(
    val minSize: Float,
    val maxSize: Float,
    val minPages: Int,
    val maxPages: Int,
    val dateRange: String,
    val sortMode: String
)

data class LargeFileWarning(
    val uri: String,
    val sizeMB: Long
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val repository: PdfRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isOnboardingDone = MutableStateFlow<Boolean?>(null)
    val isOnboardingDone: StateFlow<Boolean?> = _isOnboardingDone.asStateFlow()

    private val _sortMode = MutableStateFlow("الأحدث أولاً")
    val sortMode: StateFlow<String> = _sortMode.asStateFlow()

    private val _filterMinSize = MutableStateFlow(0f)
    val filterMinSize: StateFlow<Float> = _filterMinSize.asStateFlow()

    private val _filterMaxSize = MutableStateFlow(100f)
    val filterMaxSize: StateFlow<Float> = _filterMaxSize.asStateFlow()

    private val _filterMinPages = MutableStateFlow(1)
    val filterMinPages: StateFlow<Int> = _filterMinPages.asStateFlow()

    private val _filterMaxPages = MutableStateFlow(500)
    val filterMaxPages: StateFlow<Int> = _filterMaxPages.asStateFlow()

    private val _filterDateRange = MutableStateFlow("الكل")
    val filterDateRange: StateFlow<String> = _filterDateRange.asStateFlow()

    private val _activeFilterCount = MutableStateFlow(0)
    val activeFilterCount: StateFlow<Int> = _activeFilterCount.asStateFlow()

    private val _currentFilterParams = MutableStateFlow(FilterParams(0f, 100f, 1, 500, "الكل", "الأحدث أولاً"))

    private val _recentDocuments = MutableStateFlow<List<RecentFileEntity>>(emptyList())
    val recentDocuments: StateFlow<List<RecentFileEntity>> = _recentDocuments.asStateFlow()

    private val _favoriteDocuments = MutableStateFlow<List<RecentFileEntity>>(emptyList())
    val favoriteDocuments: StateFlow<List<RecentFileEntity>> = _favoriteDocuments.asStateFlow()

    private val _activePageBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val activePageBookmarks: StateFlow<List<BookmarkEntity>> = _activePageBookmarks.asStateFlow()

    private val _activeHighlights = MutableStateFlow<List<HighlightEntity>>(emptyList())
    val activeHighlights: StateFlow<List<HighlightEntity>> = _activeHighlights.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _readingMode = MutableStateFlow("normal")
    val readingMode: StateFlow<String> = _readingMode.asStateFlow()

    private val _isSwipeHorizontal = MutableStateFlow(false)
    val isSwipeHorizontal: StateFlow<Boolean> = _isSwipeHorizontal.asStateFlow()

    private val _readingScrollMode = MutableStateFlow("continuous")
    val readingScrollMode: StateFlow<String> = _readingScrollMode.asStateFlow()

    private val _fitMode = MutableStateFlow("width")
    val fitMode: StateFlow<String> = _fitMode.asStateFlow()

    private val _isDoublePageEnabled = MutableStateFlow(false)
    val isDoublePageEnabled: StateFlow<Boolean> = _isDoublePageEnabled.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(true)
    val isToolbarVisible: StateFlow<Boolean> = _isToolbarVisible.asStateFlow()

    private val _securityExceptionUri = MutableStateFlow<String?>(null)
    val securityExceptionUri: StateFlow<String?> = _securityExceptionUri.asStateFlow()

    // 🌟 حماية إضافية: تم تحويل الـ Pair إلى Class صريح 🌟
    private val _largeFileUriPending = MutableStateFlow<LargeFileWarning?>(null)
    val largeFileUriPending: StateFlow<LargeFileWarning?> = _largeFileUriPending.asStateFlow()

    private val _showLargeFileWarningSnackbar = MutableStateFlow(false)
    val showLargeFileWarningSnackbar: StateFlow<Boolean> = _showLargeFileWarningSnackbar.asStateFlow()

    private val _defaultReadingMode = MutableStateFlow("normal")
    val defaultReadingMode: StateFlow<String> = _defaultReadingMode.asStateFlow()

    private val _primaryColorHex = MutableStateFlow("#6C63FF")
    val primaryColorHex: StateFlow<String> = _primaryColorHex.asStateFlow()

    private val _uiFontSize = MutableStateFlow(15f)
    val uiFontSize: StateFlow<Float> = _uiFontSize.asStateFlow()

    private val _autoSavePosition = MutableStateFlow(true)
    val autoSavePosition: StateFlow<Boolean> = _autoSavePosition.asStateFlow()

    private val _showPageIndicator = MutableStateFlow(true)
    val showPageIndicator: StateFlow<Boolean> = _showPageIndicator.asStateFlow()

    private val _pageSpacing = MutableStateFlow(8f)
    val pageSpacing: StateFlow<Float> = _pageSpacing.asStateFlow()

    private val _scrollSpeed = MutableStateFlow(1.0f)
    val scrollSpeed: StateFlow<Float> = _scrollSpeed.asStateFlow()

    private val _linkOpenMode = MutableStateFlow("المتصفح الافتراضي")
    val linkOpenMode: StateFlow<String> = _linkOpenMode.asStateFlow()

    private val _autoPlayAudio = MutableStateFlow(true)
    val autoPlayAudio: StateFlow<Boolean> = _autoPlayAudio.asStateFlow()

    private val _audioVolume = MutableStateFlow(1.0f)
    val audioVolume: StateFlow<Float> = _audioVolume.asStateFlow()

    private val _appLanguage = MutableStateFlow("ar")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _selectedUri = MutableStateFlow<String?>(null)
    val selectedUri: StateFlow<String?> = _selectedUri.asStateFlow()

    private val _currentDocument = MutableStateFlow<RecentFileEntity?>(null)
    val currentDocument: StateFlow<RecentFileEntity?> = _currentDocument.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    val lastPageMap = mutableMapOf<String, Int>()
    val pageRotations = mutableMapOf<Int, Int>()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _isViewerLoading = MutableStateFlow(false)
    val isViewerLoading: StateFlow<Boolean> = _isViewerLoading.asStateFlow()

    // 🌟 الحل الجذري لمنع KSP Crash باستخدام نوع محلي مبسط 🌟
    private val _tableOfContents = MutableStateFlow<List<PdfDocument.Bookmark>>(emptyList())
    val tableOfContents: StateFlow<List<PdfDocument.Bookmark>> = _tableOfContents.asStateFlow()

    val prefetchManager = PdfPrefetchManager()
    
    private val _prefetchEnabled = MutableStateFlow(true)
    val prefetchEnabled: StateFlow<Boolean> = _prefetchEnabled.asStateFlow()

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    init {
        viewModelScope.launch {
            _currentFilterParams.flatMapLatest { params ->
                val minSizeL = (params.minSize * 1024 * 1024).toLong()
                val maxSizeL = if (params.maxSize >= 100f) Long.MAX_VALUE else (params.maxSize * 1024 * 1024).toLong()
                val minDateL = when (params.dateRange) {
                    "24 ساعة" -> System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    "أسبوع" -> System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                    "شهر" -> System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
                    else -> 0L
                }

                repository.getFilteredPdfs(minSizeL, maxSizeL, params.minPages, params.maxPages, minDateL).map { list ->
                    when (params.sortMode) {
                        "الأقدم أولاً" -> list.sortedBy { it.lastOpenedAt }
                        "الاسم (أ → ي)" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        "الحجم (الأكبر أولاً)" -> list.sortedByDescending { it.sizeBytes }
                        else -> list.sortedByDescending { it.lastOpenedAt }
                    }
                }
            }.collect { sortedList ->
                _recentDocuments.value = sortedList
            }
        }

        viewModelScope.launch {
            repository.bookmarkedPdfs.collect { _favoriteDocuments.value = it }
        }

        viewModelScope.launch {
            _selectedUri.flatMapLatest { uri ->
                if (uri != null) repository.getPageBookmarksForPdf(uri) else flowOf(emptyList())
            }.collect { _activePageBookmarks.value = it }
        }

        viewModelScope.launch {
            _selectedUri.flatMapLatest { uri ->
                if (uri != null) repository.getHighlightsForPdf(uri) else flowOf(emptyList())
            }.collect { _activeHighlights.value = it }
        }

        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                _defaultReadingMode.value = preferences[DEFAULT_READING_MODE_KEY] ?: "normal"
                val initialMode = preferences[READING_MODE_KEY] ?: preferences[DEFAULT_READING_MODE_KEY] ?: if (preferences[NIGHT_MODE_KEY] == true) "night" else "normal"
                _readingMode.value = initialMode
                _isNightMode.value = (initialMode == "night")
                
                _isOnboardingDone.value = preferences[ONBOARDING_DONE_KEY] == true
                _primaryColorHex.value = preferences[PRIMARY_COLOR_KEY] ?: "#6C63FF"
                _uiFontSize.value = preferences[UI_FONT_SIZE_KEY] ?: 15f
                _autoSavePosition.value = preferences[AUTO_SAVE_POSITION_KEY] ?: true
                _showPageIndicator.value = preferences[SHOW_PAGE_INDICATOR_KEY] ?: true
                _pageSpacing.value = preferences[PAGE_SPACING_KEY] ?: 8f
                _scrollSpeed.value = preferences[SCROLL_SPEED_KEY] ?: 1.0f
                _linkOpenMode.value = preferences[LINK_OPEN_MODE_KEY] ?: "المتصفح الافتراضي"
                _autoPlayAudio.value = preferences[AUTO_PLAY_AUDIO_KEY] ?: true
                _audioVolume.value = preferences[AUDIO_VOLUME_KEY] ?: 1.0f
                
                _sortMode.value = preferences[SORT_MODE_KEY] ?: "الأحدث أولاً"
                _filterMinSize.value = preferences[FILTER_MIN_SIZE_KEY] ?: 0f
                _filterMaxSize.value = preferences[FILTER_MAX_SIZE_KEY] ?: 100f
                _filterMinPages.value = preferences[FILTER_MIN_PAGES_KEY] ?: 1
                _filterMaxPages.value = preferences[FILTER_MAX_PAGES_KEY] ?: 500
                _filterDateRange.value = preferences[FILTER_DATE_RANGE_KEY] ?: "الكل"
                _appLanguage.value = preferences[APP_LANGUAGE_KEY] ?: "ar"
                _readingScrollMode.value = preferences[READING_SCROLL_MODE_KEY] ?: "continuous"
                _fitMode.value = preferences[FIT_MODE_KEY] ?: "width"
                _isDoublePageEnabled.value = preferences[DOUBLE_PAGE_KEY] ?: false
                
                triggerFilterUpdate()
                _isReady.value = true
            }
        }

        viewModelScope.launch {
            currentPage.debounce(200L).collect { page ->
                val fileUriString = _selectedUri.value
                if (fileUriString != null && _prefetchEnabled.value) {
                    try {
                        prefetchManager.prefetchAround(page, _totalPages.value, Uri.parse(fileUriString), context)
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun triggerFilterUpdate() {
        var count = 0
        if (_filterMinSize.value > 0f || _filterMaxSize.value < 100f) count++
        if (_filterMinPages.value > 1 || _filterMaxPages.value < 500) count++
        if (_filterDateRange.value != "الكل") count++
        _activeFilterCount.value = count

        _currentFilterParams.value = FilterParams(
            _filterMinSize.value, _filterMaxSize.value, _filterMinPages.value, _filterMaxPages.value, _filterDateRange.value, _sortMode.value
        )
    }

    fun completeOnboarding() {
        viewModelScope.launch { context.dataStore.edit { it[ONBOARDING_DONE_KEY] = true } }
    }

    fun clearSecurityException() { _securityExceptionUri.value = null }
    fun clearLargeFilePending() { _largeFileUriPending.value = null }
    fun consumeLargeFileWarningSnackbar() { _showLargeFileWarningSnackbar.value = false }

    fun selectDocumentForced(context: Context, uri: Uri) {
        _largeFileUriPending.value = null
        _showLargeFileWarningSnackbar.value = true
        _prefetchEnabled.value = false
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val header = ByteArray(4)
            stream?.read(header)
            stream?.close()
            if (!header.toString(Charsets.ISO_8859_1).startsWith("%PDF")) {
                _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
                return
            }
        } catch (e: Exception) {
            _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
            return
        }
        proceedWithLoading(context, uri)
    }

    fun toggleToolbarVisibility() { _isToolbarVisible.value = !_isToolbarVisible.value }
    fun setToolbarVisibility(visible: Boolean) { _isToolbarVisible.value = visible }
    fun setTableOfContents(toc: List<PdfDocument.Bookmark>) { _tableOfContents.value = toc }

    fun toggleNightMode() {
        val nextValue = !_isNightMode.value
        _isNightMode.value = nextValue
        val nextMode = if (nextValue) "night" else "normal"
        _readingMode.value = nextMode
        viewModelScope.launch { context.dataStore.edit { it[NIGHT_MODE_KEY] = nextValue; it[READING_MODE_KEY] = nextMode } }
    }

    fun setReadingMode(mode: String) {
        _readingMode.value = mode
        _isNightMode.value = (mode == "night")
        viewModelScope.launch { context.dataStore.edit { it[READING_MODE_KEY] = mode; it[NIGHT_MODE_KEY] = (mode == "night") } }
    }

    fun toggleSwipeHorizontal() { _isSwipeHorizontal.value = !_isSwipeHorizontal.value }

    fun setReadingScrollMode(mode: String) {
        _readingScrollMode.value = mode
        viewModelScope.launch { context.dataStore.edit { it[READING_SCROLL_MODE_KEY] = mode } }
    }

    fun setFitMode(mode: String) {
        _fitMode.value = mode
        viewModelScope.launch { context.dataStore.edit { it[FIT_MODE_KEY] = mode } }
    }

    fun toggleDoublePageMode() {
        val nextValue = !_isDoublePageEnabled.value
        _isDoublePageEnabled.value = nextValue
        viewModelScope.launch { context.dataStore.edit { it[DOUBLE_PAGE_KEY] = nextValue } }
    }

    fun selectDocument(context: Context, uri: Uri) {
        _errorState.value = null
        _securityExceptionUri.value = null
        _largeFileUriPending.value = null
        var sizeBytes = 0L
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            sizeBytes = pfd?.statSize ?: 0L
            pfd?.close()
        } catch (e: SecurityException) {
            _securityExceptionUri.value = uri.toString()
            return
        } catch (e: Exception) {}

        when {
            sizeBytes > 500L * 1024L * 1024L -> { _errorState.value = "الملف أكبر من 500 ميجابايت. هذا الحجم غير مدعوم."; return }
            sizeBytes > 100L * 1024L * 1024L -> {
                _prefetchEnabled.value = false
                _largeFileUriPending.value = LargeFileWarning(uri.toString(), sizeBytes / (1024 * 1024))
                return
            }
            else -> { _prefetchEnabled.value = true }
        }

        try {
            val stream = context.contentResolver.openInputStream(uri)
            val header = ByteArray(4)
            stream?.read(header)
            stream?.close()
            if (!header.toString(Charsets.ISO_8859_1).startsWith("%PDF")) {
                _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
                return
            }
        } catch (e: Exception) {
            _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
            return
        }
        proceedWithLoading(context, uri)
    }

    private fun proceedWithLoading(context: Context, uri: Uri) {
        _selectedUri.value = uri.toString()
        _isViewerLoading.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            val metadata = getUriMetadata(context, uri)
            var doc = repository.getPdfByUri(uri.toString())
            val key = intPreferencesKey("last_page_${uri.toString().hashCode()}")
            var savedPage = 0
            try { savedPage = context.dataStore.data.map { it[key] ?: 0 }.first() } catch (e: Exception) {}
            if (savedPage <= 0 && doc != null) savedPage = doc.currentPage
            if (savedPage < 0) savedPage = 0

            if (doc == null) {
                doc = RecentFileEntity(uri.toString(), metadata.first, metadata.second, savedPage, 0, System.currentTimeMillis(), null)
            } else {
                doc = doc.copy(lastOpenedAt = System.currentTimeMillis(), currentPage = savedPage)
            }
            repository.insertPdf(doc)
            _currentPage.value = savedPage
            _currentDocument.value = doc
            checkIfCurrentPageIsBookmarked()
        }
    }

    fun updateProgress(uri: String, page: Int, total: Int) {
        _currentPage.value = page
        _totalPages.value = total
        viewModelScope.launch {
            if (_autoSavePosition.value) {
                repository.updatePdfProgress(uri, page, total)
                _currentDocument.value = _currentDocument.value?.copy(currentPage = page, totalPages = total)
            }
            checkIfCurrentPageIsBookmarked()
        }
    }

    fun setDefaultReadingMode(mode: String) { viewModelScope.launch { context.dataStore.edit { it[DEFAULT_READING_MODE_KEY] = mode; it[READING_MODE_KEY] = mode } } }
    fun setPrimaryColorHex(hex: String) { viewModelScope.launch { context.dataStore.edit { it[PRIMARY_COLOR_KEY] = hex } } }
    fun setUiFontSize(size: Float) { viewModelScope.launch { context.dataStore.edit { it[UI_FONT_SIZE_KEY] = size } } }
    fun setAutoSavePosition(b: Boolean) { viewModelScope.launch { context.dataStore.edit { it[AUTO_SAVE_POSITION_KEY] = b } } }
    fun setShowPageIndicator(b: Boolean) { viewModelScope.launch { context.dataStore.edit { it[SHOW_PAGE_INDICATOR_KEY] = b } } }
    fun setPageSpacing(spacing: Float) { viewModelScope.launch { context.dataStore.edit { it[PAGE_SPACING_KEY] = spacing } } }
    fun setScrollSpeed(speed: Float) { viewModelScope.launch { context.dataStore.edit { it[SCROLL_SPEED_KEY] = speed } } }
    fun setLinkOpenMode(mode: String) { viewModelScope.launch { context.dataStore.edit { it[LINK_OPEN_MODE_KEY] = mode } } }
    fun setAutoPlayAudio(b: Boolean) { viewModelScope.launch { context.dataStore.edit { it[AUTO_PLAY_AUDIO_KEY] = b } } }
    fun setAudioVolume(volume: Float) { viewModelScope.launch { context.dataStore.edit { it[AUDIO_VOLUME_KEY] = volume } } }
    fun setAppLanguage(lang: String) { viewModelScope.launch { context.dataStore.edit { it[APP_LANGUAGE_KEY] = lang } } }
    fun clearAllRecentFiles() { viewModelScope.launch { repository.clearAllRecentFiles() } }
    fun clearAllBookmarks() { viewModelScope.launch { repository.clearAllBookmarks() } }
    fun clearAllHighlights() { viewModelScope.launch { repository.clearAllHighlights() } }

    fun toggleFavorite(uri: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updatePdfBookmarkState(uri, isFavorite)
            if (_currentDocument.value?.uri == uri) _currentDocument.value = _currentDocument.value?.copy(isBookmarked = isFavorite)
        }
    }

    fun deleteDocument(uri: String) {
        viewModelScope.launch { repository.deletePdf(uri); if (_selectedUri.value == uri) closeDocument() }
    }

    fun closeDocument() {
        _selectedUri.value = null
        _currentDocument.value = null
        _currentPage.value = 0
        _totalPages.value = 0
        _isCurrentPageBookmarked.value = false
        _isViewerLoading.value = false
        hasShownRestoreSnackbarForUri = null
        _tableOfContents.value = emptyList()
        pageRotations.clear()
    }

    fun toggleCurrentPageBookmark() {
        val uri = _selectedUri.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            if (repository.hasPageBookmark(uri, page)) {
                repository.deletePageBookmark(uri, page)
                _isCurrentPageBookmarked.value = false
            } else {
                repository.insertPageBookmark(BookmarkEntity(fileUri = uri, pageNumber = page, label = "Page ${page + 1}", createdAt = System.currentTimeMillis()))
                _isCurrentPageBookmarked.value = true
            }
        }
    }

    fun deletePageBookmark(pdfUri: String, pageNumber: Int) {
        viewModelScope.launch {
            repository.deletePageBookmark(pdfUri, pageNumber)
            if (_selectedUri.value == pdfUri && _currentPage.value == pageNumber) _isCurrentPageBookmarked.value = false
        }
    }

    fun addPageBookmark(pdfUri: String, pageNumber: Int, label: String) {
        viewModelScope.launch {
            repository.insertPageBookmark(BookmarkEntity(fileUri = pdfUri, pageNumber = pageNumber, label = label, createdAt = System.currentTimeMillis()))
            if (_selectedUri.value == pdfUri && _currentPage.value == pageNumber) _isCurrentPageBookmarked.value = true
        }
    }

    fun insertHighlight(highlight: HighlightEntity) { viewModelScope.launch { repository.insertHighlight(highlight) } }
    fun deleteHighlight(id: Int) { viewModelScope.launch { repository.deleteHighlight(id) } }
    fun jumpToPage(pageNumber: Int) { _currentPage.value = pageNumber; checkIfCurrentPageIsBookmarked() }
    fun setViewerLoading(isLoading: Boolean) { _isViewerLoading.value = isLoading }

    private var hasShownRestoreSnackbarForUri: String? = null
    fun shouldShowRestoreSnackbar(uri: String): Boolean {
        if (hasShownRestoreSnackbarForUri == uri) return false
        hasShownRestoreSnackbarForUri = uri
        return true
    }

    fun saveLastPage(uri: String, page: Int) {
        lastPageMap[uri] = page
        viewModelScope.launch {
            context.dataStore.edit { it[intPreferencesKey("last_page_${uri.hashCode()}")] = page }
            repository.updatePdfProgress(uri, page, _totalPages.value)
        }
    }

    fun setTotalPages(total: Int) {
        _totalPages.value = total
        val uri = _selectedUri.value
        if (uri != null) {
            viewModelScope.launch {
                repository.updatePdfProgress(uri, _currentPage.value, total)
                _currentDocument.value = _currentDocument.value?.copy(totalPages = total)
            }
        }
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        val uri = _selectedUri.value
        if (uri != null) {
            lastPageMap[uri] = page
            viewModelScope.launch {
                context.dataStore.edit { it[intPreferencesKey("last_page_${uri.hashCode()}")] = page }
                repository.updatePdfProgress(uri, page, _totalPages.value)
                _currentDocument.value = _currentDocument.value?.copy(currentPage = page)
            }
            checkIfCurrentPageIsBookmarked()
        }
    }

    private val _errorState: MutableStateFlow<String?> = MutableStateFlow(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()
    fun setError(error: String?) { _errorState.value = error }

    private fun checkIfCurrentPageIsBookmarked() {
        val uri = _selectedUri.value ?: return
        val page = _currentPage.value
        viewModelScope.launch { _isCurrentPageBookmarked.value = repository.hasPageBookmark(uri, page) }
    }

    private fun getUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "Document.pdf"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nIdx != -1) name = cursor.getString(nIdx) ?: name
                    if (sIdx != -1) size = cursor.getLong(sIdx)
                }
            }
        } catch (e: Exception) { uri.lastPathSegment?.let { name = it } }
        if (!name.lowercase().endsWith(".pdf")) name += ".pdf"
        return Pair(name, size)
    }

    fun setSortMode(mode: String) {
        _sortMode.value = mode
        triggerFilterUpdate()
        viewModelScope.launch { context.dataStore.edit { it[SORT_MODE_KEY] = mode } }
    }
    fun setFilterMinSize(size: Float) {
        _filterMinSize.value = size
        triggerFilterUpdate()
        viewModelScope.launch { context.dataStore.edit { it[FILTER_MIN_SIZE_KEY] = size } }
    }
    fun setFilterMaxSize(size: Float) {
        _filterMaxSize.value = size
        triggerFilterUpdate()
        viewModelScope.launch { context.dataStore.edit { it[FILTER_MAX_SIZE_KEY] = size } }
    }
    fun setFilterMinPages(pages: Int) {
        _filterMinPages.value = pages
        triggerFilterUpdate()
        viewModelScope.launch { context.dataStore.edit { it[FILTER_MIN_PAGES_KEY] = pages } }
    }
    fun setFilterMaxPages(pages: Int) {
        _filterMaxPages.value = pages
        triggerFilterUpdate()
        viewModelScope.launch { context.dataStore.edit { it[FILTER_MAX_PAGES_KEY] = pages } }
    }
    fun setFilterDateRange(range: String) {
        _filterDateRange.value = range
        triggerFilterUpdate()
        viewModelScope.launch { context.dataStore.edit { it[FILTER_DATE_RANGE_KEY] = range } }
    }
    fun resetFilters() {
        _filterMinSize.value = 0f
        _filterMaxSize.value = 100f
        _filterMinPages.value = 1
        _filterMaxPages.value = 500
        _filterDateRange.value = "الكل"
        triggerFilterUpdate()
        viewModelScope.launch {
            context.dataStore.edit {
                it[FILTER_MIN_SIZE_KEY] = 0f
                it[FILTER_MAX_SIZE_KEY] = 100f
                it[FILTER_MIN_PAGES_KEY] = 1
                it[FILTER_MAX_PAGES_KEY] = 500
                it[FILTER_DATE_RANGE_KEY] = "الكل"
            }
        }
    }
}
