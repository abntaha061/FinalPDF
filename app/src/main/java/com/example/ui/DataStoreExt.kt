package com.example.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// وضعنا هذا السطر في ملف منعزل لكي لا ينهار مترجم KSP الخاص بـ Hilt
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_reader_settings")
