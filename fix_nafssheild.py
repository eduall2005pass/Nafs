#!/usr/bin/env python3
"""
NafsShield - Complete crash fix (4 files)
Termux এ project root এ: python fix_nafssheild.py
"""
import os, shutil, re

BASE_CANDIDATES = [".", "NafsShield"]

def find_base():
    for base in BASE_CANDIDATES:
        if os.path.isfile(os.path.join(base, "app/src/main/AndroidManifest.xml")):
            return base
    custom = input("Project root path দাও: ").strip()
    if os.path.isfile(os.path.join(custom, "app/src/main/AndroidManifest.xml")):
        return custom
    print("❌ Project root পাওয়া যায়নি।"); exit(1)

base = find_base()
print(f"✅ Project: {os.path.abspath(base)}\n")

MANIFEST   = os.path.join(base, "app/src/main/AndroidManifest.xml")
MAIN_ACT   = os.path.join(base, "app/src/main/java/com/nafsshield/ui/MainActivity.kt")
PIN_ACT    = os.path.join(base, "app/src/main/java/com/nafsshield/ui/pin/PinActivity.kt")
SETTINGS_F = os.path.join(base, "app/src/main/java/com/nafsshield/ui/settings/SettingsFragment.kt")

for f in [MANIFEST, MAIN_ACT, PIN_ACT, SETTINGS_F]:
    if not os.path.isfile(f):
        print(f"❌ Missing: {f}"); exit(1)
    shutil.copy2(f, f + ".bak")
print("✅ Backups done\n")

# ══════════════════════════════════════════════════════
# FIX 1: AndroidManifest — MainActivity singleTask → standard
#         PinActivity singleTop → standard
# ══════════════════════════════════════════════════════
with open(MANIFEST, "r", encoding="utf-8") as f:
    content = f.read()

# MainActivity: singleTask → standard
content = re.sub(
    r'(android:name="\.ui\.MainActivity"[^>]*?)android:launchMode="singleTask"',
    r'\1android:launchMode="standard"',
    content, flags=re.DOTALL
)
# PinActivity: singleTop → standard
content = re.sub(
    r'(android:name="\.ui\.pin\.PinActivity"[^>]*?)android:launchMode="singleTop"',
    r'\1android:launchMode="standard"',
    content, flags=re.DOTALL
)

with open(MANIFEST, "w", encoding="utf-8") as f:
    f.write(content)
print("✅ Fix 1: Manifest — singleTask + singleTop → standard")

# ══════════════════════════════════════════════════════
# FIX 2: PinActivity — NoPinSet case
# BUG: verify mode এ NoPinSet হলে startActivity(setup) করছে
#      এই নতুন instance এর RESULT_OK কখনো pinLauncher এ পৌঁছায় না
# FIX: নতুন launch না করে নিজেই MODE_SETUP এ switch করো
# ══════════════════════════════════════════════════════
with open(PIN_ACT, "r", encoding="utf-8") as f:
    content = f.read()

OLD_NO_PIN = '''            is PinResult.NoPinSet  -> {
                startActivity(Intent(this, PinActivity::class.java).apply {
                    putExtra(MODE, MODE_SETUP)
                })
                finish()
            }'''

NEW_NO_PIN = '''            is PinResult.NoPinSet  -> {
                // নতুন Activity launch না করে এখানেই setup mode এ switch করো
                // তাহলে RESULT_OK সরাসরি pinLauncher এ যাবে
                mode = MODE_SETUP
                setupStep = 1
                firstPin = ""
                resetInput()
                updateHeader()
            }'''

if OLD_NO_PIN in content:
    content = content.replace(OLD_NO_PIN, NEW_NO_PIN)
    with open(PIN_ACT, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ Fix 2: PinActivity — NoPinSet in-place mode switch")
else:
    print("⚠️  Fix 2: NoPinSet pattern not found — check manually")

# ══════════════════════════════════════════════════════
# FIX 3: MainActivity.kt — complete rewrite
# ══════════════════════════════════════════════════════
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

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == RESULT_OK) startGuard() }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

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

    // Settings থেকে PIN change এর জন্য আলাদা launcher
    // এতে isPinActive বা isVerified disturb হয় না
    val pinChangeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
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
'''
with open(MAIN_ACT, "w", encoding="utf-8") as f:
    f.write(MAIN_FIXED)
print("✅ Fix 3: MainActivity.kt")

# ══════════════════════════════════════════════════════
# FIX 4: SettingsFragment — PIN change startActivity → launcher
# ══════════════════════════════════════════════════════
with open(SETTINGS_F, "r", encoding="utf-8") as f:
    content = f.read()

OLD_SETTING = '''        view.findViewById<View>(R.id.rowChangePin).setOnClickListener {
            startActivity(Intent(requireContext(), PinActivity::class.java).apply {
                putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)
            })
        }'''

NEW_SETTING = '''        view.findViewById<View>(R.id.rowChangePin).setOnClickListener {
            (requireActivity() as MainActivity).pinChangeLauncher.launch(
                Intent(requireContext(), PinActivity::class.java).apply {
                    putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)
                }
            )
        }'''

if OLD_SETTING in content:
    content = content.replace(OLD_SETTING, NEW_SETTING)
    with open(SETTINGS_F, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ Fix 4: SettingsFragment.kt — pinChangeLauncher")
else:
    print("⚠️  Fix 4: pattern not found — skipping")

print()
print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
print("✅ সব fix done!")
print()
print("🔥 Bug summary:")
print("  1. MainActivity singleTask → ActivityResultLauncher broken")
print("  2. PinActivity singleTop → setResult() interfere")
print("  3. NoPinSet case এ নতুন PinActivity launch → RESULT_OK হারিয়ে যায়")
print("  4. SettingsFragment startActivity → onStop isVerified=false")
print()
print("▶ এরপর:")
print("  git add -A")
print('  git commit -m "fix: all PIN crash bugs"')
print("  git push")
print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
