# NafsShield - Complete Fix Documentation

## 🛡️ All Issues Fixed

### ✅ 1. NafsShield No Longer Appears in Block List
**Problem:** Users could select and block NafsShield itself from the app blocking list.

**Fix:** Updated `MainViewModel.kt` - `loadInstalledApps()` method now:
- Filters out the app's own package name
- Excludes already blocked apps from the selection list
- Only shows non-system apps that can actually be blocked

**Files Modified:**
- `app/src/main/java/com/nafsshield/viewmodel/MainViewModel.kt`

---

### ✅ 2. Custom Browser Support (Any App as Browser)
**Problem:** Only predefined browsers could be allowed. Users couldn't add custom browsers or other apps.

**Fix:** Updated `SettingsFragment.kt` - `showBrowserPicker()` method now:
- Shows ALL installed non-system apps
- Allows selecting any app as an "allowed browser"
- Filters out NafsShield itself
- Filters out already allowed apps

**Files Modified:**
- `app/src/main/java/com/nafsshield/ui/settings/SettingsFragment.kt`

---

### ✅ 3. Device Admin Activation No Longer Minimizes App
**Problem:** When tapping "Device Admin" in settings, the app would minimize/go to background, making it confusing.

**Fix:** Updated `MainActivity.kt`:
- Added `ActivityResultLauncher` for device admin activation
- Properly handles the result without app minimization
- Settings screen updates automatically when returning

**Files Modified:**
- `app/src/main/java/com/nafsshield/ui/MainActivity.kt`

---

### ✅ 4. CRITICAL: Aggressive Uninstall Prevention
**Problem:** Users had enough time to click "OK" on uninstall dialogs before protection kicked in.

**Fix:** Completely rewritten `NafsAccessibilityService.kt` with multiple protection layers:

#### Layer 1: Instant Detection
- Detects uninstall screen immediately via multiple class name patterns
- Added comprehensive list of uninstall activity names for all major Android OEMs:
  - Stock Android, Samsung, Xiaomi/MIUI, Huawei, Oppo, Vivo, OnePlus

#### Layer 2: Multiple Back Actions
- Sends 3 rapid BACK actions immediately on detection
- Follows with HOME action after 50ms
- Shows persistent blocking overlay for 8 seconds

#### Layer 3: Button Blocking
- Automatically tries to click "Cancel" or "No" buttons
- Searches for keywords in multiple languages (English, Bengali, Korean)
- Prevents "OK" or "Uninstall" button clicks

#### Layer 4: Settings Protection
- Detects when viewing NafsShield's app info page
- Continuous monitoring every 300ms when in Settings
- Scans all text on screen for "nafsshield" or package name
- Immediately blocks with 5 BACK actions if detected

#### Layer 5: Persistent Overlay
- Shows large, attention-grabbing warning overlay
- Stays on screen for 5-8 seconds
- Clear Bengali message explaining protection is active

**Files Modified:**
- `app/src/main/java/com/nafsshield/service/NafsAccessibilityService.kt`
- `app/src/main/java/com/nafsshield/util/Constants.kt`
- `app/src/main/java/com/nafsshield/overlay/OverlayManager.kt`

**New Protection Patterns Added to Constants.kt:**
```kotlin
UNINSTALL_ACTIVITIES = setOf(
    "com.android.packageinstaller.UninstallerActivity",
    "com.google.android.packageinstaller.UninstallerActivity",
    "com.samsung.android.packageinstaller.UninstallerActivity",
    "com.miui.packageinstaller.UninstallerActivity",
    "com.huawei.android.packageinstaller.UninstallerActivity",
    "com.oppo.packageinstaller.UninstallerActivity",
    "com.vivo.packageinstaller.UninstallerActivity",
    "com.oneplus.packageinstaller.UninstallerActivity",
    "UninstallerActivity", // Generic fallback
    "UninstallAppProgress"
)

APP_INFO_ACTIVITIES = setOf(
    "com.android.settings.applications.InstalledAppDetails",
    "com.android.settings.InstalledAppDetails",
    "com.samsung.android.settings.applications.InstalledAppDetails",
    "com.miui.appmanager.ApplicationsDetailsActivity",
    "AppInfoDashboardFragment",
    "InstalledAppDetailsTop"
)
```

