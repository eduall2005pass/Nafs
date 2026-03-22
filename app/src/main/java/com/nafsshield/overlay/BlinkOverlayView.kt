package com.nafsshield.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import android.util.TypedValue

class BlinkOverlayView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bannerView: LinearLayout? = null
    private var animator: ValueAnimator? = null

    companion object {
        const val TAG = "BlinkOverlay"
    }

    fun showAndExit(keyword: String, onExitDone: () -> Unit) {
        try {
            if (bannerView != null) return

            // Small top banner — শুধু keyword দেখাবে
            val banner = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.argb(230, 180, 0, 0))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 20, 32, 20)
            }

            // 🚫 icon
            val icon = TextView(context).apply {
                text = "🚫"
                textSize = 18f
                setPadding(0, 0, 16, 0)
            }

            // Keyword text — matched word red highlight
            val label = TextView(context).apply {
                text = ""$keyword" detected"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.05f
            }

            banner.addView(icon)
            banner.addView(label)
            bannerView = banner

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }

            windowManager.addView(banner, params)

            // Vibrate: buzz-buzz
            vibrate()

            // Blink: banner alpha flash করবে
            animator = ValueAnimator.ofFloat(1f, 0.15f).apply {
                duration = 100
                repeatCount = 4
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { anim ->
                    banner.alpha = anim.animatedValue as Float
                }
                start()
            }

            // 0.5s পর dismiss + home
            banner.postDelayed({
                dismiss()
                onExitDone()
            }, 500)

        } catch (e: Exception) {
            Log.e(TAG, "showAndExit error: ${e.message}")
            dismiss()
            onExitDone()
        }
    }

    private fun vibrate() {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 120), -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 120, 60, 120), -1)
            }
        } catch (e: Exception) { Log.e(TAG, "Vibrate: ${e.message}") }
    }

    fun dismiss() {
        try {
            animator?.cancel()
            animator = null
            bannerView?.let { windowManager.removeView(it); bannerView = null }
        } catch (e: Exception) { Log.e(TAG, "Dismiss: ${e.message}") }
    }
}
