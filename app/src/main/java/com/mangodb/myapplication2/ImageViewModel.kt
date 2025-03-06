package com.mangodb.myapplication2

import android.os.Parcel
import android.os.Parcelable
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

        // 처음 추가된 이미지를 자동으로 활성 이미지로 설정
        if (activeNotificationImage.value == null) {
            setActiveNotificationImage(uri)
        }
    }

    fun setActiveNotificationImage(uri: Uri) {
        activeNotificationImage.value = uri
    }

}
