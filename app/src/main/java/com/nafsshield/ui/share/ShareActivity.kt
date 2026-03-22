package com.nafsshield.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.nafsshield.util.PinManager
import com.nafsshield.viewmodel.MainViewModel

class ShareActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        val sharedText = when {
            intent?.action == Intent.ACTION_SEND &&
            intent.type?.startsWith("text/") == true ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: ""
            else -> ""
        }

        if (sharedText.isBlank()) { finish(); return }

        val isUrl = sharedText.startsWith("http://") ||
                    sharedText.startsWith("https://") ||
                    sharedText.contains(".")

        val typeLabel = if (isUrl) "🌐 Website Block" else "🔑 Keyword Block"
        val itemLabel = if (isUrl) {
            sharedText.removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").substringBefore("/").take(50)
        } else {
            sharedText.take(50)
        }

        AlertDialog.Builder(this)
            .setTitle(typeLabel)
            .setMessage(itemLabel + " NafsShield এ block করবেন?")
            .setPositiveButton("✅ Block করুন") { _, _ ->
                addToNafsShield(isUrl, itemLabel, sharedText)
            }
            .setNegativeButton("❌ বাতিল") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun addToNafsShield(isUrl: Boolean, label: String, original: String) {
        val pinManager = PinManager(this)
        if (isUrl) {
            pinManager.addBlockedWebsite(original)
            Toast.makeText(this, label + " block হয়েছে ✅", Toast.LENGTH_SHORT).show()
        } else {
            val vm = ViewModelProvider(this)[MainViewModel::class.java]
            vm.addKeyword(original)
            Toast.makeText(this, label + " keyword যোগ হয়েছে ✅", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
