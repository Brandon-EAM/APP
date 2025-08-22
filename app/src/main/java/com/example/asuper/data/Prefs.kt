package com.example.asuper.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cfe_form", Context.MODE_PRIVATE)

    fun putText(key: String, value: String) = sp.edit { putString(key, value) }
    fun getText(key: String, def: String = ""): String = sp.getString(key, def) ?: def

    fun putUris(key: String, uris: List<Uri>) = sp.edit { putStringSet(key, uris.map { it.toString() }.toSet()) }
    fun getUris(key: String): List<Uri> = sp.getStringSet(key, emptySet())?.map(Uri::parse) ?: emptyList()

    fun clear() = sp.edit { clear() }
}