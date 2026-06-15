package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey

val Context.pdfReaderDataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_reader_settings")

val LAST_FILE_NAME_KEY = stringPreferencesKey("last_file_name")
val LAST_FILE_URI_KEY = stringPreferencesKey("last_file_uri")
val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
val SEARCH_CASE_SENSITIVE_KEY = booleanPreferencesKey("search_case_sensitive")
