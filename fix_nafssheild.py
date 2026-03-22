#!/usr/bin/env python3
"""
NafsShield - Deep crash fix (3 files)
Termux এ project root এ যাও তারপর: python fix_nafssheild.py
"""

import os, shutil, re

# ── File paths ────────────────────────────────────────────────────────
BASE_CANDIDATES = [
    ".",
    "NafsShield",
]

def find_base():
    for base in BASE_CANDIDATES:
        manifest = os.path.join(base, "app/src/main/AndroidManifest.xml")
        if os.path.isfile(manifest):
            return base
    custom = input("Project root path দাও (যেখানে app/ folder আছে): ").strip()
    manifest = os.path.join(custom, "app/src/main/AndroidManifest.xml")
    if os.path.isfile(manifest):
        return custom
    print("❌ Project root পাওয়া যায়নি।")
    exit(1)

base = find_base()
print(f"✅ Project found: {os.path.abspath(base)}\n")

MANIFEST   = os.path.join(base, "app/src/main/AndroidManifest.xml")
MAIN_ACT   = os.path.join(base, "app/src/main/java/com/nafsshield/ui/MainActivity.kt")
SETTINGS_F = os.path.join(base, "app/src/main/java/com/nafsshield/ui/settings/SettingsFragment.kt")

for f in [MANIFEST, MAIN_ACT, SETTINGS_F]:
    if not os.path.isfile(f):
        print(f"❌ File missing: {f}")
        exit(1)
    shutil.copy2(f, f + ".bak")
    print(f"✅ Backup: {f}.bak")

print()

# ════════════════════════════════════════════════════════════════════
# FIX 1: AndroidManifest.xml — singleTask → standard
# ════════════════════════════════════════════════════════════════════
# BUG: MainActivity এ singleTask launchMode আছে।
# singleTask এ ActivityResultLauncher কাজ করে না —
# PinActivity finish() করলে RESULT_OK কখনো MainActivity তে পৌঁছায় না।
# তাই PIN verify করলেও dashboard crash করে।

with open(MANIFEST, "r", encoding="utf-8") as f:
    manifest_content = f.read()

# MainActivity এর launchMode singleTask → standard
fixed_manifest = re.sub(
    r'(android:name="\.ui\.MainActivity"[^>]*?)android:launchMode="singleTask"',
    r'\1android:launchMode="standard"',
    manifest_content,
    flags=re.DOTALL
)

if fixed_manifest == manifest_content:
    print("⚠️  Manifest: launchMode pattern not found — skipping (may already be fixed)")
else:
    with open(MANIFEST, "w", encoding="utf-8") as f:
        f.write(fixed_manifest)
    print("✅ Fix 1: AndroidManifest.xml — singleTask → standard")

# ════════════════════════════════════════════════════════════════════
# FIX 2: MainActivity.kt — PIN flow overhaul
# ════════════════════════════════════════════════════════════════════

MAIN_FIXED = '''package com.nafsshield.ui

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
    private var wentToBackground = false
    private var isPinActive = false

    // VPN permission launcher
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startGuard()
    }

    // Device admin launcher
    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // PIN launcher — RESULT_OK হলে app দেখাবে, না হলে বন্ধ
    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isPinActive = false
        if (result.resultCode == RESULT_OK) {
            PinActivity.isVerified = true
            wentToBackground = false
        } else {
            // PIN cancel/back — app বন্ধ করো
            finishAffinity()
        }
    }

    // PIN change launcher — শুধু Settings থেকে change PIN এর জন্য
    // এতে onStop/onStart এর isVerified logic disturb হবে না
    val pinChangeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* PIN change হলে কিছু করার নেই */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setContentView সবার আগে — NavHostFragment inflate করতে হবে
        setContentView(R.layout.activity_main)

        pinManager = PinManager(this)
        viewModel  = ViewModelProvider(this)[MainViewModel::class.java]
        setupNavigation()

        // Fresh launch এ PIN check করো
        if (savedInstanceState == null) {
            when {
                !pinManager.isPinSetup  -> launchPin(PinActivity.MODE_SETUP)
                !PinActivity.isVerified -> launchPin(PinActivity.MODE_VERIFY)
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        if (pinManager.isMasterEnabled && !MasterService.isRunning) {
            checkAndStartGuard()
        }
    }

    override fun onStop() {
        super.onStop()
        // PIN screen চলাকালীন বা rotation এ background flag সেট করবো না
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
        if (isPinActive) return  // PIN screen ইতিমধ্যে চলছে

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
'''

with open(MAIN_ACT, "w", encoding="utf-8") as f:
    f.write(MAIN_FIXED)
print("✅ Fix 2: MainActivity.kt — PIN flow + isPinActive guard")

# ════════════════════════════════════════════════════════════════════
# FIX 3: SettingsFragment.kt — PIN change: startActivity → launcher
# ════════════════════════════════════════════════════════════════════
# BUG: SettingsFragment এ PIN change করতে সরাসরি startActivity() use হচ্ছে।
# এতে MainActivity এর onStop() fire হয়, isVerified=false হয়,
# PIN change screen থেকে ফিরলে আবার verify screen আসে → loop/crash।
# Fix: MainActivity এর pinChangeLauncher use করো।

with open(SETTINGS_F, "r", encoding="utf-8") as f:
    settings_content = f.read()

OLD_PIN_CHANGE = '''        view.findViewById<View>(R.id.rowChangePin).setOnClickListener {
            startActivity(Intent(requireContext(), PinActivity::class.java).apply {
                putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)
            })
        }'''

NEW_PIN_CHANGE = '''        view.findViewById<View>(R.id.rowChangePin).setOnClickListener {
            // startActivity() না — MainActivity এর launcher use করো
            // না হলে onStop() isVerified=false করে দেয়, ফিরলে আবার verify আসে
            (requireActivity() as MainActivity).pinChangeLauncher.launch(
                Intent(requireContext(), PinActivity::class.java).apply {
                    putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)
                }
            )
        }'''

if OLD_PIN_CHANGE in settings_content:
    fixed_settings = settings_content.replace(OLD_PIN_CHANGE, NEW_PIN_CHANGE)
    with open(SETTINGS_F, "w", encoding="utf-8") as f:
        f.write(fixed_settings)
    print("✅ Fix 3: SettingsFragment.kt — PIN change uses launcher")
else:
    print("⚠️  SettingsFragment: PIN change pattern not found — check manually")

# ════════════════════════════════════════════════════════════════════
print()
print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
print("✅ সব fix apply হয়েছে!")
print()
print("🔥 Root cause (আসল কারণ):")
print("   MainActivity তে android:launchMode=\"singleTask\" ছিল।")
print("   singleTask এ ActivityResultLauncher কাজ করে না —")
print("   PinActivity RESULT_OK পাঠালেও MainActivity পায় না।")
print("   তাই PIN দেওয়ার পরেও dashboard crash হত।")
print()
print("📋 3টা fix:")
print("   1. AndroidManifest: singleTask → standard")
print("   2. MainActivity: isPinActive guard, clean PIN flow")
print("   3. SettingsFragment: PIN change এ launcher use")
print()
print("▶ এরপর: Build → Clean Project → Rebuild → Install")
print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
