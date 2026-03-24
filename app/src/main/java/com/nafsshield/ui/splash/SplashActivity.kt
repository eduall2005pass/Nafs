package com.nafsshield.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nafsshield.R
import android.os.Handler
import android.os.Looper
import android.view.View
import com.nafsshield.ui.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = newBase.getSharedPreferences("nafsshield_prefs", android.content.Context.MODE_PRIVATE)
            .getString("app_language", "en") ?: "en"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.ivSplashLogo)
        val title = findViewById<TextView>(R.id.tvSplashTitle)
        val arabic = findViewById<TextView>(R.id.tvSplashArabic)
        val subtitle = findViewById<TextView>(R.id.tvSplashSubtitle)

        // Animations
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)

        logo.startAnimation(fadeIn)
        title.startAnimation(slideUp)
        arabic.startAnimation(fadeIn)
        subtitle.startAnimation(fadeIn)

        // Dot animation
        val dot1 = findViewById<View>(R.id.dot1)
        val dot2 = findViewById<View>(R.id.dot2)
        val dot3 = findViewById<View>(R.id.dot3)
        val dots = listOf(dot1, dot2, dot3)
        val handler = Handler(Looper.getMainLooper())
        var step = 0

        val dotRunnable = object : Runnable {
            override fun run() {
                dots.forEach { it.setBackgroundResource(R.drawable.dot_inactive) }
                dots[step % 3].setBackgroundResource(R.drawable.dot_active)
                step++
                handler.postDelayed(this, 400)
            }
        }
        handler.post(dotRunnable)

        // 2 সেকেন্ড পর MainActivity তে যাও
        logo.postDelayed({
            handler.removeCallbacks(dotRunnable)
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2000)
    }
}
