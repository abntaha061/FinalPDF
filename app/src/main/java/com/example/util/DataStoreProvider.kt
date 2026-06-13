package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.pdfReaderDataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_reader_settings")
