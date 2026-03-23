package com.haseeb.recorder

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class ScreenRecordTileService : TileService() {

    companion object {
        const val TAG = "ScreenRecordTile"
        const val REQUEST_CODE_MEDIA_PROJECTION = 1001
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (ScreenRecordService.isRecording) {
            // Stop recording
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.label = "Screen Record"
            qsTile?.updateTile()
        } else {
            // Launch the permission activity to get MediaProjection token
            val intent = Intent(this, MediaProjectionPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requires PendingIntent overload
                val pendingIntent = PendingIntent.getActivity(
                    this, REQUEST_CODE_MEDIA_PROJECTION, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (ScreenRecordService.isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Recording"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Record"
        }
        tile.updateTile()
    }
}

