package com.mangodb.statbuddy

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ImageViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context = application.applicationContext
    private val prefsManager = SharedPreferencesManager(context)

    val savedImages = mutableStateListOf<Uri>()
    val activeNotificationImage = mutableStateOf<Uri?>(null)
    val notificationActive = mutableStateOf(false)

    init {
        // 앱 시작 시 저장된 데이터 불러오기
        loadSavedState()
    }

    private fun loadSavedState() {
        // 저장된 이미지 목록 불러오기
        val savedImagesList = prefsManager.getSavedImages()
        savedImages.clear()
        savedImages.addAll(savedImagesList)

        // 활성 이미지 불러오기
        activeNotificationImage.value = prefsManager.getActiveImageUri()

        // 알림 활성화 상태 불러오기
        notificationActive.value = prefsManager.getNotificationActive()

        // 알림이 활성화되어 있으면 서비스 시작
        if (notificationActive.value) {
            NotificationService.startNotification(context, activeNotificationImage.value)
        }
    }

    fun addImageToLibrary(uri: Uri) {
        if (!savedImages.contains(uri)) {
            savedImages.add(uri)
            prefsManager.saveSavedImages(savedImages.toList())
        }

        // 처음 추가된 이미지를 자동으로 활성 이미지로 설정
        if (activeNotificationImage.value == null) {
            setActiveNotificationImage(uri)
        }
    }

    fun setActiveNotificationImage(uri: Uri) {
        activeNotificationImage.value = uri
        prefsManager.saveActiveImageUri(uri)
    }

    fun setNotificationActive(active: Boolean) {
        notificationActive.value = active
        prefsManager.saveNotificationActive(active)
    }
}