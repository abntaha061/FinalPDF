package com.example.ui

import android.content.Context
import android.net.Uri
import java.io.File
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
import com.example.data.ReadingSessionEntity
import com.example.data.PdfRepository
import com.example.util.PdfPrefetchManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.example.util.pdfReaderDataStore

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.util.Log
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive

private val Context.dataStore get() = this.pdfReaderDataStore

private val NIGHT_MODE_KEY = booleanPreferencesKey("is_night_mode")
private val READING_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("reading_mode")
private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

private val GESTURE_MAPPINGS_KEY = androidx.datastore.preferences.core.stringPreferencesKey("gesture_mappings")

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
private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")

private val READING_SCROLL_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("reading_scroll_mode")
private val FIT_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("fit_mode")

private val SORT_MODE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("sort_mode")
private val FILTER_MIN_SIZE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("filter_min_size")
private val FILTER_MAX_SIZE_KEY = androidx.datastore.preferences.core.floatPreferencesKey("filter_max_size")
private val FILTER_MIN_PAGES_KEY = androidx.datastore.preferences.core.intPreferencesKey("filter_min_pages")
private val FILTER_MAX_PAGES_KEY = androidx.datastore.preferences.core.intPreferencesKey("filter_max_pages")
private val FILTER_DATE_RANGE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("filter_date_range")

private val LAST_FILES_CHECK_KEY = androidx.datastore.preferences.core.longPreferencesKey("last_files_check")
private val LAST_CLOUD_CHECK_KEY = androidx.datastore.preferences.core.longPreferencesKey("last_cloud_check")

