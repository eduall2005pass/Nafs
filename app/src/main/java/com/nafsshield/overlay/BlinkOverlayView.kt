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
class BlinkOverlayView(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var banner: LinearLayout? = null
    private var anim: ValueAnimator? = null
    fun showAndExit(keyword: String, onDone: () -> Unit) {
        try {
            if (banner != null) return
            val b = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.argb(230, 180, 0, 0))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(32, 20, 32, 20)
            }
            b.addView(TextView(context).apply { text = "🚫"; textSize = 18f; setPadding(0,0,16,0) })
            b.addView(TextView(context).apply {
                text = "[$keyword] detected"
                setTextColor(Color.WHITE); textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            banner = b
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }
            wm.addView(b, p)
            try {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0,120,60,120),-1))
                else @Suppress("DEPRECATION") v.vibrate(longArrayOf(0,120,60,120),-1)
            } catch(e:Exception){}
            anim = ValueAnimator.ofFloat(1f,0.15f).apply {
                duration=100; repeatCount=4; repeatMode=ValueAnimator.REVERSE
                addUpdateListener { b.alpha = it.animatedValue as Float }
                start()
            }
            b.postDelayed({ dismiss(); onDone() }, 500)
        } catch(e:Exception){ Log.e("Blink","${e.message}"); dismiss(); onDone() }
    }
    fun dismiss() {
        try { anim?.cancel(); anim=null; banner?.let{ wm.removeView(it); banner=null } } catch(e:Exception){}
    }
}