---

### ✅ 5. Recent Tasks / Background Activity Protection
**Problem:** Users could disable the app from Recent Tasks by turning off "Background activity".

**Solution Implemented:**
- **Continuous Settings Monitoring:** Active scanning every 300ms when Settings app is open
- **Proactive Detection:** Detects NafsShield mentions in settings before dangerous actions can be taken
- **Multi-language Protection:** Scans for "nafsshield", "com.nafsshield", and various translations
- **Immediate Blocking:** 5 rapid BACK presses + HOME action within milliseconds
- **Persistent Warning:** 5-second blocking overlay with clear warning message

**Additional Protection:**
- `stopWithTask="false"` in service manifests (prevents service from stopping)
- Foreground service type `specialUse` (harder to kill)
- Boot receiver with priority 1000 (auto-restart on device boot)

---

### ✅ 6. Custom Schedule Feature (NEW)
**New Feature:** Users can now create custom blocking schedules.

**What Was Added:**
- New `Schedule` entity in database with migration
- `ScheduleFragment.kt` - UI for managing schedules
- `ScheduleAdapter.kt` - RecyclerView adapter
- `ScheduleDao.kt` - Database operations
- Integration in `MainViewModel` and `NafsRepository`

**Schedule Features:**
- Set start and end times (24-hour format)
- Choose days of the week
- Enable/disable schedules with switch
- Multiple schedules support
- Bengali language UI

**Files Created:**
- `app/src/main/java/com/nafsshield/ui/schedule/ScheduleFragment.kt`
- `app/src/main/java/com/nafsshield/ui/schedule/ScheduleAdapter.kt`

**Files Modified:**
- `app/src/main/java/com/nafsshield/data/model/Models.kt`
- `app/src/main/java/com/nafsshield/data/db/dao/Daos.kt`
- `app/src/main/java/com/nafsshield/data/db/NafsShieldDatabase.kt` (v1→v2 migration)
- `app/src/main/java/com/nafsshield/data/repository/NafsRepository.kt`
- `app/src/main/java/com/nafsshield/viewmodel/MainViewModel.kt`

---

## 🎨 Icon Visibility
**Status:** App icon is properly configured.

