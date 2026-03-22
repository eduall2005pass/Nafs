package com.nafsshield.receiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nafsshield.service.MasterService
import com.nafsshield.service.NafsVpnService
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON", Constants.SAMSUNG_BOOT_ACTION)) return
        val pm = PinManager(context)
        if (!pm.isMasterEnabled) return
        ContextCompat.startForegroundService(context,
            Intent(context, MasterService::class.java).apply { action = Constants.ACTION_START_MASTER })
        if (pm.isVpnEnabled)
            ContextCompat.startForegroundService(context,
                Intent(context, NafsVpnService::class.java).apply { action = Constants.ACTION_START_VPN })
    }
}
