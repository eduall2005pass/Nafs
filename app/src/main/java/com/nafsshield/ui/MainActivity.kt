package com.nafsshield.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nafsshield.R
import com.nafsshield.admin.NafsDeviceAdmin
import com.nafsshield.service.MasterService
import com.nafsshield.ui.pin.PinActivity
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
import com.nafsshield.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var pinManager: PinManager

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startGuard()
    }
    
    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Result is handled, app won't minimize
        // Admin status will update in onResume of SettingsFragment
    }

    private var lastPausedTime = 0L
    private val LOCK_TIMEOUT_MS = 30_000L  // 30 সেকেন্ড background এ থাকলে PIN চাইবে

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            lastPausedTime = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isChangingConfigurations && pinManager.isPinSetup && !PinActivity.isVerified) {
            val elapsed = System.currentTimeMillis() - lastPausedTime
            // শুধু 30 সেকেন্ডের বেশি background এ থাকলে PIN চাইবে
            if (elapsed > LOCK_TIMEOUT_MS || lastPausedTime == 0L) {
                startActivity(Intent(this, PinActivity::class.java).apply {
                    putExtra(PinActivity.MODE, PinActivity.MODE_VERIFY)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinManager = PinManager(this)

        // PIN setup হয়নি → setup screen
        if (!pinManager.isPinSetup) {
            startActivity(Intent(this, PinActivity::class.java).apply {
                putExtra(PinActivity.MODE, PinActivity.MODE_SETUP)
            })
            finish()
            return
        }

        // PIN verify হয়নি → verify screen
        if (!PinActivity.isVerified) {
            startActivity(Intent(this, PinActivity::class.java).apply {
                putExtra(PinActivity.MODE, PinActivity.MODE_VERIFY)
            })
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupNavigation()

        // Overlay permission check (optional prompt)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        // Auto-start guard if was enabled
        if (pinManager.isMasterEnabled && !MasterService.isRunning) {
            checkAndStartGuard()
        }
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        NavigationUI.setupWithNavController(
            findViewById<BottomNavigationView>(R.id.bottom_nav),
            navHost.navController
        )
    }

    fun checkAndStartGuard() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnLauncher.launch(vpnIntent)
        } else {
            startGuard()
        }
    }

    private fun startGuard() {
        val intent = Intent(this, MasterService::class.java).apply {
            action = Constants.ACTION_START_MASTER
        }
        ContextCompat.startForegroundService(this, intent)
        pinManager.isMasterEnabled = true
    }

    fun activateDeviceAdmin() {
        val dpm            = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, NafsDeviceAdmin::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "NafsShield কে Device Admin করলে uninstall কঠিন হয়ে যাবে।"
                )
            }
            deviceAdminLauncher.launch(intent)
        }
    }
}
