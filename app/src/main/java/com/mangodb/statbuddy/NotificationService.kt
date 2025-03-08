package com.mangodb.statbuddy

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class NotificationService : Service() {

    companion object {
        private const val CHANNEL_ID = "ImageNotificationChannel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.mangodb.statbuddy.START"
        private const val ACTION_STOP = "com.mangodb.statbuddy.STOP"
        private const val EXTRA_IMAGE_URI = "image_uri"
        // 헤드업 알림 표시 지속 시간(ms)
        private const val HEADS_UP_DURATION = 5000L

        fun startNotification(context: Context, imageUri: Uri?) {
            val priorityIntent = Intent(context, PriorityNotificationService::class.java).apply {
                action = ACTION_START
                imageUri?.let { putExtra(EXTRA_IMAGE_URI, it.toString()) }
                // 헤드업 표시 여부 전달
                putExtra("show_heads_up", true)
            }
            context.startService(priorityIntent)

            // 5초 후 헤드업 알림 제거하는 인텐트 전송
            Handler(Looper.getMainLooper()).postDelayed({
                val updateIntent = Intent(context, PriorityNotificationService::class.java).apply {
                    action = ACTION_START
                    imageUri?.let { putExtra(EXTRA_IMAGE_URI, it.toString()) }
                    putExtra("show_heads_up", false)
                }
                context.startService(updateIntent)
            }, HEADS_UP_DURATION)
        }

        fun stopNotification(context: Context) {
            val priorityIntent = Intent(context, PriorityNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(priorityIntent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}

class PriorityNotificationService : Service() {
    companion object {
        private const val PRIORITY_CHANNEL_ID = "PriorityImageNotificationChannel"
        private const val PRIORITY_NOTIFICATION_ID = 1
        private const val ACTION_START = "com.mangodb.statbuddy.START"
        private const val ACTION_STOP = "com.mangodb.statbuddy.STOP"
        private const val EXTRA_IMAGE_URI = "image_uri"

        // 알림 갱신 주기 단축 (5초)
        private const val NOTIFICATION_UPDATE_INTERVAL = 5000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var showHeadsUp = false
    private var imageUri: Uri? = null
    private var cachedBitmap: Bitmap? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createPriorityChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
                val newImageUri = imageUriString?.toUri()

                // 헤드업 표시 여부 확인
                showHeadsUp = intent.getBooleanExtra("show_heads_up", false)

                if (imageUri != newImageUri) {
                    imageUri = newImageUri
                    loadImageBitmap()
                } else {
                    // 이미지는 동일해도 헤드업 상태가 변경되었을 수 있으므로 알림 갱신
                    showPriorityNotification()
                }

                if (updateRunnable == null) {
                    startNotificationUpdateTimer()
                }
            }
            ACTION_STOP -> {
                stopService()
            }
        }
        return START_STICKY
    }

    private fun createPriorityChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // 기존 채널이 있다면 삭제
            notificationManager.deleteNotificationChannel(PRIORITY_CHANNEL_ID)

            // 채널 이름의 특수문자를 변경하여 더 상단에 표시되도록 함
            val name = "!#최상위 알림"
            val descriptionText = "최상위 우선순위로 표시되는 알림 채널"

            var importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(PRIORITY_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                // 알림음과 진동은 비활성화하되 높은 우선순위는 유지
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                // 알림음과 진동은 명시적으로 끄기
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)

                // 알림 배지(도트) 표시 활성화
                setShowBadge(true)

                // 중요도는 HIGH로 설정했지만 알림음은 없음
                importance = NotificationManager.IMPORTANCE_HIGH
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadImageBitmap() {
        if (imageUri == null) {
            cachedBitmap = null
            return
        }

        coroutineScope.launch {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(contentResolver, imageUri!!)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                }

                cachedBitmap = bitmap

                withContext(Dispatchers.Main) {
                    showPriorityNotification()
                }
            } catch (e: Exception) {
                Log.e("PriorityNotificationService", "이미지 로딩 오류", e)
                cachedBitmap = null
            }
        }
    }

    private fun showPriorityNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 현재 시간에서 미래로 설정하여 알림 정렬에서 상단에 오도록 함
        val futureTime = System.currentTimeMillis() + 1000

        val builder = NotificationCompat.Builder(this, PRIORITY_CHANNEL_ID)
            .setContentTitle("이미지 표시 중")
            .setContentText("이 알림이 항상 최상단에 표시됩니다")
            .setSmallIcon(R.drawable.ic_bis)
            // 최대 우선순위 설정
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // 전화 통화 카테고리는 일반적으로 최고 우선순위를 가짐
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            // 알림 순서에 영향을 미치는 시간 설정
            .setWhen(futureTime)
            // 소리와 진동은 명시적으로 끄기
            .setSilent(true)
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
            // 체계적 우선순위 설정
            .setGroup("high_priority_group")
            .setGroupSummary(true)
            // 알림 배지(도트) 설정
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)

        // 헤드업 알림 표시 여부 (showHeadsUp 플래그로 제어)
        if (showHeadsUp) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        // 캐시된 이미지가 있으면 큰 아이콘 및 확장 스타일 설정
        cachedBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
                .setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?))
        }

        // 서비스를 포그라운드로 시작
        startForeground(PRIORITY_NOTIFICATION_ID, builder.build())
    }

    private fun startNotificationUpdateTimer() {
        updateRunnable = object : Runnable {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun run() {
                val notificationManager = NotificationManagerCompat.from(this@PriorityNotificationService)
                try {
                    // 알림 갱신 (매번 미래 타임스탬프로 새로고침)
                    val notification = createNotificationWithoutHeadsUp()
                    notificationManager.notify(PRIORITY_NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.e("PriorityService", "알림 업데이트 실패", e)
                }

                // 짧은 주기로 다시 예약
                handler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL)
            }
        }

        handler.postDelayed(updateRunnable!!, NOTIFICATION_UPDATE_INTERVAL)
    }

    private fun createNotificationWithoutHeadsUp(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 매 갱신마다 미래 시간으로 설정하여 최상단 유지
        val futureTime = System.currentTimeMillis() + 1000

        val builder = NotificationCompat.Builder(this, PRIORITY_CHANNEL_ID)
            .setContentTitle("이미지 표시 중")
            .setContentText("이 알림이 항상 최상단에 표시됩니다")
            .setSmallIcon(R.drawable.ic_bis)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(futureTime)
            .setSilent(true)
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
            // 그룹 설정 유지
            .setGroup("high_priority_group")
            .setGroupSummary(true)
            // 알림 배지(도트) 설정
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)

        // 캐시된 이미지 추가
        cachedBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
                .setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?))
        }

        return builder.build()
    }

    private fun stopService() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(PRIORITY_NOTIFICATION_ID)

        cachedBitmap?.recycle()
        cachedBitmap = null
        imageUri = null

        stopSelf()
    }

    override fun onDestroy() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
        cachedBitmap?.recycle()
        cachedBitmap = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}