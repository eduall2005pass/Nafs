package com.nafsshield.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nafsshield.data.model.BlockLog
import com.nafsshield.data.model.BlockReason
import com.nafsshield.data.repository.NafsRepository
import com.nafsshield.overlay.OverlayManager
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
import kotlinx.coroutines.*

class NafsAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "NafsAccessibility"
        @Volatile var instance: NafsAccessibilityService? = null
        @Volatile var gracePeriodUntil: Long = 0L
        fun grantGracePeriod(ms: Long = 15000L) { gracePeriodUntil = System.currentTimeMillis() + ms }
        fun isInGracePeriod() = System.currentTimeMillis() < gracePeriodUntil
        val isRunning get() = instance != null
        
        // Correct PIN দেওয়ার পর 4 সেকেন্ড monitoring pause
        }

        // Samsung/Android system UI packages যেখানে "Stop" button আসে
        val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.lool",       // Samsung Device Care
            "com.samsung.android.sm",
            "com.samsung.android.sm.policy",
            "android"
        )

        // "Stop" বাটনের text variants (বাংলা + English + Korean)
        val STOP_BUTTON_TEXTS = listOf(
            "stop", "বন্ধ", "강제 종료", "force stop",
            "close", "end", "종료", "중지"
        )
        
        val BG_ACTIVITY_TITLES = listOf(
            "check background activity",
            "background activity",
            "background app"
        )
    }

    private lateinit var repository: NafsRepository
    private lateinit var overlayManager: OverlayManager
    private lateinit var pinManager: PinManager

    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastPkg   = ""
    private var scanJob: Job? = null
    private var settingsMonitorJob: Job? = null
    private var isInSettings = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance       = this
        repository     = NafsRepository.getInstance(this)
        overlayManager = OverlayManager(this)
        pinManager     = PinManager(this)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or   // Long press detect
                AccessibilityEvent.TYPE_VIEW_SELECTED           // Drag select detect
            feedbackType    = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags           = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                              AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Log.i(TAG, "AccessibilityService connected ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return  // নিজের app ignore

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                handleWindowChange(pkg, event)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                if (pkg == lastPkg) scheduleKeywordScan(pkg)

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                // Long press — launcher এ হলে uninstall drag zone check করো
                if (Constants.LAUNCHER_PACKAGES.contains(pkg)) {
                    mainHandler.postDelayed({ checkLauncherUninstallDrag() }, 300)
                    mainHandler.postDelayed({ checkLauncherUninstallDrag() }, 800)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                // Drag selection — launcher এ NafsShield drag হচ্ছে কিনা
                if (Constants.LAUNCHER_PACKAGES.contains(pkg)) {
                    mainHandler.postDelayed({ checkLauncherUninstallDrag() }, 200)
                }
            }
        }
    }
        if (isInGracePeriod()) return

    private fun handleWindowChange(pkg: String, event: AccessibilityEvent) {
        lastPkg = pkg

        // Grace period এ আছি — কিছু block করবো না
        if (isInGracePeriod()) return

        // Stop settings monitoring if we left settings
        if (isInSettings && !Constants.SETTINGS_PACKAGES.contains(pkg)) {
            isInSettings = false
            settingsMonitorJob?.cancel()
        }

        // 1. Blocked app check (O(1) — in-memory set)
        if (MasterService.blockedPackages.contains(pkg)) {
            blockAndGoHome(pkg, BlockReason.APP_BLOCKED)
            return
        }

        // 2. Browser whitelist check
        if (Constants.ALL_KNOWN_BROWSERS.contains(pkg) &&
            !MasterService.allowedBrowsers.contains(pkg)) {
            blockAndGoHome(pkg, BlockReason.BROWSER_NOT_ALLOWED)
            return
        }

        // 3. In-app WebView detect (allowed browser এ থাকলে skip)
        if (!MasterService.allowedBrowsers.contains(pkg) && detectWebView()) {
            blockAndGoHome(pkg, BlockReason.BROWSER_NOT_ALLOWED)
            return
        }

        // 4. Settings screen (uninstall / force stop protect)
        if (Constants.SETTINGS_PACKAGES.contains(pkg)) {
            isInSettings = true
            startContinuousSettingsMonitoring()
            handleSettingsScreen(event)
            return
        }

        // 5. Uninstall screen — class name + package name দুটোই check করো
        val cls = event.className?.toString() ?: ""
        val isUninstallActivity = Constants.UNINSTALL_ACTIVITIES.any {
            cls.contains(it, ignoreCase = true)
        }
        val isPackageInstaller = pkg.contains("packageinstaller", ignoreCase = true) ||
                                 pkg == "com.android.packageinstaller" ||
                                 pkg == "com.google.android.packageinstaller"
        if (isUninstallActivity || isPackageInstaller) {
            // NafsShield কে uninstall করার চেষ্টা হচ্ছে কিনা check করো
            mainHandler.postDelayed({
                val root2 = rootInActiveWindow
                if (root2 != null) {
                    val txt = extractAllTextFromNode(root2).lowercase()
                    if (txt.contains("nafsshield") || txt.contains("com.nafsshield")) {
                        handleUninstallScreen()
                    }
                    root2.recycle()
                } else {
                    // Root পাওয়া না গেলেও block করো (safety)
                    if (isUninstallActivity) handleUninstallScreen()
                }
            }, 150)
            return
        }
        
        // 6. Device Admin deactivate screen — block completely
        if (Constants.DEVICE_ADMIN_ACTIVITIES.any { cls.contains(it) || pkg.contains(it) }) {
            handleDeviceAdminScreen()
            return
        }

        // 7. App Info screen - detect NafsShield app info page
        if (Constants.APP_INFO_ACTIVITIES.any { cls.contains(it) }) {
            handleAppInfoScreen(event)
            return
        }

        // 7. System UI "Stop" button detect
        if (SYSTEM_UI_PACKAGES.contains(pkg)) {
            mainHandler.postDelayed({ checkAndBlockStopButton() }, 150)
        }
        
        // 7b. "Check background activity" dialog detect
        val windowTitle = event.text?.joinToString(" ")?.lowercase() ?: ""
        val className2 = event.className?.toString() ?: ""
        if (BG_ACTIVITY_TITLES.any { windowTitle.contains(it) } ||
            pkg == "com.android.systemui" && windowTitle.contains("background")) {
            mainHandler.postDelayed({ blockBgActivityStopButton() }, 200)
        }

        // 8. Launcher long-press uninstall drag detect
        if (Constants.LAUNCHER_PACKAGES.contains(pkg)) {
            mainHandler.postDelayed({ checkLauncherUninstallDrag() }, 200)
        }

        // 9. Keyword scan — NafsShield নিজের screen এ scan করবে না
        if (pkg != packageName) scheduleKeywordScan(pkg)
    }
    
    private fun startContinuousSettingsMonitoring() {
        settingsMonitorJob?.cancel()
        settingsMonitorJob = scope.launch {
            while (isActive && isInSettings) {
                delay(300) // Check every 300ms
                mainHandler.post {
                    checkForNafsShieldInSettings()
                }
            }
        }
    }
        if (isInGracePeriod()) return
    
    private fun checkForNafsShieldInSettings() {
        if (isInGracePeriod()) return
        try {
            val root = rootInActiveWindow ?: return
            val allText = extractAllTextFromNode(root).lowercase()
            
            if (allText.contains("nafsshield") || allText.contains("com.nafsshield")) {
                Log.d(TAG, "⚠️ Continuous monitor: NafsShield detected in settings!")
                
                // Try to click dangerous buttons to disable them
                blockDangerousButtons(root)
                
                // Then block the screen
                blockSettingsImmediately()
            }
            
            root.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Settings monitor error: ${e.message}")
        }
    }
    
    private fun blockDangerousButtons(root: AccessibilityNodeInfo) {
        try {
            // Disable dangerous buttons by making them unclickable
            val dangerousKeywords = listOf(
                "uninstall", "আনইনস্টল", "force stop", "ফোর্স স্টপ", "stop app", "end", 
                "disable", "নিষ্ক্রিয়", "clear data", "ডেটা মুছে", "turn off", "deactivate",
                "stop", "বন্ধ", "remove", "সরান"
            )
            
            disableButtonsWithKeywords(root, dangerousKeywords)
        } catch (e: Exception) {
            Log.e(TAG, "Error blocking buttons: ${e.message}")
        }
    }
    
    private fun disableButtonsWithKeywords(node: AccessibilityNodeInfo?, keywords: List<String>) {
        if (node == null) return
        
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""
        
        // Check if this is a button with dangerous text
        if ((className.contains("Button") || node.isClickable) && 
            keywords.any { text.contains(it) || desc.contains(it) }) {
            
            // Try to disable it by clicking it to trigger our blocking
            Log.d(TAG, "Found dangerous button: $text")
        }
        
        // Recurse through children
        for (i in 0 until node.childCount) {
            disableButtonsWithKeywords(node.getChild(i), keywords)
        }
    }
    
        if (isInGracePeriod()) return
    private fun handleDeviceAdminScreen() {
        if (isInGracePeriod()) return
        Log.d(TAG, "🔒 Device Admin screen — launching PIN")
        startActivity(android.content.Intent(this,
            com.nafsshield.ui.pin.PinActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                     android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(com.nafsshield.ui.pin.PinActivity.MODE,
                     com.nafsshield.ui.pin.PinActivity.MODE_VERIFY_ADMIN)
        })
    }
        if (isInGracePeriod()) return

    private fun handleAppInfoScreen(event: AccessibilityEvent) {
        if (isInGracePeriod()) return
        // Check if this is showing NafsShield's app info
        mainHandler.postDelayed({
            val root = rootInActiveWindow
            if (root != null) {
                val allText = extractAllTextFromNode(root).lowercase()
                if (allText.contains("nafsshield") || allText.contains("com.nafsshield")) {
                    Log.d(TAG, "⚠️ CRITICAL: App Info page for NafsShield detected!")
                    blockSettingsImmediately()
                }
                root.recycle()
            }
        }, 100) // Small delay to let content load
    }

    private fun detectWebView(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val found = hasWebViewNode(root)
            root.recycle()
            found
        } catch (e: Exception) { false }
    }

    private fun hasWebViewNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (Constants.WEBVIEW_CLASSES.contains(node.className?.toString())) return true
        for (i in 0 until node.childCount) {
            if (hasWebViewNode(node.getChild(i))) return true
        }
        return false
        if (isInGracePeriod()) return
    }

    private fun handleSettingsScreen(event: AccessibilityEvent) {
        if (isInGracePeriod()) return
        // Immediately block if accessing NafsShield's app info
        val root = rootInActiveWindow
        if (root != null) {
            val allText = extractAllTextFromNode(root).lowercase()
            
            // Check if this is NafsShield's app info page
            if (allText.contains("nafsshield") || 
                allText.contains("com.nafsshield")) {
                Log.d(TAG, "⚠️ CRITICAL: Settings accessing NafsShield!")
                blockSettingsImmediately()
                root.recycle()
                return
            }
            
            // Check for dangerous actions
            if (Constants.DANGEROUS_SETTINGS_TITLES.any { allText.contains(it) } && 
                allText.contains("nafsshield")) {
                Log.d(TAG, "⚠️ Dangerous settings action detected")
                blockSettingsImmediately()
                root.recycle()
                return
            }
            
            root.recycle()
        }
        
        // Also check event text
        val title = event.text?.joinToString(" ")?.lowercase() ?: ""
        if (title.contains("nafsshield") || 
            Constants.DANGEROUS_SETTINGS_TITLES.any { title.contains(it) && title.contains("nafs") }) {
            blockSettingsImmediately()
        }
    }
    
    private fun extractAllTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        collectAllText(node, sb)
        return sb.toString()
    }
    
    private fun collectAllText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), sb)
        }
    }
    
    private fun blockBgActivityStopButton() {
        try {
            val root = rootInActiveWindow ?: return
            val allText = extractAllTextFromNode(root).lowercase()
            // NafsShield এর Stop button আছে কিনা দেখো
            if (!allText.contains("nafsshield")) {
                root.recycle()
                return
            }
            // Stop button খুঁজে intercept করো
            interceptStopButton(root)
            root.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "blockBgActivity: ${e.message}")
        }
    }
    
    private fun blockSettingsImmediately() {
        Log.d(TAG, "🔒 Protected settings — launching PIN")
        startActivity(android.content.Intent(this,
            com.nafsshield.ui.pin.PinActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                     android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(com.nafsshield.ui.pin.PinActivity.MODE,
                     com.nafsshield.ui.pin.PinActivity.MODE_SETTINGS_ACCESS)
        if (isInGracePeriod()) return
        })
    }

    private fun handleUninstallScreen() {
        // Grace period এ আছি — PIN দিয়ে uninstall হচ্ছে
        if (isInGracePeriod()) return
        // ✅ App এর ভেতর থেকে PIN দিয়ে uninstall করা হলে — block করো না
        if (com.nafsshield.util.UninstallGuard.isAllowed()) {
            Log.d(TAG, "✅ User-initiated uninstall — allowing")
            com.nafsshield.util.UninstallGuard.revoke()  // একবারই allow
            return
        }

        Log.d(TAG, "⛔ Unauthorized uninstall attempt — blocking!")

        repeat(3) { performGlobalAction(GLOBAL_ACTION_BACK) }

        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 50)

        mainHandler.postDelayed({
            overlayManager.showPersistentBlockOverlay(
                "⛔ NafsShield আনইনস্টল করা যাবে না!\n\n" +
                "এই অ্যাপটি সুরক্ষিত এবং অপসারণ করা সম্ভব নয়।\n\n" +
                "Device Admin সক্রিয় আছে।",
                8000
            )
        }, 100)

        mainHandler.postDelayed({ tryClickCancelButton() }, 150)
    }
    
    private fun tryClickCancelButton() {
        try {
            val root = rootInActiveWindow ?: return
            val cancelClicked = findAndClickNode(root, listOf("cancel", "বাতিল", "취소", "no", "না"))
            root.recycle()
            if (cancelClicked) {
                Log.d(TAG, "✅ Clicked cancel button")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking cancel: ${e.message}")
        }
    }
    
    private fun findAndClickNode(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        if (keywords.any { text.contains(it) || desc.contains(it) }) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        
        for (i in 0 until node.childCount) {
            if (findAndClickNode(node.getChild(i), keywords)) {
                return true
            }
        if (isInGracePeriod()) return
        }
        return false
    }

    private fun checkLauncherUninstallDrag() {
        if (isInGracePeriod()) return
        try {
            val root = rootInActiveWindow ?: return
            val allText = extractAllTextFromNode(root).lowercase()

            // Launcher এ "uninstall"/"আনইনস্টল" text দেখা দিলে
            // AND "nafsshield" ও text এ থাকলে — block করো
            val hasUninstall = Constants.UNINSTALL_DRAG_TEXTS.any { allText.contains(it) }
            val hasNafs = allText.contains("nafsshield") ||
                          allText.contains("com.nafsshield") ||
                          allText.contains("naf") // short form

            if (hasUninstall) {
                // Uninstall zone visible হলেই সতর্ক থাকো
                // NafsShield drag করা হচ্ছে কিনা check করো
                checkIfNafsShieldDragging(root)
            }
            root.recycle()
        if (isInGracePeriod()) return
        } catch (e: Exception) {
            Log.e(TAG, "checkLauncherDrag error: ${e.message}")
        }
    }

    private fun checkIfNafsShieldDragging(root: AccessibilityNodeInfo) {
        if (isInGracePeriod()) return
        try {
            // Drag করা item এর text/description এ NafsShield আছে কিনা দেখো
            val dragging = findDraggedItem(root)
            if (dragging != null) {
                val text = (dragging.text?.toString() ?: "") +
                           (dragging.contentDescription?.toString() ?: "")
                if (text.lowercase().contains("nafs") ||
                    text.lowercase().contains("shield")) {
                    Log.d(TAG, "⚠️ NafsShield drag-to-uninstall detected!")
                    // সাথে সাথে home এ ফিরে যাও
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    mainHandler.postDelayed({
                        overlayManager.showPersistentBlockOverlay("⛔ NafsShield আনইনস্টল করা যাবে না!\n\nDevice Admin সক্রিয় আছে।", 3000)
                    }, 200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkDragging: ${e.message}")
        }
    }

    private fun findDraggedItem(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        // Dragged items সাধারণত selected/focused state এ থাকে
        if (node.isSelected || node.isFocused) {
            val t = node.text?.toString()?.lowercase() ?: ""
            val d = node.contentDescription?.toString()?.lowercase() ?: ""
            if (t.contains("nafs") || d.contains("nafs") ||
                t.contains("shield") || d.contains("shield")) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
        if (isInGracePeriod()) return
            val found = findDraggedItem(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun checkAndBlockStopButton() {
        if (isInGracePeriod()) return
        try {
            val root = rootInActiveWindow ?: return
            val allText = extractAllTextFromNode(root).lowercase()

            // "NafsShield" + "Stop"/"বন্ধ" একসাথে আছে কিনা দেখো
            val hasNafs = allText.contains("nafsshield") ||
                          allText.contains("com.nafsshield")
            val hasStop = STOP_BUTTON_TEXTS.any { allText.contains(it) }

            if (hasNafs && hasStop) {
                Log.d(TAG, "⚠️ Stop button detected for NafsShield!")
                // Stop button disable করো
                interceptStopButton(root)
                root.recycle()
                return
            }
            root.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "checkStopButton error: ${e.message}")
        }
    }

    private fun interceptStopButton(root: AccessibilityNodeInfo) {
        // সব clickable node খোঁজো যেগুলোর text "stop"/"বন্ধ"
        findStopNodes(root) { node ->
            // Stop button এ click করার আগে home এ যাও
            Log.d(TAG, "🔒 Intercepting Stop button")
            performGlobalAction(GLOBAL_ACTION_HOME)
            mainHandler.postDelayed({
                overlayManager.showPersistentBlockOverlay("⛔ NafsShield বন্ধ করা যাবে না!\n\nApp সুরক্ষিত আছে।", 4000)
            }, 100)
        }
    }

    private fun findStopNodes(
        node: AccessibilityNodeInfo?,
        onFound: (AccessibilityNodeInfo) -> Unit
    ) {
        if (node == null) return
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if ((node.isClickable || node.isEnabled) &&
            STOP_BUTTON_TEXTS.any { text.contains(it) || desc.contains(it) }) {
            onFound(node)
            return
        }
        for (i in 0 until node.childCount) {
            findStopNodes(node.getChild(i), onFound)
        }
    }

    private fun scheduleKeywordScan(pkg: String) {
        if (MasterService.activeKeywords.isEmpty()) return
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(Constants.OCR_DEBOUNCE_MS)
            val text  = extractAccessibilityText()
            val match = findKeyword(text)
            if (match != null) {
                mainHandler.post { blockAndGoHome(pkg, BlockReason.KEYWORD_FOUND, match) }
            }
        }
    }

    private fun extractAccessibilityText(): String {
        return try {
            val root = rootInActiveWindow ?: return ""
            val sb   = StringBuilder()
            collectText(root, sb)
            root.recycle()
            sb.toString()
        } catch (e: Exception) { "" }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb)
    }

    private fun findKeyword(text: String): String? {
        if (text.isEmpty() || MasterService.activeKeywords.isEmpty()) return null
        val lower = text.lowercase()
        return MasterService.activeKeywords.firstOrNull { lower.contains(it) }
    }

    private fun blockAndGoHome(pkg: String, reason: BlockReason, keyword: String? = null) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlayManager.showBlockOverlay(pkg, reason, keyword)
        scope.launch {
            try {
                repository.logBlock(BlockLog(
                    blockedPackage   = pkg,
                    reason           = reason,
                    triggeredKeyword = keyword
                ))
                MasterService.totalBlockedToday++
            } catch (e: Exception) {
                Log.e(TAG, "logBlock failed: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        instance = null
        scanJob?.cancel()
        settingsMonitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}