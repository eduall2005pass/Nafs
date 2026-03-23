package com.nafsshield.service

import com.nafsshield.R

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nafsshield.data.repository.NafsRepository
import com.nafsshield.ui.MainActivity
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MasterService : LifecycleService() {

    companion object {
        const val TAG = "MasterService"

        @Volatile var blockedPackages   = emptySet<String>()
        @Volatile var allowedBrowsers   = emptySet<String>()
        @Volatile var activeKeywords    = emptySet<String>()
        @Volatile var isRunning         = false
        @Volatile var totalBlockedToday = 0
    }

    private lateinit var repository: NafsRepository
    private lateinit var pinManager: PinManager

    override fun onCreate() {
        super.onCreate()
        repository = NafsRepository.getInstance(this)
        pinManager = PinManager(this)

        createNotificationChannels()
        // startForeground অবশ্যই onCreate/onStartCommand এর ৫ সেকেন্ডের মধ্যে call করতে হবে
        startForeground(Constants.NOTIF_ID_MASTER, buildNotification())

        isRunning = true
        Log.i(TAG, "MasterService started")

        observeBlocklists()

        lifecycleScope.launch(Dispatchers.IO) {
            totalBlockedToday = repository.todayBlockCount()
            repository.cleanOldLogs()
        }

        if (pinManager.isVpnEnabled) startVpnService()
    }

    private fun observeBlocklists() {
        repository.allBlockedApps.observe(this) { apps ->
            blockedPackages = apps.map { it.packageName }.toSet()
            Log.d(TAG, "Blocked apps: ${blockedPackages.size}")
        }
        repository.allAllowedBrowsers.observe(this) { browsers ->
            allowedBrowsers = browsers.map { it.packageName }.toSet()
        }
        repository.allKeywords.observe(this) { keywords ->
            // শুধু active keywords cache করো
            activeKeywords = keywords
                .filter { it.isActive }
                .map { if (it.isCaseSensitive) it.word else it.word.lowercase() }
                .toSet()
            Log.d(TAG, "Active keywords: ${activeKeywords.size}")
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, NafsVpnService::class.java).apply {
            action = Constants.ACTION_START_VPN
        }
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, NafsVpnService::class.java).apply {
            action = Constants.ACTION_STOP_VPN
        }
        startService(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            Constants.ACTION_STOP_MASTER -> stopSelf()
            Constants.ACTION_START_VPN   -> startVpnService()
            Constants.ACTION_STOP_VPN    -> stopVpnService()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopVpnService()
        scheduleRestart(100)
        scheduleRestart(3000)
        super.onDestroy()
        Log.i(TAG, "MasterService destroyed — restart scheduled")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed — scheduling restart")
        scheduleRestart(100)
        scheduleRestart(2000)
        scheduleRestart(5000)
    }

    private fun scheduleRestart(delayMs: Long = 100L) {
        if (!pinManager.isMasterEnabled) return
        try {
            val reqCode = (9000 + delayMs / 100).toInt()
            val pi = android.app.PendingIntent.getBroadcast(
                this, reqCode,
                android.content.Intent(this, com.nafsshield.receiver.BootReceiver::class.java)
                    .apply { action = "com.nafsshield.RESTART_SERVICE" },
                android.app.PendingIntent.FLAG_IMMUTABLE or
                android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
                .setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMs, pi)
            Log.d(TAG, "Restart scheduled in ${delayMs}ms")
        } catch (e: Exception) { Log.e(TAG, "scheduleRestart: ${e.message}") }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_GUARD)
            .setContentTitle("NafsShield সক্রিয় 🛡️")
            .setContentText(getString(R.string.today_count, totalBlockedToday))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    Constants.CHANNEL_ID_GUARD,
                    "NafsShield Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Background guard service" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    Constants.CHANNEL_ID_ALERT,
                    "Block Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "App/site blocked notifications" }
            )
        }
    }
}
