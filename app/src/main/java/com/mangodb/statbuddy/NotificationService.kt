package com.mangodb.statbuddy

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat

class NotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "ImageNotificationChannel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.mangodb.statbuddy.START"
        private const val ACTION_STOP = "com.mangodb.statbuddy.STOP"
        private const val EXTRA_IMAGE_URI = "image_uri"

        // 현재 사용 중인 동적 아이콘 리소스 ID
        private var currentIconResourceId: Int? = null

        fun startNotification(context: Context, imageUri: Uri?) {
            val intent = Intent(context, NotificationService::class.java).apply {
                action = ACTION_START
                imageUri?.let { putExtra(EXTRA_IMAGE_URI, it.toString()) }
            }
            context.startService(intent)
        }

        fun stopNotification(context: Context) {
            val intent = Intent(context, NotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
                val imageUri = imageUriString?.let { Uri.parse(it) }
                showNotification(imageUri)
            }
            ACTION_STOP -> {
                stopNotification()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "이미지 알림"
            val descriptionText = "선택한 이미지를 표시하는 알림 채널"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(imageUri: Uri?) {
        createNotificationChannel()

        val pendingIntent: android.app.PendingIntent =
            android.app.PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )

        // 동적 아이콘 생성 시도
        val dynamicIconId = imageUri?.let {
            NotificationUtils.createSmallIconFromImage(this, it)
        }

        // 알림 빌더 생성
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("이미지 표시 중")
            .setContentText("선택한 이미지가 알림으로 표시되고 있습니다")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // 동적 아이콘이 생성되었으면 사용, 아니면 기본 아이콘 사용
        if (dynamicIconId != null) {
            try {
                // 동적 아이콘 설정 시도
                val iconFile = NotificationUtils.DynamicResourceManager.getIconFile(dynamicIconId)
                if (iconFile != null && iconFile.exists()) {
                    val iconUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "com.mangodb.statbuddy.fileprovider",
                        iconFile
                    )

                    // Android 8.0 이상에서는 Icon 객체 사용
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val icon = android.graphics.drawable.Icon.createWithContentUri(iconUri)
                        builder.setSmallIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(
                            android.graphics.BitmapFactory.decodeFile(iconFile.absolutePath)
                        ))
                    } else {
                        // 하위 버전에서는 기본 아이콘 사용
                        builder.setSmallIcon(R.drawable.ic_bis)
                    }

                    currentIconResourceId = dynamicIconId
                } else {
                    builder.setSmallIcon(R.drawable.ic_bis)
                }
            } catch (e: Exception) {
                Log.e("NotificationService", "동적 아이콘 설정 실패", e)
                builder.setSmallIcon(R.drawable.ic_bis)
            }
        } else {
            builder.setSmallIcon(R.drawable.ic_bis)
        }

        // 큰 아이콘 및 확장 스타일 설정
        imageUri?.let {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(contentResolver, it)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, it)
                }

                builder.setLargeIcon(bitmap)
                    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            } catch (e: Exception) {
                Log.e("NotificationService", "이미지 로딩 오류", e)
            }
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun stopNotification() {
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}