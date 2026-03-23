package com.nafsshield.ui.pin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.nafsshield.R
import com.nafsshield.admin.NafsDeviceAdmin
import com.nafsshield.util.PinManager
import com.nafsshield.util.PinResult

class PinActivity : AppCompatActivity() {

    companion object {
        const val MODE                = "mode"
        const val MODE_SETUP          = "setup"
        const val MODE_VERIFY         = "verify"
        const val MODE_CHANGE         = "change"
        const val MODE_VERIFY_ADMIN   = "verify_admin"
        const val MODE_SETTINGS_ACCESS = "settings_access"

        @Volatile var isVerified = false
    }

    private lateinit var pinManager: PinManager
    private lateinit var tvTitle:    TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvMessage:  TextView
    private lateinit var dots:       List<ImageView>

    private val currentPin = StringBuilder()
    private var firstPin   = ""
    private var mode       = MODE_VERIFY
    private var setupStep  = 1
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        pinManager = PinManager(this)
        mode       = intent.getStringExtra(MODE) ?: MODE_VERIFY

        tvTitle    = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvMessage  = findViewById(R.id.tvMessage)
        dots       = listOf(
            findViewById(R.id.dot1), findViewById(R.id.dot2),
            findViewById(R.id.dot3), findViewById(R.id.dot4)
        )