**Configuration:**
- Adaptive icon with shield + lock design
- Located in `res/mipmap-*/ic_launcher.xml`
- Foreground: Blue shield with white lock
- Background: Dark (#0D1117)
- Should be visible in launcher

**If icon still not showing:**
1. Uninstall old version completely
2. Reinstall new APK
3. Restart device if needed
4. Check launcher settings (some launchers hide apps)

---

## 📦 Complete File List of Changes

### Core Protection Files:
1. `NafsAccessibilityService.kt` - Main protection logic ⚠️ CRITICAL CHANGES
2. `Constants.kt` - Protection patterns and settings
3. `OverlayManager.kt` - Blocking overlay UI
4. `MainActivity.kt` - Device admin launcher fix

### UI Files:
5. `SettingsFragment.kt` - Custom browser support
6. `MainViewModel.kt` - Schedule support + app filtering
7. `ScheduleFragment.kt` - NEW: Schedule management UI
8. `ScheduleAdapter.kt` - NEW: Schedule list adapter

### Data Layer:
9. `Models.kt` - Schedule entity
10. `Daos.kt` - ScheduleDao interface
11. `NafsShieldDatabase.kt` - Database v2 migration
12. `NafsRepository.kt` - Schedule repository methods

---

## 🚀 How to Build and Install

### Prerequisites:
- Android Studio Hedgehog or newer
- JDK 17 or newer
- Android SDK with API 34

### Build Steps:

1. **Extract the ZIP file:**
   ```bash
   unzip NafsShield_Fixed.zip
   cd NafsShield
   ```

2. **Open in Android Studio:**
   - File → Open → Select `NafsShield` folder
   - Wait for Gradle sync to complete

3. **Build APK:**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Or use terminal:
     ```bash
     ./gradlew assembleRelease
     ```

4. **Install:**
   - Find APK in `app/build/outputs/apk/release/`
   - Transfer to device and install
   - Grant all required permissions:
     - Accessibility Service ✅
     - VPN Service ✅
     - Display over other apps ✅
     - Device Administrator ✅

---

## 🔒 Security Features Summary

### Protection Levels:
1. **Device Admin:** Prevents simple uninstall
2. **Accessibility Service:** Monitors and blocks dangerous actions
3. **Foreground Service:** Keeps protection running
4. **Overlay Permission:** Shows blocking screens
5. **VPN Service:** DNS-level blocking

### What's Protected:
✅ Uninstall prevention (aggressive multi-layer)
✅ Force stop prevention
✅ Disable prevention
✅ Clear data prevention
✅ Settings tampering prevention
✅ Recent tasks protection
✅ Background activity protection

### What's Monitored:
- Settings app (continuous 300ms scan)
- Uninstall screens (instant detection)
- App info pages (automatic blocking)
- Dangerous settings screens
- WebView detection
- Keyword scanning

---

## ⚙️ Database Migration

**Version:** 1 → 2

**New Table:** `schedules`
```sql
CREATE TABLE schedules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    startHour INTEGER NOT NULL,
    startMinute INTEGER NOT NULL,
    endHour INTEGER NOT NULL,
    endMinute INTEGER NOT NULL,
    daysOfWeek TEXT NOT NULL,
    isActive INTEGER DEFAULT 1,
    createdAt INTEGER NOT NULL
)
```

**Migration is automatic** - existing users will have their data preserved.

---

## 🧪 Testing Checklist

Before deployment, test:

- [ ] App installs successfully
- [ ] PIN setup works
- [ ] Block an app → verify it blocks
- [ ] Try to uninstall NafsShield → should be blocked
- [ ] Go to Settings → Apps → NafsShield → should be blocked
- [ ] Try "Force Stop" → should be blocked
- [ ] Add custom browser → verify it works
- [ ] Create schedule → verify it saves
- [ ] Device Admin activation → app doesn't minimize
- [ ] NafsShield not in block list
- [ ] Recent tasks → disable background activity → should be blocked

---

## 📝 Notes for Users

### Bengali Messages:
- Uninstall blocked: "⛔ NafsShield আনইনস্টল করা যাবে না!"
- Settings blocked: "⛔ NafsShield সেটিংস পরিবর্তন করা যাবে না!"
- Protection active: "সুরক্ষা ব্যবস্থা সক্রিয় আছে।"

### Permissions Required:
1. **Accessibility Service** - CRITICAL for all protection features
2. **VPN Service** - For DNS blocking
3. **Display over other apps** - For blocking overlays
4. **Device Administrator** - For uninstall protection

### Important:
- If Accessibility Service is disabled, protection won't work
- Keep the app in "Do not optimize battery" list
- Don't force stop the service from Recent Apps
- Protection works even if app is closed

---

## 🐛 Known Limitations

1. **Root Access:** Cannot protect against root users
2. **ADB:** Cannot protect against ADB uninstall
3. **Safe Mode:** Android Safe Mode bypasses accessibility
4. **System App:** Not a system app, so some restrictions apply

---

## 📞 Support

If issues persist after installation:
1. Check all permissions are granted
2. Restart device after installation
3. Enable Accessibility Service manually
4. Activate Device Admin
5. Keep app in battery optimization exceptions

---

**Version:** 2.0 (Fixed)
**Database Version:** 2
**Build Date:** March 2026
**All Critical Security Issues Resolved ✅**
