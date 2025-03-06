package com.mangodb.statbuddy

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import android.net.Uri

class ImageViewModel() : ViewModel() {
    val savedImages = mutableStateListOf<Uri>()
    val activeNotificationImage = mutableStateOf<Uri?>(null)
    val notificationActive = mutableStateOf(false)

    fun addImageToLibrary(uri: Uri) {
        if (!savedImages.contains(uri)) {
            savedImages.add(uri)
        }

        // select image as active notification image if not active image exists
        if (activeNotificationImage.value == null) {
            setActiveNotificationImage(uri)
        }
    }

    fun setActiveNotificationImage(uri: Uri) {
        activeNotificationImage.value = uri
    }

}
