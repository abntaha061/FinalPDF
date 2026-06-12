package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HighlightEntity
import com.example.data.RecentFileEntity
import com.example.data.BookmarkEntity
import com.example.data.PdfRepository
import com.example.util.PdfPrefetchManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "pdf_reader_settings")
private val NIGHT_MODE_KEY = booleanPreferencesKey("is_night_mode")
private val READING_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("reading_mode")
private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

private val DEFAULT_READING_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("default_reading_mode")
private val PRIMARY_COLOR_KEY = androidx.datastore.preferences.core.stringPreferencesKey("primary_color")
private val UI_FONT_SIZE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("ui_font_size")
private val AUTO_SAVE_POSITION_KEY = booleanPreferencesKey("auto_save_position")
private val SHOW_PAGE_INDICATOR_KEY = booleanPreferencesKey("show_page_indicator")
private val PAGE_SPACING_KEY = androidx.datastore.preferences.core.floatPreferencesKey("page_spacing")
private val SCROLL_SPEED_KEY = androidx.datastore.preferences.core.floatPreferencesKey("scroll_speed")
private val LINK_OPEN_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("link_open_mode")
private val AUTO_PLAY_AUDIO_KEY = booleanPreferencesKey("auto_play_audio")
private val AUDIO_VOLUME_KEY = androidx.datastore.preferences.core.floatPreferencesKey("audio_volume")
private val APP_LANGUAGE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("app_language")

private val SORT_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("sort_mode")
private val FILTER_MIN_SIZE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("filter_min_size")
private val FILTER_MAX_SIZE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("filter_max_size")
private val FILTER_MIN_PAGES_KEY = androidx.datastore.preferences.core.intPreferencesKey("filter_min_pages")
private val FILTER_MAX_PAGES_KEY = androidx.datastore.preferences.core.intPreferencesKey("filter_max_pages")
private val FILTER_DATE_RANGE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("filter_date_range")

