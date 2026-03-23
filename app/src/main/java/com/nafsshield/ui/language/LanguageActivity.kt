package com.nafsshield.ui.language

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nafsshield.R
import com.nafsshield.ui.MainActivity
import java.util.Locale

class LanguageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        val btnEnglish = findViewById<LinearLayout>(R.id.btnLangEnglish)
        val btnBangla = findViewById<LinearLayout>(R.id.btnLangBangla)
        val btnContinue = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnContinue)

        var selectedLang = "en"

        // Default - English selected
        updateSelection(btnEnglish, btnBangla, "en")

        btnEnglish.setOnClickListener {
            selectedLang = "en"
            updateSelection(btnEnglish, btnBangla, "en")
        }

        btnBangla.setOnClickListener {
            selectedLang = "bn"
            updateSelection(btnEnglish, btnBangla, "bn")
        }

        btnContinue.setOnClickListener {
            saveLanguage(selectedLang)
            applyLanguage(selectedLang)
            // App restart করো language apply করতে
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun updateSelection(btnEn: LinearLayout, btnBn: LinearLayout, lang: String) {
        val activeColor = getColor(R.color.accent_emerald)
        val inactiveColor = getColor(R.color.bg_card_border)
        if (lang == "en") {
            btnEn.background.setTint(activeColor)
            btnBn.background.setTint(inactiveColor)
        } else {
            btnBn.background.setTint(activeColor)
            btnEn.background.setTint(inactiveColor)
        }
    }

    private fun saveLanguage(lang: String) {
        getSharedPreferences("nafsshield_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", lang).apply()
    }

    private fun applyLanguage(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    companion object {
        fun applyStoredLanguage(context: Context) {
            val lang = context.getSharedPreferences("nafsshield_prefs", Context.MODE_PRIVATE)
                .getString("app_language", "en") ?: "en"
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }

        fun isLanguageSet(context: Context): Boolean {
            return context.getSharedPreferences("nafsshield_prefs", Context.MODE_PRIVATE)
                .contains("app_language")
        }
    }
}
