package com.nafsshield.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private var wentToBackground = false
    private var isPinActive = false

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startGuard()
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this@MainActivity)) {
            if (pinManager.isMasterEnabled && !MasterService.isRunning) {
                checkAndStartGuard()
            }
        }
    }

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isPinActive = false
        if (result.resultCode == RESULT_OK) {
            PinActivity.isVerified = true
            wentToBackground = false
        } else {
            finishAffinity()
        }
    }

    val pinChangeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setContentView সবার আগে — NavHostFragment inflate এর জন্য জরুরি
        setContentView(R.layout.activity_main)

        pinManager = PinManager(this)
        viewModel  = ViewModelProvider(this)[MainViewModel::class.java]
        setupNavigation()

        if (savedInstanceState == null) {
            when {
                !pinManager.isPinSetup  -> launchPin(PinActivity.MODE_SETUP)
                !PinActivity.isVerified -> launchPin(PinActivity.MODE_VERIFY)
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
        if (pinManager.isMasterEnabled && !MasterService.isRunning) {
            checkAndStartGuard()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isPinActive) {
            wentToBackground = true
            PinActivity.isVerified = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (isChangingConfigurations) return
        if (!::pinManager.isInitialized) return
        if (!pinManager.isPinSetup) return
        if (isPinActive) return
        if (wentToBackground && !PinActivity.isVerified) {
            launchPin(PinActivity.MODE_VERIFY)
            wentToBackground = false
        }
    }

    fun launchPin(mode: String) {
        if (isPinActive) return
        isPinActive = true
        pinLauncher.launch(Intent(this, PinActivity::class.java).apply {
            putExtra(PinActivity.MODE, mode)
        })
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        NavigationUI.setupWithNavController(bottomNav, navController)
    }

    fun checkAndStartGuard() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) vpnLauncher.launch(vpnIntent)
        else startGuard()
    }

    private fun startGuard() {
        ContextCompat.startForegroundService(this,
            Intent(this, MasterService::class.java).apply {
                action = Constants.ACTION_START_MASTER
            })
        pinManager.isMasterEnabled = true
    }

    fun activateDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, NafsDeviceAdmin::class.java)
        if (!dpm.isAdminActive(admin)) {
            deviceAdminLauncher.launch(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "NafsShield কে Device Admin করলে uninstall কঠিন হয়ে যাবে।")
                }
            )
        }
    }
}