        setupKeypad()
        updateHeader()
        // Biometric disabled
    }

    private fun setupKeypad() {
        val keys = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )
        keys.forEach { (id, digit) ->
            findViewById<View>(id).setOnClickListener { onDigit(digit) }
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { onBackspace() }
    }

    private fun onDigit(digit: String) {
        if (currentPin.length >= 4) return
        currentPin.append(digit)
        updateDots()
        if (currentPin.length == 4) onPinComplete()
    }

    private fun onBackspace() {
        if (currentPin.isNotEmpty()) {
            currentPin.deleteCharAt(currentPin.length - 1)
            updateDots()
        }
    }

    private fun onPinComplete() {
        val pin = currentPin.toString()
        when (mode) {
            MODE_SETUP           -> handleSetup(pin)
            MODE_VERIFY          -> handleVerify(pin)
            MODE_CHANGE          -> handleChange(pin)
            MODE_VERIFY_ADMIN    -> handleVerifyAdmin(pin)
            MODE_SETTINGS_ACCESS -> handleVerifySettings(pin)
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────
    private fun handleSetup(pin: String) {
        if (setupStep == 1) {
            firstPin = pin; setupStep = 2; resetInput()
            tvTitle.text    = "PIN নিশ্চিত করুন"
            tvSubtitle.text = "আবার একই PIN দিন"
        } else {
            if (pin == firstPin) {
                pinManager.setPin(pin)
                isVerified = true   // setup এর পর verified
                showSuccess("PIN সেট হয়েছে ✅")
                window.decorView.postDelayed({ goToMain() }, 600)
            } else {
                showError(getString(R.string.pin_mismatch))
                setupStep = 1; firstPin = ""; updateHeader()
            }
        }
    }

    // ── Verify ────────────────────────────────────────────────────────
    private fun handleVerify(pin: String) {
        when (val result = pinManager.verifyPin(pin)) {
            is PinResult.Correct -> {
                isVerified = true
                showSuccess("✅ সঠিক!")
                window.decorView.postDelayed({ goToMain() }, 400)
            }
            is PinResult.Wrong     -> showError(getString(R.string.pin_wrong, result.attemptsLeft))
            is PinResult.LockedOut -> startLockoutTimer(result.secondsRemaining)
            is PinResult.NoPinSet  -> {
                // নতুন Activity launch না করে এখানেই setup mode switch করো
                // তাহলে RESULT_OK সরাসরি MainActivity pinLauncher এ যাবে
                mode = MODE_SETUP
                setupStep = 1
                firstPin = ""
                resetInput()
                updateHeader()
            }
        }
    }

    // ── Change PIN ────────────────────────────────────────────────────
    private fun handleChange(pin: String) {
        when (setupStep) {
            1 -> when (pinManager.verifyPin(pin)) {
                is PinResult.Correct -> {
                    setupStep = 2; resetInput()
                    tvTitle.text    = "নতুন PIN দিন"
                    tvSubtitle.text = ""
                }
                is PinResult.Wrong     -> showError("পুরনো PIN ভুল")
                is PinResult.LockedOut -> showError("অ্যাকাউন্ট লক")
                else -> {}
            }
            2 -> { firstPin = pin; setupStep = 3; resetInput(); tvTitle.text = "নতুন PIN নিশ্চিত করুন" }
            3 -> if (pin == firstPin) {
                pinManager.setPin(pin)
                showSuccess("PIN পরিবর্তন হয়েছে ✅")
                window.decorView.postDelayed({ finish() }, 600)
            } else {
                showError(getString(R.string.pin_mismatch)); setupStep = 2
            }
        }
    }

    // ── Device Admin remove ───────────────────────────────────────────
    private fun handleVerifyAdmin(pin: String) {
        when (val r = pinManager.verifyPin(pin)) {
            is PinResult.Correct -> {
                // 4 সেকেন্ড grace period দাও
                com.nafsshield.service.NafsAccessibilityService.grantGracePeriod(4000L)
                startAdminRemoveCountdown()
            }
            is PinResult.Wrong -> {
                showError(getString(R.string.pin_wrong, r.attemptsLeft))
                showBlockedOverlay()
            }
            is PinResult.LockedOut -> startLockoutTimer(r.secondsRemaining)
            else -> {}
        }
    }

    private fun startAdminRemoveCountdown() {
        setKeypadEnabled(false)
        object : android.os.CountDownTimer(4000, 1000) {
            override fun onTick(ms: Long) {
                showSuccess("✅ PIN সঠিক — ${ms / 1000 + 1} সেকেন্ড পর বন্ধ হবে…")
            }
            override fun onFinish() {
                val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.removeActiveAdmin(ComponentName(this@PinActivity, NafsDeviceAdmin::class.java))
                finish()
            }
        }.start()
    }

    private fun showBlockedOverlay() {
        try {
            val overlay = com.nafsshield.overlay.OverlayManager(this)
            overlay.showPersistentBlockOverlay(
                "⛔ ভুল PIN!

অননুমোদিত প্রবেশের চেষ্টা সনাক্ত হয়েছে।",
                3000
            )
        } catch (_: Exception) {}
    }

    // ── Settings access ───────────────────────────────────────────────
    private fun handleVerifySettings(pin: String) {
        when (val r = pinManager.verifyPin(pin)) {
            is PinResult.Correct -> {
                // 4 সেকেন্ড grace period দাও
                com.nafsshield.service.NafsAccessibilityService.grantGracePeriod(4000L)
                showSuccess("✅ PIN সঠিক")
                window.decorView.postDelayed({ finish() }, 500)
            }
            is PinResult.Wrong -> {
                showError(getString(R.string.pin_wrong, r.attemptsLeft))
                showBlockedOverlay()
            }
            is PinResult.LockedOut -> startLockoutTimer(r.secondsRemaining)
            else -> {}
        }
    }

    // ── Biometric disabled ────────────────────────────────────────────
    private fun setupBiometric() { return  // Fingerprint disabled
        val btn = findViewById<View>(R.id.btnBiometric)
        val bm  = BiometricManager.from(this)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS) {
            btn.visibility = View.INVISIBLE; return
        }
        btn.setOnClickListener {
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isVerified = true; goToMain()
                    }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        showError("Biometric error: $msg")
                    }
                    override fun onAuthenticationFailed() = showError("Biometric ব্যর্থ")
                }
            ).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("NafsShield")
                    .setSubtitle("Fingerprint দিয়ে unlock করুন")
                    .setNegativeButtonText("PIN ব্যবহার করুন")
                    .build()
            )
        }
    }

    // ── Lockout timer ─────────────────────────────────────────────────
    private fun startLockoutTimer(seconds: Long) {
        lockoutTimer?.cancel()
        setKeypadEnabled(false)
        lockoutTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(ms: Long) = showError(getString(R.string.pin_locked, ms / 1000))
            override fun onFinish() { setKeypadEnabled(true); hideMessage(); resetInput() }
        }.start()
    }

    private fun setKeypadEnabled(enabled: Boolean) {
        listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
               R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnBack)
            .forEach { id -> findViewById<View>(id).isEnabled = enabled }
    }

    // ── UI helpers ────────────────────────────────────────────────────
    private fun updateDots() {
        dots.forEachIndexed { i, dot ->
            dot.setImageResource(
                if (i < currentPin.length) R.drawable.pin_dot_filled
                else R.drawable.pin_dot_empty
            )
        }
    }

    private fun resetInput() { currentPin.clear(); updateDots(); hideMessage() }

    private fun showError(msg: String) {
        tvMessage.text = msg
        tvMessage.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        tvMessage.visibility = View.VISIBLE

        // Shake
        tvMessage.animate().translationX(-12f).setDuration(40)
            .withEndAction {
                tvMessage.animate().translationX(12f).setDuration(40)
                    .withEndAction {
                        tvMessage.animate().translationX(0f).setDuration(40).start()
                    }.start()
            }.start()

        // Vibrate
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 80, 60, 80), -1)
            }
        } catch (_: Exception) {}

        resetInput()
    }

    private fun showSuccess(msg: String) {
        tvMessage.text = msg
        tvMessage.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        tvMessage.visibility = View.VISIBLE
    }

    private fun hideMessage() { tvMessage.visibility = View.INVISIBLE }

    private fun updateHeader() {
        when (mode) {
            MODE_SETUP           -> { tvTitle.text = getString(R.string.setup_pin_title); tvSubtitle.text = getString(R.string.setup_pin_subtitle) }
            MODE_VERIFY          -> { tvTitle.text = getString(R.string.verify_pin_title); tvSubtitle.text = getString(R.string.verify_pin_subtitle) }
            MODE_CHANGE          -> { tvTitle.text = "পুরনো PIN দিন"; tvSubtitle.text = "নিশ্চিত করতে আগের PIN দিন" }
            MODE_VERIFY_ADMIN    -> { tvTitle.text = "🔐 Device Admin"; tvSubtitle.text = "নিষ্ক্রিয় করতে PIN দিন" }
            MODE_SETTINGS_ACCESS -> { tvTitle.text = "🔐 সুরক্ষিত সেটিংস"; tvSubtitle.text = "পরিবর্তন করতে PIN দিন" }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────
    private fun goToMain() {
        // নতুন MainActivity launch করবো না — RESULT_OK দিয়ে ফিরে যাবো
        setResult(RESULT_OK)
        finish()
    }

    // ── Back press ────────────────────────────────────────────────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (mode) {
            MODE_VERIFY_ADMIN, MODE_SETTINGS_ACCESS -> {
                // Settings bypass রোধ — Home এ যাও
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                finish()
            }
            MODE_VERIFY, MODE_SETUP -> { /* blocked */ }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        lockoutTimer?.cancel()
        super.onDestroy()
    }
}