class PdfViewModel(
    private val repository: PdfRepository,
    private val context: Context
) : ViewModel() {

    val timerManager by lazy { ReadingTimerManager(repository) }
    val ttsManager by lazy { com.example.util.TtsManager(context) }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Events for Tab Click Logic
    private val _scrollToTopEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    private val _navigateToRootEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToRootEvent = _navigateToRootEvent.asSharedFlow()

    fun triggerScrollToTop() {
        _scrollToTopEvent.tryEmit(Unit)
    }

    fun triggerNavigateToRoot() {
        _navigateToRootEvent.tryEmit(Unit)
    }

    private val _hasUnreadFiles = MutableStateFlow(false)
    val hasUnreadFiles: StateFlow<Boolean> = _hasUnreadFiles.asStateFlow()

    private val _hasUnreadCloud = MutableStateFlow(false)
    val hasUnreadCloud: StateFlow<Boolean> = _hasUnreadCloud.asStateFlow()

    fun markFilesAsRead() {
        _hasUnreadFiles.value = false
        viewModelScope.launch {
            try {
                context.pdfReaderDataStore.edit { preferences ->
                    preferences[LAST_FILES_CHECK_KEY] = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun markCloudAsRead() {
        _hasUnreadCloud.value = false
        viewModelScope.launch {
            try {
                context.pdfReaderDataStore.edit { preferences ->
                    preferences[LAST_CLOUD_CHECK_KEY] = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun setUnreadCloud(value: Boolean) {
        _hasUnreadCloud.value = value
    }

    private val _isBottomBarVisible = MutableStateFlow(true)
    val isBottomBarVisible: StateFlow<Boolean> = _isBottomBarVisible.asStateFlow()

    fun setBottomBarVisible(visible: Boolean) {
        _isBottomBarVisible.value = visible
    }

    private val _isPdfSearchable = MutableStateFlow<Boolean?>(null)
    val isPdfSearchable: StateFlow<Boolean?> = _isPdfSearchable.asStateFlow()

    private val _dismissedOcrBanners = MutableStateFlow<Set<String>>(emptySet())
    val dismissedOcrBanners: StateFlow<Set<String>> = _dismissedOcrBanners.asStateFlow()

    private val _ocrResultForActiveFile = MutableStateFlow<com.example.data.OcrResultEntity?>(null)
    val ocrResultForActiveFile: StateFlow<com.example.data.OcrResultEntity?> = _ocrResultForActiveFile.asStateFlow()

    private val _ocrProgress = MutableStateFlow<Pair<Int, Int>?>(null) // Pair(currentPage, totalPages)
    val ocrProgress: StateFlow<Pair<Int, Int>?> = _ocrProgress.asStateFlow()

    private val _isOcrRunning = MutableStateFlow(false)
    val isOcrRunning: StateFlow<Boolean> = _isOcrRunning.asStateFlow()

    private val _isSearchableRunning = MutableStateFlow(false)
    val isSearchableRunning: StateFlow<Boolean> = _isSearchableRunning.asStateFlow()

    private val _searchableProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val searchableProgress: StateFlow<Pair<Int, Int>?> = _searchableProgress.asStateFlow()

    fun dismissOcrBanner(uri: String) {
        _dismissedOcrBanners.update { it + uri }
    }

    val allReadingSessions = repository.allReadingSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalReadingTime = repository.totalReadingTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun clearAllReadingSessions() {
        viewModelScope.launch {
            repository.clearAllReadingSessions()
        }
    }

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

    val favoriteDocuments: StateFlow<List<RecentFileEntity>> = repository.favoritePdfs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
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

    private val _isDoublePageMode = MutableStateFlow(false)
    val isDoublePageMode: StateFlow<Boolean> = _isDoublePageMode.asStateFlow()

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

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

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

    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    private val _pendingDragDropUri = MutableStateFlow<String?>(null)
    val pendingDragDropUri: StateFlow<String?> = _pendingDragDropUri.asStateFlow()

    fun setIsDragging(dragging: Boolean) {
        _isDragging.value = dragging
    }

    fun setPendingDragDropUri(uri: String?) {
        _pendingDragDropUri.value = uri
    }

    private val _gestureMappings = MutableStateFlow<Map<com.example.data.GestureType, com.example.data.GestureAction>>(com.example.data.defaultGestures)
    val gestureMappings: StateFlow<Map<com.example.data.GestureType, com.example.data.GestureAction>> = _gestureMappings.asStateFlow()

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

    // Digital Signature States
    private val _savedSignaturePath = MutableStateFlow<String?>(null)
    val savedSignaturePath: StateFlow<String?> = _savedSignaturePath.asStateFlow()

    fun setSavedSignaturePath(path: String?) {
        _savedSignaturePath.value = path
    }

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

    private val _tableOfContents = MutableStateFlow<List<com.example.data.PdfBookmark>>(emptyList())
    val tableOfContents: StateFlow<List<com.example.data.PdfBookmark>> = _tableOfContents.asStateFlow()

    val prefetchManager = PdfPrefetchManager()
    private val _prefetchEnabled = MutableStateFlow(true)
    val prefetchEnabled: StateFlow<Boolean> = _prefetchEnabled.asStateFlow()

    fun setTableOfContents(toc: List<com.example.data.PdfBookmark>) {
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
    val activeAudioBookmarks: StateFlow<List<com.example.data.AudioBookmarkEntity>> = _selectedUri
        .flatMapLatest { uri ->
            if (uri != null) repository.getAudioBookmarksForPdf(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertAudioBookmark(audioBookmark: com.example.data.AudioBookmarkEntity) {
        viewModelScope.launch {
            repository.insertAudioBookmark(audioBookmark)
        }
    }

    fun deleteAudioBookmark(id: Int) {
        viewModelScope.launch {
            repository.deleteAudioBookmark(id)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeHighlights: StateFlow<List<HighlightEntity>> = _selectedUri
        .flatMapLatest { uri ->
            if (uri != null) repository.getHighlightsForPdf(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeComments: StateFlow<List<com.example.data.CommentEntity>> = _selectedUri
        .flatMapLatest { uri ->
            if (uri != null) repository.getCommentsForPdf(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    init {
        // Load saved digital signature from SharedPreferences if existing
        val prefs = context.getSharedPreferences("PdfPrefs", Context.MODE_PRIVATE)
        _savedSignaturePath.value = prefs.getString("saved_signature_path", null)

        // Prepopulate demo PDF from assets
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val demoFile = File(context.filesDir, "demo.pdf")
                if (!demoFile.exists()) {
                    context.assets.open("pdfjs/web/compressed.tracemonkey-pldi-09.pdf").use { input ->
                        demoFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (demoFile.exists()) {
                    val demoUri = Uri.fromFile(demoFile).toString()
                    val existing = repository.getPdfByUri(demoUri)
                    if (existing == null) {
                        val demoDoc = RecentFileEntity(
                            uri = demoUri,
                            name = "TraceMonkey (Demo).pdf",
                            sizeBytes = demoFile.length(),
                            currentPage = 0,
                            totalPages = 14,
                            lastOpenedAt = System.currentTimeMillis()
                        )
                        repository.insertPdf(demoDoc)
                        Log.d("PdfViewModel", "Successfully prepopulated demo PDF file!")
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e("PdfViewModel", "Failed to prepopulate demo PDF", e)
            }
        }

        // Observe recent documents to check if any file in the last 24 hours is after last_files_check
        viewModelScope.launch {
            try {
                combine(
                    recentDocuments,
                    context.pdfReaderDataStore.data.map { it[LAST_FILES_CHECK_KEY] ?: 0L }
                ) { documents, lastCheck ->
                    val now = System.currentTimeMillis()
                    val oneDayAgo = now - 24 * 60 * 60 * 1000L
                    documents.any { doc ->
                        doc.lastOpenedAt > oneDayAgo && doc.lastOpenedAt > lastCheck
                    }
                }.collect { hasUnread ->
                    _hasUnreadFiles.value = hasUnread
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // Check cloud syncer unread badge
        viewModelScope.launch {
            try {
                context.pdfReaderDataStore.data.map { it[LAST_CLOUD_CHECK_KEY] ?: 0L }.collect { lastCheck ->
                    // Set to true if never checked before (inviting onboarding sync style)
                    _hasUnreadCloud.value = lastCheck == 0L
                }
            } catch (e: Exception) {
                // ignore
            }
        }

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
                _dynamicColor.value = preferences[DYNAMIC_COLOR_KEY] ?: true

                val gestureJson = preferences[GESTURE_MAPPINGS_KEY]
                _gestureMappings.value = com.example.data.GestureSerializer.deserialize(gestureJson)
                _readingScrollMode.value = preferences[READING_SCROLL_MODE_KEY] ?: "continuous"
                _fitMode.value = preferences[FIT_MODE_KEY] ?: "width"
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
        // Skip redundant checks and permission popups if the exact same document is already selected and active.
        // This prevents transient SecurityExceptions from showing during normal reading, scrolling, or lifecycle ON_RESUME.
        if (_selectedUri.value == uri.toString()) {
            return
        }

        _errorState.value = null
        _securityExceptionUri.value = null
        _largeFileUriPending.value = null

        // Try to take persistable permission FIRST, before anything else!
        if (uri.scheme != "file") {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if it's not a persistent URI or already granted
            }
        }

        // 1. Get file size from metadata safely first (which does not throw SecurityException)
        val metadata = getUriMetadata(context, uri)
        var sizeBytes = metadata.second

        // If scheme is file, read direct file length
        if (uri.scheme == "file") {
            try {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    sizeBytes = file.length()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // If metadata size is zero or not found, try to open file descriptor safely (only for non-file URIs)
        if (sizeBytes <= 0 && uri.scheme != "file") {
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
            val stream = if (uri.scheme == "file") {
                java.io.FileInputStream(java.io.File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }
            val header = ByteArray(4)
            stream?.read(header)
            stream?.close()
            val isPdf = header.toString(Charsets.ISO_8859_1).startsWith("%PDF")
            if (!isPdf) {
                _errorState.value = "الملف ليس PDF صحيحاً أو تالف"
                return
            }
        } catch (e: SecurityException) {
            _securityExceptionUri.value = uri.toString()
            return
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

            try {
                context.dataStore.edit { preferences ->
                    preferences[com.example.util.LAST_FILE_NAME_KEY] = metadata.first
                    preferences[com.example.util.LAST_FILE_URI_KEY] = uri.toString()
                }
            } catch (e: Exception) {
                // Ignore
            }

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
            checkIfPdfIsSearchable(context, uri)
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

    fun scanDeviceForPdfs(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<java.io.File>()
            
            // Fast MediaStore query
            try {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns.DATA,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.SIZE
                )
                val selection = "${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("application/pdf", "%.pdf")
                
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val dataIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataIndex)
                        if (!path.isNullOrEmpty()) {
                            val file = java.io.File(path)
                            if (file.exists() && file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                                list.add(file)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "MediaStore query failed, falling back to path scanning", e)
            }
            
            // Fallback / complement: Scan standard directories
            val publicDirs = listOf(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                android.os.Environment.getExternalStorageDirectory()
            )
            
            for (dir in publicDirs) {
                if (dir != null && dir.exists() && dir.isDirectory) {
                    scanDir(dir, list)
                }
            }
            
            val uniqueFiles = list.distinctBy { it.absolutePath }
            for (file in uniqueFiles) {
                val fileUriStr = Uri.fromFile(file).toString()
                val existing = repository.getPdfByUri(fileUriStr)
                if (existing == null) {
                    val entity = RecentFileEntity(
                        uri = fileUriStr,
                        name = file.name,
                        lastOpenedAt = 0L,
                        sizeBytes = file.length(),
                        isFavorite = false,
                        currentPage = 0,
                        totalPages = 0
                    )
                    repository.insertPdf(entity)
                }
            }
        }
    }

    private fun scanDir(dir: java.io.File, list: MutableList<java.io.File>, maxDepth: Int = 4) {
        if (maxDepth <= 0 || list.size > 200) return
        try {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".") && f.name != "Android" && f.name != "self") {
                        scanDir(f, list, maxDepth - 1)
                    }
                } else if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true)) {
                    list.add(f)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun saveGestureMappings(mappings: Map<com.example.data.GestureType, com.example.data.GestureAction>) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[GESTURE_MAPPINGS_KEY] = com.example.data.GestureSerializer.serialize(mappings)
            }
        }
    }

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

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[DYNAMIC_COLOR_KEY] = enabled
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
            repository.updatePdfFavoriteState(uri, isFavorite)
            if (_currentDocument.value?.uri == uri) {
                _currentDocument.value = _currentDocument.value?.copy(isFavorite = isFavorite)
            }
        }
    }

    fun toggleFavoriteForFile(file: File, isFavorite: Boolean) {
        viewModelScope.launch {
            val uriStr = Uri.fromFile(file).toString()
            val existing = repository.getPdfByUri(uriStr)
            if (existing == null) {
                if (isFavorite) {
                    val doc = RecentFileEntity(
                        uri = uriStr,
                        name = file.name,
                        sizeBytes = file.length(),
                        totalPages = 0,
                        currentPage = 0,
                        lastOpenedAt = System.currentTimeMillis(),
                        isFavorite = true
                    )
                    repository.insertPdf(doc)
                }
            } else {
                repository.updatePdfFavoriteState(uriStr, isFavorite)
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

    fun insertComment(comment: com.example.data.CommentEntity) {
        viewModelScope.launch {
            repository.insertComment(comment)
        }
    }

    fun deleteComment(id: Int) {
        viewModelScope.launch {
            repository.deleteComment(id)
        }
    }

    fun importAnnotationsFromOtherPdf(otherFileUri: String, onResult: (Int) -> Unit) {
        val currentUri = _selectedUri.value ?: return
        viewModelScope.launch {
            try {
                val otherComments = repository.getCommentsForPdf(otherFileUri).first()
                val otherHighlights = repository.getHighlightsForPdf(otherFileUri).first()
                
                val copiedComments = otherComments.map {
                    it.copy(id = 0, fileUri = currentUri)
                }
                val copiedHighlights = otherHighlights.map {
                    it.copy(id = 0, fileUri = currentUri)
                }

                if (copiedComments.isNotEmpty()) {
                    repository.insertComments(copiedComments)
                }
                if (copiedHighlights.isNotEmpty()) {
                    repository.insertHighlights(copiedHighlights)
                }

                onResult(copiedComments.size + copiedHighlights.size)
            } catch (e: Exception) {
                onResult(0)
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

    private val _isTtsActive = MutableStateFlow(false)
    val isTtsActive: StateFlow<Boolean> = _isTtsActive.asStateFlow()

    fun showTtsBar(show: Boolean) {
        _isTtsActive.value = show
        if (!show) {
            ttsManager.stop()
        }
    }

    fun startTtsForCurrentPage() {
        val uriStr = _selectedUri.value ?: return
        val uri = Uri.parse(uriStr)
        val page = _currentPage.value
        
        viewModelScope.launch {
            _isTtsActive.value = true
            val pageTextRaw = getPageTextForTts(context, uri, page)
            val pageText = if (pageTextRaw.isBlank()) {
                _ocrResultForActiveFile.value?.extractedText ?: ""
            } else {
                pageTextRaw
            }
            
            if (pageText.isNotBlank()) {
                val locale = if (pageText.take(100).any { it in '\u0600'..'\u06FF' }) {
                    java.util.Locale("ar")
                } else {
                    java.util.Locale.ENGLISH
                }
                val pitch = if (locale.language == "ar") 1.1f else 1.0f
                
                ttsManager.init {
                    ttsManager.setPitch(pitch)
                    ttsManager.speak(pageText, locale)
                }
            } else {
                ttsManager.init {
                    ttsManager.speak("لا يوجد نص مقروء في هذه الصفحة")
                }
            }
        }
    }

    fun stopTts() {
        ttsManager.stop()
        _isTtsActive.value = false
    }

    fun getPageTextForTts(context: Context, uri: Uri, pageIndex: Int): String {
        return try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                    if (pageIndex < document.numberOfPages) {
                        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        val text = stripper.getText(document)
                        text ?: ""
                    } else {
                        ""
                    }
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e("PdfViewModel", "Error extracting page text for TTS", e)
            ""
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
        if (uri.scheme == "file") {
            try {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                    if (!name.lowercase().endsWith(".pdf")) {
                        name += ".pdf"
                    }
                    return Pair(name, size)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
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

    fun setReadingScrollMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[READING_SCROLL_MODE_KEY] = mode
            }
            _readingScrollMode.value = mode
        }
    }

    fun setFitMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[FIT_MODE_KEY] = mode
            }
            _fitMode.value = mode
        }
    }

    fun setDoublePageMode(enabled: Boolean) {
        _isDoublePageMode.value = enabled
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

    suspend fun getOcrResultByUri(uri: String): com.example.data.OcrResultEntity? {
        return repository.getOcrResultByUri(uri)
    }

    suspend fun insertOcrResult(ocrResult: com.example.data.OcrResultEntity) {
        repository.insertOcrResult(ocrResult)
    }

    suspend fun deleteOcrResult(uri: String) {
        repository.deleteOcrResult(uri)
    }

    fun checkIfPdfIsSearchable(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getOcrResultByUri(uri.toString())
            _ocrResultForActiveFile.value = existing
            if (existing != null) {
                _isPdfSearchable.value = true
                return@launch
            }
            var isSearchable = true
            try {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream)
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.startPage = 1
                    stripper.endPage = 1
                    val pageText = stripper.getText(document)
                    document.close()
                    isSearchable = pageText.trim().length > 50
                }
            } catch (e: Exception) {
                Log.e("PdfViewModel", "Error checking if pdf is searchable", e)
            }
            _isPdfSearchable.value = isSearchable
        }
    }

    fun renderPageToBitmap(context: Context, fileUri: Uri, pageIndex: Int): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                try {
                    if (pageIndex < renderer.pageCount) {
                        val page = renderer.openPage(pageIndex)
                        try {
                            val originalWidth = page.width
                            val originalHeight = page.height
                            val scale = (2048f / originalWidth).coerceAtLeast(1.5f)
                            val targetWidth = (originalWidth * scale).toInt()
                            val targetHeight = (originalHeight * scale).toInt()
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        } finally {
                            page.close()
                        }
                    } else {
                        null
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (e: Exception) {
            Log.e("PdfViewModel", "Failed to render page to bitmap", e)
            null
        }
    }

    private suspend fun processImageAwait(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: com.google.mlkit.vision.common.InputImage
    ): com.google.mlkit.vision.text.Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { text ->
                if (continuation.isActive) {
                    continuation.resume(text)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
    }

    private var ocrJob: kotlinx.coroutines.Job? = null

    fun cancelOcr() {
        ocrJob?.cancel()
        _isOcrRunning.value = false
        _ocrProgress.value = null
    }

    fun startOcr(context: Context, uri: Uri, lang: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        cancelOcr()
        _isOcrRunning.value = true
        _ocrProgress.value = 0 to 1
        ocrJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("Cannot open file")
                val totalPages = pfd.use { fd ->
                    val r = android.graphics.pdf.PdfRenderer(fd)
                    val count = r.pageCount
                    r.close()
                    count
                }
                _ocrProgress.value = 0 to totalPages
                val pageTextsList = ArrayList<String>()
                val options = if (lang == "en") {
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                } else {
                    try {
                        val clazz = Class.forName("com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions\$Builder")
                        val builder = clazz.getDeclaredConstructor().newInstance()
                        val buildMethod = clazz.getMethod("build")
                        buildMethod.invoke(builder) as com.google.mlkit.vision.text.TextRecognizerOptionsInterface
                    } catch (e: Exception) {
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                    }
                }
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(options)
                for (i in 0 until totalPages) {
                    if (!isActive) break
                    _ocrProgress.value = i to totalPages
                    val bitmap = renderPageToBitmap(context, uri, i)
                    if (bitmap != null) {
                        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                        val textResult = processImageAwait(recognizer, inputImage)
                        pageTextsList.add(textResult.text)
                        bitmap.recycle()
                    } else {
                        pageTextsList.add("")
                    }
                }
                if (isActive) {
                    val fullText = pageTextsList.joinToString("\n\n")
                    val pageTextsJson = org.json.JSONArray(pageTextsList).toString()
                    val ocrResult = com.example.data.OcrResultEntity(
                        fileUri = uri.toString(),
                        extractedText = fullText,
                        pageTexts = pageTextsJson,
                        language = lang,
                        createdAt = System.currentTimeMillis()
                    )
                    repository.insertOcrResult(ocrResult)
                    _ocrResultForActiveFile.value = ocrResult
                    _isPdfSearchable.value = true
                    _isOcrRunning.value = false
                    _ocrProgress.value = null
                    launch(Dispatchers.Main) {
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewModel", "OCR Failed", e)
                _isOcrRunning.value = false
                _ocrProgress.value = null
                launch(Dispatchers.Main) {
                    onFailure(e.localizedMessage ?: "OCR Failed")
                }
            }
        }
    }

    fun makePdfSearchable(
        context: Context,
        fileUri: Uri,
        lang: String,
        onSuccess: (java.io.File) -> Unit,
        onFailure: (String) -> Unit
    ) {
        _isSearchableRunning.value = true
        _searchableProgress.value = 0 to 1
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                val originalInputStream = context.contentResolver.openInputStream(fileUri) ?: throw Exception("Cannot open file")
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(originalInputStream)
                val totalPages = document.numberOfPages
                _searchableProgress.value = 0 to totalPages
                val fontFiles = listOf(
                    "/system/fonts/NotoNaskhArabic-Regular.ttf",
                    "/system/fonts/NotoSansArabic-Regular.ttf",
                    "/system/fonts/DroidSansArabic.ttf",
                    "/system/fonts/NotoSansCJK-Regular.ttc",
                    "/system/fonts/Roboto-Regular.ttf"
                )
                var pdfFont: com.tom_roush.pdfbox.pdmodel.font.PDFont? = null
                for (path in fontFiles) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        try {
                            pdfFont = com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(document, file)
                            break
                        } catch (e: Exception) {
                            Log.e("PdfViewModel", "Failed to load $path", e)
                        }
                    }
                }
                val isFontUnicode = pdfFont != null && pdfFont !is com.tom_roush.pdfbox.pdmodel.font.PDType1Font
                if (pdfFont == null) {
                    pdfFont = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                }
                val options = if (lang == "en") {
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                } else {
                    try {
                        val clazz = Class.forName("com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions\$Builder")
                        val builder = clazz.getDeclaredConstructor().newInstance()
                        val buildMethod = clazz.getMethod("build")
                        buildMethod.invoke(builder) as com.google.mlkit.vision.text.TextRecognizerOptionsInterface
                    } catch (e: Exception) {
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                    }
                }
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(options)
                for (i in 0 until totalPages) {
                    if (!isActive) break
                    _searchableProgress.value = i to totalPages
                    val page = document.getPage(i)
                    val originalWidthPt = page.mediaBox.width
                    val originalHeightPt = page.mediaBox.height
                    val bitmap = renderPageToBitmap(context, fileUri, i)
                    if (bitmap != null) {
                        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                        val textResult = processImageAwait(recognizer, inputImage)
                        val contentStream = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                            document,
                            page,
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        )
                        contentStream.beginText()
                        contentStream.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()
                        val scaleX = originalWidthPt / bitmapWidth
                        val scaleY = originalHeightPt / bitmapHeight
                        for (block in textResult.textBlocks) {
                            for (line in block.lines) {
                                val boundingBox = line.boundingBox ?: continue
                                var text = line.text
                                if (!isFontUnicode) {
                                    text = text.filter { it.code in 0..255 }
                                }
                                text = text.replace("\n", " ").trim()
                                if (text.isEmpty()) continue
                                val pixelX = boundingBox.left.toFloat()
                                val pixelY = boundingBox.bottom.toFloat()
                                val pdfX = pixelX * scaleX
                                val pdfY = (bitmapHeight - pixelY) * scaleY
                                val fontHeightPx = boundingBox.height().toFloat()
                                val fontSizePt = (fontHeightPx * scaleY).coerceAtLeast(6f).coerceAtMost(24f)
                                try {
                                    contentStream.setFont(pdfFont, fontSizePt)
                                    val matrix = com.tom_roush.pdfbox.util.Matrix(1f, 0f, 0f, 1f, pdfX, pdfY)
                                    contentStream.setTextMatrix(matrix)
                                    contentStream.showText(text)
                                } catch (e: Exception) {
                                    Log.e("PdfViewModel", "Error rendering line text in PDF: $text", e)
                                }
                            }
                        }
                        contentStream.endText()
                        contentStream.close()
                        bitmap.recycle()
                    }
                }
                if (isActive) {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val originalNameFile = getUriMetadata(context, fileUri).first
                    val baseName = originalNameFile.replace(".pdf", "", ignoreCase = true)
                    val searchablePdfFile = java.io.File(dir, "${baseName}_searchable.pdf")
                    val fos = java.io.FileOutputStream(searchablePdfFile)
                    document.save(fos)
                    fos.close()
                    document.close()
                    _isSearchableRunning.value = false
                    _searchableProgress.value = null
                    launch(Dispatchers.Main) {
                        onSuccess(searchablePdfFile)
                    }
                } else {
                    document.close()
                }
            } catch (e: Exception) {
                Log.e("PdfViewModel", "Making searchable PDF failed", e)
                _isSearchableRunning.value = false
                _searchableProgress.value = null
                launch(Dispatchers.Main) {
                    onFailure(e.localizedMessage ?: "Process Failed")
                }
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
