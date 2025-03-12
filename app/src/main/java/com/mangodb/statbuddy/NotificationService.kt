package com.mangodb.statbuddy

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.graphics.scale
import androidx.media.app.NotificationCompat.MediaStyle
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

class NotificationService : Service() {
    companion object {
        private const val MEDIA_CHANNEL_ID = "SuperiorMediaChannel"
        private const val MEDIA_NOTIFICATION_ID = 9999
        private const val ACTION_START = "com.mangodb.statbuddy.SUPERIOR_START"
        private const val ACTION_STOP = "com.mangodb.statbuddy.SUPERIOR_STOP"
        private const val ACTION_PLAY = "com.mangodb.statbuddy.SUPERIOR_PLAY"
        private const val ACTION_PAUSE = "com.mangodb.statbuddy.SUPERIOR_PAUSE"
        private const val EXTRA_IMAGE_URI = "image_uri"
        // Reduced update frequency to save battery
        private const val UPDATE_INTERVAL = 15000L // 15 seconds

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

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var cachedBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var isPlaying = true
    private var isForegroundAppPlaying = false

    // Use Job for structured concurrency
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // More efficient mechanism for updates
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // Audio focus management
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    // For long-term persistent notifications, we don't need audio playback at all
    // Just a dummy flag to track state
    private var dummyAudioState = false

    override fun onCreate() {
        super.onCreate()
        createMediaChannel()
        initMediaSession()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Initialize audio focus listener
        initAudioFocusListener()

        // Request audio focus once at startup, but don't fight for it
        requestAudioFocusMinimal()
    }

    private fun initAudioFocusListener() {
        // Create audio focus listener with weak reference to avoid memory leaks
        val serviceRef = WeakReference(this)
        audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Another app requested focus, we'll yield but keep our notification
                    isForegroundAppPlaying = true
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Only restart if we were playing before
                    isForegroundAppPlaying = false
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Lower volume but keep focus
                    isForegroundAppPlaying = true
                }
            }
            updatePlaybackState()
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "SuperiorMediaSession")

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onPlay() {
                isPlaying = true
                dummyAudioState = true
                updatePlaybackState()
                updateMediaNotification()
            }

            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onPause() {
                isPlaying = false
                dummyAudioState = false
                updatePlaybackState()
                updateMediaNotification()
            }
        })

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "StatBuddy 실행 중")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "알림 유지 중")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "StatBuddy")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 3600000)
            .build()
        mediaSession.setMetadata(metadata)

        updatePlaybackState()
        mediaSession.isActive = true
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        // Using a very small playback speed to maintain "playing" appearance without actual audio
        val playbackSpeed = if (isPlaying && !isForegroundAppPlaying) 0.01f else 0.0f

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, 0, playbackSpeed)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    // Minimal audio focus request that doesn't fight with other apps
    private fun requestAudioFocusMinimal() {
        if (audioFocusChangeListener == null) {
            initAudioFocusListener()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use notification audio attributes
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Release existing request
            releaseAudioFocusRequest()

            // Request minimal focus that doesn't interfere with other apps
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Just log and continue with notification
                Log.i("SuperiorMediaService", "Audio focus request failed, showing notification only")
            }
        } else {
            // For older versions
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i("SuperiorMediaService", "Audio focus request failed, showing notification only")
            }
        }
    }

    private fun releaseAudioFocusRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                try {
                    audioManager.abandonAudioFocusRequest(it)
                } catch (e: Exception) {
                    Log.e("SuperiorMediaService", "Failed to release audio focus", e)
                }
                audioFocusRequest = null
            }
        } else {
            audioFocusChangeListener?.let {
                try {
                    audioManager.abandonAudioFocus(it)
                } catch (e: Exception) {
                    Log.e("SuperiorMediaService", "Failed to release audio focus", e)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
                imageUri = imageUriString?.toUri()

                // Only load bitmap if not already cached
                if (cachedBitmap == null) {
                    loadImageBitmap()
                } else {
                    showMediaNotification()
                }

                // Start periodic updates (less frequent)
                startPeriodicUpdates()

                isPlaying = true
                dummyAudioState = true
                updatePlaybackState()
            }
            ACTION_PLAY -> {
                isPlaying = true
                dummyAudioState = true
                updatePlaybackState()
                updateMediaNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                dummyAudioState = false
                updatePlaybackState()
                updateMediaNotification()
            }
            ACTION_STOP -> {
                stopService()
            }
        }
        return START_STICKY
    }

    // Check music state without changing audio
    private fun checkForegroundMusicState() {
        // Just check if music is active, don't modify
        isForegroundAppPlaying = audioManager.isMusicActive
        // Update state if needed
        updatePlaybackState()
    }

    private fun startPeriodicUpdates() {
        // Cancel existing updates
        updateRunnable?.let { mainHandler.removeCallbacks(it) }

        // Create new update runnable with less frequency
        updateRunnable = object : Runnable {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun run() {
                try {
                    // Update notification
                    updateMediaNotification()

                    // Check music state (without playing any audio)
                    checkForegroundMusicState()

                    // Schedule next update
                    mainHandler.postDelayed(this, UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e("SuperiorMediaService", "Periodic update failed", e)
                }
            }
        }

        // Start periodic updates
        updateRunnable?.let { mainHandler.post(it) }
    }

    private fun createMediaChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Don't recreate if channel exists
            val existingChannel = notificationManager.getNotificationChannel(MEDIA_CHANNEL_ID)
            if (existingChannel != null) {
                return
            }

            val name = "! StatBuddy Noti"
            val descriptionText = "StatBuddy 아이콘 알림"

            val channel = NotificationChannel(MEDIA_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = descriptionText
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadImageBitmap() {
        if (imageUri == null) {
            showMediaNotification()
            return
        }

        // Load image only if needed
        serviceScope.launch {
            try {
                // Only load if not cached
                if (cachedBitmap == null) {
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

                    // Resize bitmap to save memory (using smaller size)
                    cachedBitmap = if (bitmap.width > 64 || bitmap.height > 64) {
                        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val targetWidth = if (ratio > 1) 64 else (64 * ratio).toInt()
                        val targetHeight = if (ratio <= 1) 64 else (64 / ratio).toInt()
                        val scaled = bitmap.scale(targetWidth, targetHeight)
                        bitmap.recycle() // Recycle original to save memory
                        scaled
                    } else {
                        bitmap
                    }
                }

                withContext(Dispatchers.Main) {
                    showMediaNotification()
                }
            } catch (e: Exception) {
                Log.e("SuperiorMediaService", "Image loading error", e)
                withContext(Dispatchers.Main) {
                    showMediaNotification()
                }
            }
        }
    }

    private fun showMediaNotification() {
        val notification = createMediaNotification()
        startForeground(MEDIA_NOTIFICATION_ID, notification)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateMediaNotification() {
        try {
            val notificationManager = NotificationManagerCompat.from(this)
            val notification = createMediaNotification()
            notificationManager.notify(MEDIA_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("SuperiorMediaService", "Failed to update notification", e)
        }
    }

    private fun createMediaNotification(): Notification {
        // Create PendingIntent for main activity
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Play/Pause action
        val playPauseIcon = if (isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY

        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, NotificationService::class.java).apply { action = playPauseAction },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Keep using future time for priority
        val futureTime = System.currentTimeMillis() + 100000000L

        // Build notification
        val builder = NotificationCompat.Builder(this, MEDIA_CHANNEL_ID)
            .setContentTitle("⚡ " + (if (isPlaying) "재생 중" else "일시정지"))
            .setContentText("StatBuddy 실행 중" + (if (isForegroundAppPlaying) " (기기 오디오 재생 중)" else ""))
            .setSubText("! StatBuddy 알림")
            .setSmallIcon(R.drawable.ic_bis)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(futureTime)
            .setSilent(true)
            .setVibrate(null)
            .setSound(null)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(playPauseIcon, "재생/일시정지", playPauseIntent)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)

        // Add large icon if available
        cachedBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        return builder.build()
    }

    private fun stopService() {
        // Clean up all resources
        updateRunnable?.let { mainHandler.removeCallbacks(it) }
        updateRunnable = null

        // Cancel all coroutines
        serviceJob.cancel()

        // Release audio focus
        releaseAudioFocusRequest()
        audioFocusChangeListener = null

        // Release MediaSession
        try {
            if (::mediaSession.isInitialized) {
                mediaSession.release()
            }
        } catch (e: Exception) {
            Log.e("SuperiorMediaService", "Failed to release MediaSession", e)
        }

        // Clean up bitmap resources
        try {
            cachedBitmap?.recycle()
            cachedBitmap = null
        } catch (e: Exception) {
            Log.e("SuperiorMediaService", "Failed to release bitmap", e)
        }

        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Optional - only restart if necessary
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}