package com.haseeb.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The main launcher activity. Its only job on OneUI-based devices that
 * lack a built-in screen recorder is to:
 *   1. Request RECORD_AUDIO + POST_NOTIFICATIONS permissions.
 *   2. Request SYSTEM_ALERT_WINDOW (overlay) permission.
 *   3. Instruct the user how to add the Quick Settings tile.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_PERMISSIONS = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request overlay permission if not already granted
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow overlay permission, then re-open the app", Toast.LENGTH_LONG).show()
        }

        // Request runtime permissions
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            showReady()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            showReady()
        }
    }

    private fun showReady() {
        Toast.makeText(
            this,
            "Screen Recorder is ready!\nPull down your Quick Settings and add the \"Screen Record\" tile.",
            Toast.LENGTH_LONG
        ).show()
        // We can finish — the app is headless from here on. The user
        // interacts only via the QS tile and the floating pill.
        finish()
    }
}

