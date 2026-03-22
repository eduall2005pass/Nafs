package com.nafsshield.util

/**
 * App এর ভেতর থেকে PIN verify করে uninstall করার অনুমতি দেওয়া হয়েছে কিনা।
 * Accessibility Service এই flag দেখে uninstall screen block করবে না।
 */
object UninstallGuard {

    @Volatile
    var isUserInitiated: Boolean = false
        private set

    private var grantedAt: Long = 0L
    private const val WINDOW_MS = 10_000L  // 10 সেকেন্ডের মধ্যে uninstall শুরু করতে হবে

    /** PIN সঠিক হলে এটা call করো — 10 সেকেন্ডের window দেওয়া হবে */
    fun grant() {
        isUserInitiated = true
        grantedAt = System.currentTimeMillis()
    }

    /** Accessibility Service call করবে — window শেষ হয়ে গেলে false */
    fun isAllowed(): Boolean {
        if (!isUserInitiated) return false
        if (System.currentTimeMillis() - grantedAt > WINDOW_MS) {
            revoke()
            return false
        }
        return true
    }

    /** Uninstall হয়ে গেলে বা cancel হলে revoke করো */
    fun revoke() {
        isUserInitiated = false
        grantedAt = 0L
    }
}
