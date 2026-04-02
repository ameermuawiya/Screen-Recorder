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
 * Floating overlay during recording — minimal pill with timer + stop button.
 */
class RecordingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
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
        val timerView = Chronometer(this).apply {
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

        // ── Pause / Resume button ───────────────────────────────────
        val pauseBtn = ImageView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#44FFFFFF"))
            }
            setImageResource(R.drawable.ic_pause)
            setColorFilter(Color.WHITE)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginStart = dp(8)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (ScreenRecordService.isPaused) {
                    // Resume
                    startService(Intent(this@RecordingOverlayService, ScreenRecordService::class.java).apply {
                        action = ScreenRecordService.ACTION_RESUME
                    })
                    setImageResource(R.drawable.ic_pause)
                    timerView.start()
                    // Restart red dot pulse
                    redDot.alpha = 1f
                } else {
                    // Pause
                    startService(Intent(this@RecordingOverlayService, ScreenRecordService::class.java).apply {
                        action = ScreenRecordService.ACTION_PAUSE
                    })
                    setImageResource(R.drawable.ic_play)
                    timerView.stop()
                    // Solid red dot while paused
                    redDot.alpha = 1f
                }
            }
        }
        pill.addView(pauseBtn)

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
}
