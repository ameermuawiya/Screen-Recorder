package com.haseeb.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    companion object {
        const val TAG = "ScreenRecordService"
        const val CHANNEL_ID = "screen_record_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.haseeb.recorder.ACTION_START"
        const val ACTION_STOP = "com.haseeb.recorder.ACTION_STOP"
        const val ACTION_TOGGLE_MIC = "com.haseeb.recorder.ACTION_TOGGLE_MIC"
        const val ACTION_TOGGLE_INTERNAL_AUDIO = "com.haseeb.recorder.ACTION_TOGGLE_INTERNAL_AUDIO"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        var isRecording = false
            private set

        var isMicEnabled = true
        var isInternalAudioEnabled = true

        // Direct callback for overlay icon updates (replaces unreliable broadcasts)
        interface AudioStateListener {
            fun onAudioStateChanged()
        }
        var audioStateListener: AudioStateListener? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var outputFilePath: String = ""

    // Required callback for Android 14+ before createVirtualDisplay()
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            mainHandler.post {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (data != null) {
                    // MUST call startForeground with mediaProjection type BEFORE
                    // getMediaProjection() on Android 14+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                    startRecording(resultCode, data)
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE_MIC -> {
                isMicEnabled = !isMicEnabled
                Log.d(TAG, "Mic toggled: $isMicEnabled")
                audioStateListener?.onAudioStateChanged()
            }
            ACTION_TOGGLE_INTERNAL_AUDIO -> {
                isInternalAudioEnabled = !isInternalAudioEnabled
                Log.d(TAG, "Internal audio toggled: $isInternalAudioEnabled")
                audioStateListener?.onAudioStateChanged()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // Android 14+ REQUIRES registering a callback BEFORE createVirtualDisplay()
        mediaProjection?.registerCallback(projectionCallback, mainHandler)

        // Prepare output file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ScreenRecord_$timestamp.mp4"

        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val recorderDir = File(moviesDir, "ScreenRecorder")
        if (!recorderDir.exists()) recorderDir.mkdirs()
        val outputFile = File(recorderDir, fileName)
        outputFilePath = outputFile.absolutePath

        // Set up MediaRecorder
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder?.apply {
            if (isMicEnabled) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (isMicEnabled) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
            }
            setVideoSize(screenWidth, screenHeight)
            setVideoFrameRate(60)
            setVideoEncodingBitRate(8_000_000)
            setOutputFile(outputFilePath)
            prepare()
        }

        // Create VirtualDisplay (callback is already registered above)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null, null
        )

        mediaRecorder?.start()
        isRecording = true
        Log.d(TAG, "Recording started → $outputFilePath")

        // Show the floating pill overlay
        val overlayIntent = Intent(this, RecordingOverlayService::class.java)
        startService(overlayIntent)
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        }
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}
        mediaProjection?.stop()
        mediaProjection = null

        isRecording = false
        Log.d(TAG, "Recording stopped → $outputFilePath")

        // Save to MediaStore for Gallery visibility
        saveToMediaStore()

        // Stop the floating pill overlay
        val overlayIntent = Intent(this, RecordingOverlayService::class.java)
        stopService(overlayIntent)
    }

    private fun saveToMediaStore() {
        if (outputFilePath.isEmpty()) return
        val file = File(outputFilePath)
        if (!file.exists()) return

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/ScreenRecorder")
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        Log.d(TAG, "Saved to MediaStore: ${file.name}")
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recording")
            .setContentText("Tap to stop recording")
            .setSmallIcon(R.drawable.ic_screen_record)
            .setOngoing(true)
            .addAction(R.drawable.ic_screen_record, "Stop", stopPendingIntent)
            .setContentIntent(stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active screen recording notification"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

