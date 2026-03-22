package com.nafsshield.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import com.nafsshield.data.model.BlockReason

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler       = Handler(Looper.getMainLooper())
    private var currentOverlay: View? = null

    fun showBlockOverlay(pkg: String, reason: BlockReason, keyword: String? = null) {
        if (!android.provider.Settings.canDrawOverlays(context)) return

        handler.post {
            removeCurrentOverlay()

            val overlayView = buildOverlayView(reason, keyword)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }

            try {
                windowManager.addView(overlayView, params)
                currentOverlay = overlayView
                handler.postDelayed({ removeCurrentOverlay() }, 2000)
            } catch (e: Exception) {
                // WindowManager.BadTokenException ইত্যাদি — ignore
            }
        }
    }
    
    fun showPersistentBlockOverlay(message: String, durationMs: Long = 5000) {
        if (!android.provider.Settings.canDrawOverlays(context)) return
        
        handler.post {
            removeCurrentOverlay()
            
            val overlayView = buildPersistentOverlayView(message)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                // Make it NOT focusable so system events still work, but intercept touches
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply { 
                gravity = Gravity.TOP or Gravity.START
            }
            
            try {
                windowManager.addView(overlayView, params)
                currentOverlay = overlayView
                handler.postDelayed({ removeCurrentOverlay() }, durationMs)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    private fun buildPersistentOverlayView(message: String): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 10, 14, 26))
            setPadding(64, 64, 64, 64)
        }

        val icon = TextView(context).apply {
            text     = "🚫"
            textSize = 80f
            gravity  = Gravity.CENTER
        }

        val title = TextView(context).apply {
            text      = "⚠️ সুরক্ষা সতর্কতা"
            textSize  = 32f
            setTextColor(Color.parseColor("#FF5555"))
            gravity   = Gravity.CENTER
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
        }

        val messageView = TextView(context).apply {
            text = message
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity  = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }

        container.addView(icon)
        container.addView(title)
        container.addView(messageView)
        return container
    }

    private fun buildOverlayView(reason: BlockReason, keyword: String?): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(Color.argb(235, 10, 14, 26))
            setPadding(64, 64, 64, 64)
        }

        val icon = TextView(context).apply {
            text     = "🛡️"
            textSize = 64f
            gravity  = Gravity.CENTER
        }

        val title = TextView(context).apply {
            text      = "NafsShield"
            textSize  = 28f
            setTextColor(Color.WHITE)
            gravity   = Gravity.CENTER
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
        }

        val message = TextView(context).apply {
            text = when (reason) {
                BlockReason.APP_BLOCKED          -> "এই অ্যাপটি ব্লক করা আছে"
                BlockReason.BROWSER_NOT_ALLOWED  -> "এই ব্রাউজার অনুমোদিত নয়"
                BlockReason.KEYWORD_FOUND        ->
                    "⚠️ অনুপযুক্ত বিষয়বস্তু শনাক্ত হয়েছে" +
                    (keyword?.let { "\n\"$it\"" } ?: "")
                BlockReason.DNS_BLOCKED          -> "এই সাইট ব্লক করা আছে"
            }
            textSize = 17f
            setTextColor(Color.parseColor("#AABBDD"))
            gravity  = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        container.addView(icon)
        container.addView(title)
        container.addView(message)
        return container
    }

    private fun removeCurrentOverlay() {
        currentOverlay?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            currentOverlay = null
        }
    }
}
