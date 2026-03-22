package com.nafsshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.nafsshield.service.MasterService
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Constants.SAMSUNG_BOOT_ACTION
        )
        if (intent.action !in validActions) return

        Log.i("BootReceiver", "Boot completed — starting NafsShield")

        val pinManager = PinManager(context)
        if (pinManager.isMasterEnabled) {
            val serviceIntent = Intent(context, MasterService::class.java).apply {
                action = Constants.ACTION_START_MASTER
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