class PdfViewModel(
    private val repository: PdfRepository,
    private val context: Context
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

    val activeFilterCount: StateFlow<Int> = combine(
        combine(_filterMinSize, _filterMaxSize) { minS, maxS -> minS to maxS },
        combine(_filterMinPages, _filterMaxPages) { minP, maxP -> minP to maxP },
        _filterDateRange
    ) { sizeRange, pageRange, dateRange ->
        val minS = sizeRange.first
        val maxS = sizeRange.second
        val minP = pageRange.first
        val maxP = pageRange.second
        var count = 0
        if (minS > 0f || maxS < 100f) count++
        if (minP > 1 || maxP < 500) count++
        if (dateRange != "الكل") count++
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentDocuments: StateFlow<List<RecentFileEntity>> = combine(
        combine(_filterMinSize, _filterMaxSize) { minS, maxS -> minS to maxS },
        combine(_filterMinPages, _filterMaxPages) { minP, maxP -> minP to maxP },
        _filterDateRange,
        _sortMode
    ) { sizeRange, pageRange, dateRange, sortMode ->
        val minS = sizeRange.first
        val maxS = sizeRange.second
        val minP = pageRange.first
        val maxP = pageRange.second
        
        val minSize = (minS * 1024 * 1024).toLong()
        val maxSize = if (maxS >= 100f) Long.MAX_VALUE else (maxS * 1024 * 1024).toLong()
        val minPages = minP
        val maxPages = if (maxP >= 500) Int.MAX_VALUE else maxP
        val minDate = when (dateRange) {
            "24 ساعة" -> System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            "أسبوع" -> System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            "شهر" -> System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
            else -> 0L
        }
        FilterParams(minSize, maxSize, minPages, maxPages, minDate, sortMode)
    }.flatMapLatest { params ->
        repository.getFilteredPdfs(params.minSize, params.maxSize, params.minPages, params.maxPages, params.minDate)
            .map { list ->
                when (params.sortMode) {
                    "الأقدم أولاً" -> list.sortedBy { it.lastOpenedAt }
                    "الاسم (أ → ي)" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    "الحجم (الأكبر أولاً)" -> list.sortedByDescending { it.sizeBytes }
                    else -> list.sortedByDescending { it.lastOpenedAt } // "الأحدث أولاً"
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<RecentFileEntity>> = repository.bookmarkedPdfs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _readingMode = MutableStateFlow("normal")
    val readingMode: StateFlow<String> = _readingMode.asStateFlow()

    private val _isSwipeHorizontal = MutableStateFlow(false)
    val isSwipeHorizontal: StateFlow<Boolean> = _isSwipeHorizontal.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(true)
    val isToolbarVisible: StateFlow<Boolean> = _isToolbarVisible.asStateFlow()

    private val _securityExceptionUri = MutableStateFlow<String?>(null)
    val securityExceptionUri: StateFlow<String?> = _securityExceptionUri.asStateFlow()

    private val _largeFileUriPending = MutableStateFlow<Pair<String, Long>?>(null) // Pair of Uri, size in MB
    val largeFileUriPending: StateFlow<Pair<String, Long>?> = _largeFileUriPending.asStateFlow()

    private val _showLargeFileWarningSnackbar = MutableStateFlow(false)
    val showLargeFileWarningSnackbar: StateFlow<Boolean> = _showLargeFileWarningSnackbar.asStateFlow()

    // Custom M3 Settings
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

    init {
        viewModelScope.launch {
            context.dataStore.data.map { preferences ->
                preferences[READING_MODE_KEY] ?: preferences[DEFAULT_READING_MODE_KEY] ?: if (preferences[NIGHT_MODE_KEY] == true) "night" else "normal"
            }.collect { mode ->
                _readingMode.value = mode
                _isNightMode.value = (mode == "night")
            }
        }
        viewModelScope.launch {
            context.dataStore.data.map { preferences ->
                preferences[ONBOARDING_DONE_KEY] == true
            }.collect { done ->
                _isOnboardingDone.value = done
            }
        }
        viewModelScope.launch {
            context.dataStore.data.collect { preferences ->
                _defaultReadingMode.value = preferences[DEFAULT_READING_MODE_KEY] ?: "normal"
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
            }
        }
        viewModelScope.launch {
            recentDocuments.first()
            _isOnboardingDone.filterNotNull().first()
            _isReady.value = true
        }
        viewModelScope.launch {
            currentPage
                .debounce(200L)
                .collect { page ->
                    val fileUriString = _selectedUri.value
                    if (fileUriString != null && _prefetchEnabled.value) {
                        try {
                            val fUri = Uri.parse(fileUriString)
                            prefetchManager.prefetchAround(page, _totalPages.value, fUri, context)
                        } catch (e: Exception) {
                            // Silently ignore prefetch errors
                        }
                    }
                }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[ONBOARDING_DONE_KEY] = true
            }
        }
    }

    fun clearSecurityException() {
        _securityExceptionUri.value = null
    }

    fun clearLargeFilePending() {
        _largeFileUriPending.value = null
    }

    fun consumeLargeFileWarningSnackbar() {
        _showLargeFileWarningSnackbar.value = false
    }

    fun selectDocumentForced(context: Context, uri: Uri) {
        _largeFileUriPending.value = null
        _showLargeFileWarningSnackbar.value = true
        _prefetchEnabled.value = false // disable prefetch for forced large files

        // Validate magic bytes
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val header = ByteArray(4)
            stream?.read(header)
            stream?.close()
            val isPdf = header.toString(Charsets.ISO_8859_1).startsWith("%PDF")
            if (!isPdf) {
                _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
                return
            }
        } catch (e: Exception) {
            _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
            return
        }

        proceedWithLoading(context, uri)
    }

    fun toggleToolbarVisibility() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun setToolbarVisibility(visible: Boolean) {
        _isToolbarVisible.value = visible
    }

    // Active Reader States
    private val _selectedUri = MutableStateFlow<String?>(null)
    val selectedUri: StateFlow<String?> = _selectedUri.asStateFlow()

    private val _currentDocument = MutableStateFlow<RecentFileEntity?>(null)
    val currentDocument: StateFlow<RecentFileEntity?> = _currentDocument.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    val lastPageMap: MutableMap<String, Int> = mutableMapOf()

    val pageRotations: MutableMap<Int, Int> = mutableMapOf()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _isViewerLoading = MutableStateFlow(false)
    val isViewerLoading: StateFlow<Boolean> = _isViewerLoading.asStateFlow()

    private val _tableOfContents = MutableStateFlow<List<com.shockwave.pdfium.PdfDocument.Bookmark>>(emptyList())
    val tableOfContents: StateFlow<List<com.shockwave.pdfium.PdfDocument.Bookmark>> = _tableOfContents.asStateFlow()

    val prefetchManager = PdfPrefetchManager()
    private val _prefetchEnabled = MutableStateFlow(true)
    val prefetchEnabled: StateFlow<Boolean> = _prefetchEnabled.asStateFlow()

    fun setTableOfContents(toc: List<com.shockwave.pdfium.PdfDocument.Bookmark>) {
        _tableOfContents.value = toc
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activePageBookmarks: StateFlow<List<BookmarkEntity>> = _selectedUri
        .flatMapLatest { uri ->
            if (uri != null) repository.getPageBookmarksForPdf(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeHighlights: StateFlow<List<HighlightEntity>> = _selectedUri
        .flatMapLatest { uri ->
            if (uri != null) repository.getHighlightsForPdf(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    fun toggleNightMode() {
        val nextValue = !_isNightMode.value
        _isNightMode.value = nextValue
        val nextMode = if (nextValue) "night" else "normal"
        _readingMode.value = nextMode
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[NIGHT_MODE_KEY] = nextValue
                preferences[READING_MODE_KEY] = nextMode
            }
        }
    }

    fun setReadingMode(mode: String) {
        _readingMode.value = mode
        _isNightMode.value = (mode == "night")
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[READING_MODE_KEY] = mode
                preferences[NIGHT_MODE_KEY] = (mode == "night")
            }
        }
    }

    fun toggleSwipeHorizontal() {
        _isSwipeHorizontal.value = !_isSwipeHorizontal.value
    }

    fun selectDocument(context: Context, uri: Uri) {
        _errorState.value = null
        _securityExceptionUri.value = null
        _largeFileUriPending.value = null

        var sizeBytes = 0L
        // 1. Get file size and Check thresholds
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            sizeBytes = pfd?.statSize ?: 0L
            pfd?.close()
        } catch (e: SecurityException) {
            _securityExceptionUri.value = uri.toString()
            return // Stop and show permission denied dialog
        } catch (e: Exception) {
            // Ignore other exceptions during file prep checking
        }

        when {
            sizeBytes > 500L * 1024L * 1024L -> { // > 500MB
                _errorState.value = "الملف أكبر من 500 ميجابايت. هذا الحجم غير مدعوم."
                return
            }
            sizeBytes > 100L * 1024L * 1024L -> { // > 100MB
                _prefetchEnabled.value = false
                val sizeMB = sizeBytes / (1024 * 1024)
                _largeFileUriPending.value = Pair(uri.toString(), sizeMB)
                return // Stop for user confirmation
            }
            else -> {
                _prefetchEnabled.value = true
            }
        }

        // Validate it's actually a PDF (check magic bytes):
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val header = ByteArray(4)
            stream?.read(header)
            stream?.close()
            val isPdf = header.toString(Charsets.ISO_8859_1).startsWith("%PDF")
            if (!isPdf) {
                _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
                return
            }
        } catch (e: Exception) {
            _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
            return
        }

        // Proceed normally
        proceedWithLoading(context, uri)
    }

    private fun proceedWithLoading(context: Context, uri: Uri) {
        _selectedUri.value = uri.toString()
        _isViewerLoading.value = true
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Take persistable permission if possible
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if it's not a persistent URI
            }

            val metadata = getUriMetadata(context, uri)
            var doc = repository.getPdfByUri(uri.toString())
            
            val key = androidx.datastore.preferences.core.intPreferencesKey("last_page_${uri.toString().hashCode()}")
            var savedPage = 0
            try {
                savedPage = context.dataStore.data.map { it[key] ?: 0 }.first()
            } catch (e: Exception) {
                // Ignore
            }

            if (savedPage <= 0 && doc != null) {
                savedPage = doc.currentPage
            }

            if (savedPage < 0) {
                savedPage = 0
            }

            if (doc == null) {
                doc = RecentFileEntity(
                    uri = uri.toString(),
                    name = metadata.first,
                    sizeBytes = metadata.second,
                    currentPage = savedPage,
                    totalPages = 0,
                    lastOpenedAt = System.currentTimeMillis()
                )
                repository.insertPdf(doc)
            } else {
                // Update access timestamp
                doc = doc.copy(
                    lastOpenedAt = System.currentTimeMillis(),
                    currentPage = savedPage
                )
                repository.insertPdf(doc)
            }
            
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

    // Setters for Settings
    fun setDefaultReadingMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DEFAULT_READING_MODE_KEY] = mode
                // Also update the active reading mode immediately
                preferences[READING_MODE_KEY] = mode
            }
        }
    }

    fun setPrimaryColorHex(hex: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PRIMARY_COLOR_KEY] = hex
            }
        }
    }

    fun setUiFontSize(size: Float) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[UI_FONT_SIZE_KEY] = size
            }
        }
    }

    fun setAutoSavePosition(b: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[AUTO_SAVE_POSITION_KEY] = b
            }
        }
    }

    fun setShowPageIndicator(b: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[SHOW_PAGE_INDICATOR_KEY] = b
            }
        }
    }

    fun setPageSpacing(spacing: Float) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PAGE_SPACING_KEY] = spacing
            }
        }
    }

    fun setScrollSpeed(speed: Float) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[SCROLL_SPEED_KEY] = speed
            }
        }
    }

    fun setLinkOpenMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[LINK_OPEN_MODE_KEY] = mode
            }
        }
    }

    fun setAutoPlayAudio(b: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[AUTO_PLAY_AUDIO_KEY] = b
            }
        }
    }

    fun setAudioVolume(volume: Float) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[AUDIO_VOLUME_KEY] = volume
            }
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[APP_LANGUAGE_KEY] = lang
            }
        }
    }

    // DB clear operations
    fun clearAllRecentFiles() {
        viewModelScope.launch {
            repository.clearAllRecentFiles()
        }
    }

    fun clearAllBookmarks() {
        viewModelScope.launch {
            repository.clearAllBookmarks()
        }
    }

    fun clearAllHighlights() {
        viewModelScope.launch {
            repository.clearAllHighlights()
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
        hasShownRestoreSnackbarForUri = null
        _tableOfContents.value = emptyList()
        pageRotations.clear()
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
                val bookmark = BookmarkEntity(
                    fileUri = uri,
                    pageNumber = page,
                    label = "Page ${page + 1}",
                    createdAt = System.currentTimeMillis()
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

    fun addPageBookmark(pdfUri: String, pageNumber: Int, label: String) {
        viewModelScope.launch {
            val bookmark = BookmarkEntity(
                fileUri = pdfUri,
                pageNumber = pageNumber,
                label = label,
                createdAt = System.currentTimeMillis()
            )
            repository.insertPageBookmark(bookmark)
            if (_selectedUri.value == pdfUri && _currentPage.value == pageNumber) {
                _isCurrentPageBookmarked.value = true
            }
        }
    }

    fun insertHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            repository.insertHighlight(highlight)
        }
    }

    fun deleteHighlight(id: Int) {
        viewModelScope.launch {
            repository.deleteHighlight(id)
        }
    }

    fun jumpToPage(pageNumber: Int) {
        _currentPage.value = pageNumber
        checkIfCurrentPageIsBookmarked()
    }

    fun setViewerLoading(isLoading: Boolean) {
        _isViewerLoading.value = isLoading
    }

    private var hasShownRestoreSnackbarForUri: String? = null

    fun shouldShowRestoreSnackbar(uri: String): Boolean {
        if (hasShownRestoreSnackbarForUri == uri) {
            return false
        }
        hasShownRestoreSnackbarForUri = uri
        return true
    }

    fun saveLastPage(uri: String, page: Int) {
        lastPageMap[uri] = page
        viewModelScope.launch {
            val key = androidx.datastore.preferences.core.intPreferencesKey("last_page_${uri.hashCode()}")
            context.dataStore.edit { preferences ->
                preferences[key] = page
            }
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
                val key = androidx.datastore.preferences.core.intPreferencesKey("last_page_${uri.hashCode()}")
                context.dataStore.edit { preferences ->
                    preferences[key] = page
                }
                repository.updatePdfProgress(uri, page, _totalPages.value)
                _currentDocument.value = _currentDocument.value?.copy(currentPage = page)
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

    fun setSortMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[SORT_MODE_KEY] = mode
            }
        }
    }

    fun setFilterMinSize(size: Float) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FILTER_MIN_SIZE_KEY] = size
            }
        }
    }

    fun setFilterMaxSize(size: Float) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FILTER_MAX_SIZE_KEY] = size
            }
        }
    }

    fun setFilterMinPages(pages: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FILTER_MIN_PAGES_KEY] = pages
            }
        }
    }

    fun setFilterMaxPages(pages: Int) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FILTER_MAX_PAGES_KEY] = pages
            }
        }
    }

    fun setFilterDateRange(range: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FILTER_DATE_RANGE_KEY] = range
            }
        }
    }

    fun resetFilters() {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FILTER_MIN_SIZE_KEY] = 0f
                preferences[FILTER_MAX_SIZE_KEY] = 100f
                preferences[FILTER_MIN_PAGES_KEY] = 1
                preferences[FILTER_MAX_PAGES_KEY] = 500
                preferences[FILTER_DATE_RANGE_KEY] = "الكل"
            }
        }
    }
}

private data class FilterParams(
    val minSize: Long,
    val maxSize: Long,
    val minPages: Int,
    val maxPages: Int,
    val minDate: Long,
    val sortMode: String
)

class PdfViewModelFactory(
    private val repository: PdfRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PdfViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PdfViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
