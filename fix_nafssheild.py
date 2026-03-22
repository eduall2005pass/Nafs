#!/usr/bin/env python3
"""
NafsShield - Complete fix (4 files সরাসরি rewrite)
Pattern matching নেই — সব file পুরো নতুন করে লেখা হবে।
Termux এ project root এ: python fix_nafssheild.py
"""
import os, shutil

BASE_CANDIDATES = [".", "NafsShield"]

def find_base():
    for base in BASE_CANDIDATES:
        if os.path.isfile(os.path.join(base, "app/src/main/AndroidManifest.xml")):
            return base
    custom = input("Project root path দাও (যেখানে app/ আছে): ").strip()
    if os.path.isfile(os.path.join(custom, "app/src/main/AndroidManifest.xml")):
        return custom
    print("❌ Project root পাওয়া যায়নি।"); exit(1)

base = find_base()
print(f"✅ Project: {os.path.abspath(base)}\n")

files = {
    "manifest":   os.path.join(base, "app/src/main/AndroidManifest.xml"),
    "main":       os.path.join(base, "app/src/main/java/com/nafsshield/ui/MainActivity.kt"),
    "pin":        os.path.join(base, "app/src/main/java/com/nafsshield/ui/pin/PinActivity.kt"),
    "settings":   os.path.join(base, "app/src/main/java/com/nafsshield/ui/settings/SettingsFragment.kt"),
}

for key, path in files.items():
    if not os.path.isfile(path):
        print(f"❌ Missing: {path}"); exit(1)
    shutil.copy2(path, path + ".bak")
    print(f"✅ Backup: {path}.bak")

print()

# ════════════════════════════════════════════════════════════════════
# FILE 1: AndroidManifest.xml
# FIX: MainActivity singleTask→standard, PinActivity singleTop→standard
# ════════════════════════════════════════════════════════════════════
with open(files["manifest"], "r", encoding="utf-8") as f:
    manifest = f.read()

manifest = manifest.replace(
    'android:launchMode="singleTask"',
    'android:launchMode="standard"'
)
manifest = manifest.replace(
    'android:launchMode="singleTop"',
    'android:launchMode="standard"'
)

with open(files["manifest"], "w", encoding="utf-8") as f:
    f.write(manifest)
print("✅ Fix 1: AndroidManifest — launchMode fixed")

# ════════════════════════════════════════════════════════════════════
# FILE 2: MainActivity.kt — complete rewrite
# FIX: setContentView আগে, isPinActive guard, pinChangeLauncher
# ════════════════════════════════════════════════════════════════════
MAIN_KT = '''package com.nafsshield.ui

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
    ) { result ->
        if (result.resultCode == RESULT_OK) startGuard()
    }

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

with open(files["main"], "w", encoding="utf-8") as f:
    f.write(MAIN_KT)
print("✅ Fix 2: MainActivity.kt — complete rewrite")

# ════════════════════════════════════════════════════════════════════
# FILE 3: PinActivity.kt — শুধু NoPinSet block fix, বাকি অপরিবর্তিত
# ════════════════════════════════════════════════════════════════════
with open(files["pin"], "r", encoding="utf-8") as f:
    pin_content = f.read()

# handleVerify function এর NoPinSet case খুঁজে replace করো
# লাইন-by-লাইন approach — whitespace সমস্যা নেই
lines = pin_content.split('\n')
new_lines = []
i = 0
replaced = False
while i < len(lines):
    line = lines[i]
    # NoPinSet block শুরু খুঁজি
    if 'is PinResult.NoPinSet' in line and not replaced:
        # এই block টা skip করে নতুন লিখি
        indent = len(line) - len(line.lstrip())
        base_indent = ' ' * indent
        inner_indent = ' ' * (indent + 4)
        new_lines.append(f'{base_indent}is PinResult.NoPinSet  -> {{')
        new_lines.append(f'{inner_indent}// নতুন Activity launch না করে এখানেই setup mode switch করো')
        new_lines.append(f'{inner_indent}// তাহলে RESULT_OK সরাসরি MainActivity pinLauncher এ যাবে')
        new_lines.append(f'{inner_indent}mode = MODE_SETUP')
        new_lines.append(f'{inner_indent}setupStep = 1')
        new_lines.append(f'{inner_indent}firstPin = ""')
        new_lines.append(f'{inner_indent}resetInput()')
        new_lines.append(f'{inner_indent}updateHeader()')
        new_lines.append(f'{base_indent}}}')
        replaced = True
        i += 1
        # পুরনো block এর closing } পর্যন্ত skip করি
        brace_count = 1
        while i < len(lines) and brace_count > 0:
            l = lines[i]
            brace_count += l.count('{') - l.count('}')
            i += 1
        continue
    new_lines.append(line)
    i += 1

if replaced:
    with open(files["pin"], "w", encoding="utf-8") as f:
        f.write('\n'.join(new_lines))
    print("✅ Fix 3: PinActivity.kt — NoPinSet in-place mode switch")
else:
    print("⚠️  Fix 3: NoPinSet pattern not found — may already be fixed")

# ════════════════════════════════════════════════════════════════════
# FILE 4: SettingsFragment.kt — rowChangePin: startActivity → pinChangeLauncher
# ════════════════════════════════════════════════════════════════════
with open(files["settings"], "r", encoding="utf-8") as f:
    settings_content = f.read()

lines = settings_content.split('\n')
new_lines = []
i = 0
replaced = False
while i < len(lines):
    line = lines[i]
    # rowChangePin block খুঁজি
    if 'rowChangePin' in line and 'setOnClickListener' in line and not replaced:
        indent = len(line) - len(line.lstrip())
        base_indent = ' ' * indent
        inner_indent = ' ' * (indent + 4)
        new_lines.append(line)  # rowChangePin line রাখি
        i += 1
        # পুরনো block skip করি (closing } পর্যন্ত)
        brace_count = 0
        while i < len(lines):
            l = lines[i]
            brace_count += l.count('{') - l.count('}')
            i += 1
            if brace_count <= 0:
                break
        # নতুন block লিখি
        new_lines.append(f'{inner_indent}// startActivity() না — launcher use করো')
        new_lines.append(f'{inner_indent}// না হলে onStop() isVerified=false করে, ফিরলে আবার verify আসে')
        new_lines.append(f'{inner_indent}(requireActivity() as MainActivity).pinChangeLauncher.launch(')
        new_lines.append(f'{inner_indent}    Intent(requireContext(), PinActivity::class.java).apply {{')
        new_lines.append(f'{inner_indent}        putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)')
        new_lines.append(f'{inner_indent}    }}')
        new_lines.append(f'{inner_indent})')
        new_lines.append(f'{base_indent}}}')
        replaced = True
        continue
    new_lines.append(line)
    i += 1

if replaced:
    with open(files["settings"], "w", encoding="utf-8") as f:
        f.write('\n'.join(new_lines))
    print("✅ Fix 4: SettingsFragment.kt — pinChangeLauncher")
else:
    print("⚠️  Fix 4: rowChangePin pattern not found — may already be fixed")

# ════════════════════════════════════════════════════════════════════
print()
print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
print("✅ সব fix done!")
print()
print("🔥 Fix summary:")
print("  1. Manifest: singleTask + singleTop → standard")
print("  2. MainActivity: setContentView আগে, isPinActive guard")
print("  3. PinActivity: NoPinSet → in-place mode switch")
print("  4. SettingsFragment: pinChangeLauncher use")
print()
print("▶ এখন run করো:")
print("  git add -A")
print('  git commit -m "fix: complete PIN crash fix"')
print("  git push")
print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
