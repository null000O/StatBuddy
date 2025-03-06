package com.mangodb.myapplication2

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationService : Service() {
    companion object {
        private const val CHANNEL_ID = "ImageNotificationChannel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.example.notificationimageapp.START"
        private const val ACTION_STOP = "com.example.notificationimageapp.STOP"
        private const val EXTRA_IMAGE_URI = "image_uri"

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "이미지 알림"
            val descriptionText = "선택한 이미지를 표시하는 알림 채널"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(imageUri: Uri?) {
        createNotificationChannel()

        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.translate)  // 알림 아이콘 필요
            .setContentTitle("이미지 표시 중")
            .setContentText("선택한 이미지가 알림으로 표시되고 있습니다")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // 커스텀 스타일 설정 (큰 이미지)
        imageUri?.let {
            try {
                val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, it)
                builder.setLargeIcon(bitmap)
                    .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun stopNotification() {
        stopForeground(true)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}