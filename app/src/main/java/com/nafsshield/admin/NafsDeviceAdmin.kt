package com.nafsshield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NafsDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("NafsDeviceAdmin", "Device Admin ENABLED ✅")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Accessibility service দিয়ে screen block করা হবে
        // এখানে warning message দেখাও
        return "⛔ NafsShield সরানো সম্ভব নয়! PIN ছাড়া Device Admin বন্ধ করা যাবে না।"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w("NafsDeviceAdmin", "Device Admin DISABLED ⚠️")
    }
}
