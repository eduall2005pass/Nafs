package com.nafsshield.admin
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nafsshield.ui.pin.PinActivity
class NafsDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) { super.onEnabled(context, intent) }
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        context.startActivity(Intent(context, PinActivity::class.java).apply {
            putExtra(PinActivity.MODE, PinActivity.MODE_VERIFY_ADMIN)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        return "⛔ PIN ছাড়া Device Admin বন্ধ করা যাবে না।"
    }
    override fun onDisabled(context: Context, intent: Intent) { super.onDisabled(context, intent) }
}
