package com.haseeb.recorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * A transparent activity that handles:
 * 1. Runtime permission requests (RECORD_AUDIO)
 * 2. MediaProjection consent dialog
 * 3. A 3-second countdown before recording starts
 *
 * This activity finishes itself immediately after handing off
 * the projection token to ScreenRecordService.
 */
class MediaProjectionPermissionActivity : Activity() {

    companion object {
        const val TAG = "MediaProjPermission"
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val REQUEST_AUDIO_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity has no layout — it's transparent
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        // POST_NOTIFICATIONS required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                REQUEST_AUDIO_PERMISSION
            )
        } else {
            requestMediaProjection()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            // Even if audio is denied, we can still record without mic
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Start 3-second countdown then launch service
                Toast.makeText(this, "Recording starts in 3…", Toast.LENGTH_SHORT).show()

                object : CountDownTimer(3000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000) + 1
                        // Countdown shown via toasts for simplicity
                    }

                    override fun onFinish() {
                        val serviceIntent = Intent(
                            this@MediaProjectionPermissionActivity,
                            ScreenRecordService::class.java
                        ).apply {
                            action = ScreenRecordService.ACTION_START
                            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                            putExtra(ScreenRecordService.EXTRA_DATA, data)
                        }
                        startForegroundService(serviceIntent)
                        finish()
                    }
                }.start()
            } else {
                Log.w(TAG, "MediaProjection permission denied")
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

