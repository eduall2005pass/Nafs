package com.nafsshield.util

object Constants {

    // Notification
    const val CHANNEL_ID_GUARD   = "nafsshield_guard"
    const val CHANNEL_ID_ALERT   = "nafsshield_alert"
    const val NOTIF_ID_MASTER    = 1001
    const val NOTIF_ID_VPN       = 1002

    // SharedPreferences Keys
    const val PREF_FILE           = "nafsshield_prefs"
    const val KEY_PIN_HASH        = "pin_hash"
    const val KEY_PIN_SALT        = "pin_salt"
    const val KEY_MASTER_ENABLED  = "master_enabled"
    const val KEY_VPN_ENABLED     = "vpn_enabled"
    const val KEY_KEYWORD_OCR     = "keyword_ocr_enabled"
    const val KEY_DNS_PRIMARY     = "dns_primary"
    const val KEY_DNS_SECONDARY   = "dns_secondary"
    const val KEY_PIN_SETUP_DONE  = "pin_setup_done"
    const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    const val KEY_LOCKOUT_UNTIL   = "lockout_until"

    // PIN Security
    const val MAX_FAILED_ATTEMPTS  = 5
    const val LOCKOUT_DURATION_MS  = 30_000L

    // VPN / DNS
    const val VPN_ADDRESS          = "10.0.0.2"
    const val VPN_ROUTE            = "0.0.0.0"
    const val DEFAULT_DNS_PRIMARY  = "1.1.1.3"   // Cloudflare for Families (adult content block)
    const val DEFAULT_DNS_SECONDARY = "1.0.0.3"
    const val FALLBACK_DNS         = "8.8.8.8"
    const val DNS_PORT             = 53

    // Intent Actions
    const val ACTION_START_MASTER = "com.nafsshield.START_MASTER"
    const val ACTION_STOP_MASTER  = "com.nafsshield.STOP_MASTER"
    const val ACTION_START_VPN    = "com.nafsshield.START_VPN"
    const val ACTION_STOP_VPN     = "com.nafsshield.STOP_VPN"

    // OCR
    const val OCR_DEBOUNCE_MS     = 800L

    // Samsung A35 / OneUI: Recent task kill protection
    // stopWithTask=false already in manifest, this is just a reference constant
    const val SAMSUNG_BOOT_ACTION = "com.sec.android.app.launcher.QUICKBOOT_POWERON"

    // All known browser package names
    val ALL_KNOWN_BROWSERS = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.opera.browser",
        "com.microsoft.emmx",
        "com.UCMobile.intl",
        "com.sec.android.app.sbrowser",   // Samsung Internet
        "com.opera.mini.native",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.yandex.browser",
        "org.mozilla.focus",
        "com.ecosia.android"
    )

    // Built-in blocked DNS domains (ad/tracker)
    val BLOCKED_DOMAINS = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "pagead2.googlesyndication.com", "adservice.google.com",
        "an.facebook.com", "connect.facebook.net",
        "aax.amazon-adsystem.com", "amazon-adsystem.com",
        "ads.yahoo.com", "advertising.com", "adnxs.com",
        "taboola.com", "outbrain.com", "criteo.com",
        "mopub.com", "applovin.com", "ironsrc.com", "vungle.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "appsflyer.com", "adjust.com"
    )

    // Settings screen package names (block dangerous actions)
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.samsung.android.lool",       // Samsung Device Care
        "com.samsung.android.sm",
        "com.miui.securitycenter",
        "com.huawei.systemmanager",
        "com.oneplus.settings",
        "com.oppo.settings",
        "com.vivo.permissionmanager"
    )

    // Dangerous settings titles to block
    val DANGEROUS_SETTINGS_TITLES = listOf(
        "uninstall", "force stop", "ফোর্স স্টপ", "강제 종료",
        "app info", "অ্যাপ তথ্য", "disable", "নিষ্ক্রিয়",
        "nafsshield"
    )

    // WebView class names
    val WEBVIEW_CLASSES = setOf(
        "android.webkit.WebView",
        "com.amazon.webview.chromium.WebView",
        "org.chromium.android_webview.AwContents"
    )

    // Uninstall activity class names
    val UNINSTALL_ACTIVITIES = setOf(
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
    
    // App Info / App Details activity class names
    val APP_INFO_ACTIVITIES = setOf(
        "com.android.settings.applications.InstalledAppDetails",
        "com.android.settings.InstalledAppDetails",
        "com.samsung.android.settings.applications.InstalledAppDetails",
        "com.miui.appmanager.ApplicationsDetailsActivity",
        "AppInfoDashboardFragment",
        "InstalledAppDetailsTop"
    )
}
