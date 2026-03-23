package com.haseeb.recorder

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Floating overlay during recording — OneUI 8.5 frosted glass pill.
 * Mic and internal audio icons swap between on/off drawables.
 * Uses direct callback from ScreenRecordService (no broadcasts).
 */
class RecordingOverlayService : Service(), ScreenRecordService.Companion.AudioStateListener {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private var micIcon: ImageView? = null
    private var internalAudioIcon: ImageView? = null
    private var timerView: Chronometer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Register as the direct listener
        ScreenRecordService.audioStateListener = this
        createOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenRecordService.audioStateListener = null
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }

    // Called directly by ScreenRecordService when toggle occurs
    override fun onAudioStateChanged() {
        micIcon?.post { updateIconStates() }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun createOverlay() {
        // ── Root pill container ──────────────────────────────────────
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#CC1A1D2E"))
                setStroke(dp(1), Color.parseColor("#33A0B4C8"))
            }
            elevation = dp(8).toFloat()
        }

        // ── Recording indicator (pulsing red dot) ───────────────────
        val redDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(8)
            }
        }
        ValueAnimator.ofFloat(1f, 0.3f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { redDot.alpha = it.animatedValue as Float }
            start()
        }
        pill.addView(redDot)

        // ── Timer ───────────────────────────────────────────────────
        timerView = Chronometer(this).apply {
            base = SystemClock.elapsedRealtime()
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) }
            start()
        }
        pill.addView(timerView)

        // ── Divider ─────────────────────────────────────────────────
        pill.addView(createDivider())

        // ── Mic toggle ──────────────────────────────────────────────
        micIcon = ImageView(this).apply {
            updateMicIcon(this)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startService(Intent(this@RecordingOverlayService, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_TOGGLE_MIC
                })
            }
        }
        pill.addView(micIcon)

        // ── Internal audio toggle ───────────────────────────────────
        internalAudioIcon = ImageView(this).apply {
            updateAudioIcon(this)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                marginEnd = dp(12)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startService(Intent(this@RecordingOverlayService, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_TOGGLE_INTERNAL_AUDIO
                })
            }
        }
        pill.addView(internalAudioIcon)

        // ── Divider ─────────────────────────────────────────────────
        pill.addView(createDivider())

        // ── Stop button ─────────────────────────────────────────────
        val stopBtn = ImageView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            setImageResource(R.drawable.ic_stop)
            setColorFilter(Color.WHITE)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginStart = dp(12)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startService(Intent(this@RecordingOverlayService, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                })
            }
        }
        pill.addView(stopBtn)

        // ── Window layout params ────────────────────────────────────
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        // ── Drag handling ───────────────────────────────────────────
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        pill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(pill, params)
                    true
                }
                else -> false
            }
        }

        overlayView = pill
        windowManager?.addView(pill, params)

        // Entrance animation
        pill.translationY = -dp(80).toFloat()
        pill.alpha = 0f
        pill.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(18))
        }
    }

    private fun updateMicIcon(iv: ImageView) {
        if (ScreenRecordService.isMicEnabled) {
            iv.setImageResource(R.drawable.ic_mic)
            iv.setColorFilter(Color.WHITE)
        } else {
            iv.setImageResource(R.drawable.ic_mic_off)
            iv.setColorFilter(Color.parseColor("#66FFFFFF"))
        }
    }

    private fun updateAudioIcon(iv: ImageView) {
        if (ScreenRecordService.isInternalAudioEnabled) {
            iv.setImageResource(R.drawable.ic_internal_audio)
            iv.setColorFilter(Color.WHITE)
        } else {
            iv.setImageResource(R.drawable.ic_internal_audio_off)
            iv.setColorFilter(Color.parseColor("#66FFFFFF"))
        }
    }

    private fun updateIconStates() {
        micIcon?.let { updateMicIcon(it) }
        internalAudioIcon?.let { updateAudioIcon(it) }
    }
}

