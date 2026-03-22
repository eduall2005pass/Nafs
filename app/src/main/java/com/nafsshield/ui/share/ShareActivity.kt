package com.nafsshield.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nafsshield.util.PinManager
import com.nafsshield.util.PinResult
import com.nafsshield.viewmodel.MainViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Share intent receiver — অন্য app থেকে text/URL share করলে এখানে আসে।
 * PIN ছাড়াই confirm dialog দেখায় — user OK করলে keyword/website হিসেবে add হয়।
 */
class ShareActivity : AppCompatActivity() {

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        // Transparent — শুধু dialog দেখাবে

        val sharedText = when {
            intent?.action == Intent.ACTION_SEND &&
            intent.type?.startsWith("text/") == true ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: ""
            else -> ""
        }

        if (sharedText.isBlank()) { finish(); return }

        // URL নাকি keyword?
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
            .setMessage(""$itemLabel" NafsShield এ block করবেন?")
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
            // Website block — PIN ছাড়াই add (confirm ই যথেষ্ট)
            pinManager.addBlockedWebsite(original)
            Toast.makeText(this, ""$label" block হয়েছে ✅", Toast.LENGTH_SHORT).show()
        } else {
            // Keyword — PIN ছাড়াই add
            val vm = ViewModelProvider(this)[MainViewModel::class.java]
            vm.addKeyword(original)
            Toast.makeText(this, ""$label" keyword যোগ হয়েছে ✅", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
