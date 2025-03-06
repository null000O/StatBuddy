package com.mangodb.statbuddy

import android.content.Context
import android.net.Uri

class SharedPreferencesManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("statbuddy_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_NOTIFICATION_ACTIVE = "notification_active"
        private const val KEY_ACTIVE_IMAGE_URI = "active_image_uri"
        private const val KEY_SAVED_IMAGES = "saved_images"
    }

    fun saveNotificationActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ACTIVE, active).apply()
    }

    fun getNotificationActive(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_ACTIVE, false)
    }

    fun saveActiveImageUri(uri: Uri?) {
        prefs.edit().putString(KEY_ACTIVE_IMAGE_URI, uri?.toString()).apply()
    }

    fun getActiveImageUri(): Uri? {
        val uriString = prefs.getString(KEY_ACTIVE_IMAGE_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    fun saveSavedImages(images: List<Uri>) {
        val imageStrings = images.map { it.toString() }
        prefs.edit().putStringSet(KEY_SAVED_IMAGES, imageStrings.toSet()).apply()
    }

    fun getSavedImages(): List<Uri> {
        val imageStrings = prefs.getStringSet(KEY_SAVED_IMAGES, emptySet()) ?: emptySet()
        return imageStrings.map { Uri.parse(it) }
    }
}