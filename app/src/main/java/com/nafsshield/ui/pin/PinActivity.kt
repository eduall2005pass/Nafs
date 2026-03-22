package com.nafsshield.ui.pin

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.nafsshield.R
import com.nafsshield.ui.MainActivity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.nafsshield.admin.NafsDeviceAdmin
import com.nafsshield.util.PinManager
import com.nafsshield.util.PinResult

class PinActivity : AppCompatActivity() {

    companion object {
        const val MODE        = "mode"
        const val MODE_SETUP  = "setup"
        const val MODE_VERIFY = "verify"
        const val MODE_CHANGE = "change"
        const val MODE_VERIFY_ADMIN = "verify_admin"
        const val MODE_SETTINGS_ACCESS = "settings_access"

        @Volatile var isVerified = false
    }

    private lateinit var pinManager: PinManager
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvMessage: TextView
    private lateinit var dots: List<ImageView>

    private val currentPin   = StringBuilder()
    private var firstPin     = ""
    private var mode         = MODE_VERIFY
    private var setupStep    = 1
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
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )

        setupKeypad()
        updateHeader()
        if (mode == MODE_VERIFY) setupBiometric()
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
            MODE_SETUP  -> handleSetup(pin)
            MODE_VERIFY -> handleVerify(pin)
            MODE_CHANGE -> handleChange(pin)
        }
    }

    private fun handleSetup(pin: String) {
        if (setupStep == 1) {
            firstPin  = pin; setupStep = 2; resetInput()
            tvTitle.text = "PIN নিশ্চিত করুন"
            tvSubtitle.text = "আবার একই PIN দিন"
        } else {
            if (pin == firstPin) {
                pinManager.setPin(pin)
                showSuccess("PIN সেট হয়েছে ✅")
                goToMain()
            } else {
                showError(getString(R.string.pin_mismatch))
                setupStep = 1; firstPin = ""; updateHeader()
            }
        }
    }

    private fun handleVerify(pin: String) {
        when (val result = pinManager.verifyPin(pin)) {
            is PinResult.Correct   -> { isVerified = true; showSuccess("✅ সঠিক!"); goToMain() }
            is PinResult.Wrong     -> showError(getString(R.string.pin_wrong, result.attemptsLeft))
            is PinResult.LockedOut -> startLockoutTimer(result.secondsRemaining)
            is PinResult.NoPinSet  -> {
                startActivity(Intent(this, PinActivity::class.java).apply {
                    putExtra(MODE, MODE_SETUP)
                })
                finish()
            }
        }
    }

    private fun handleChange(pin: String) {
        when (setupStep) {
            1 -> when (pinManager.verifyPin(pin)) {
                is PinResult.Correct -> {
                    when (intent.getStringExtra(MODE)) {
                        MODE_VERIFY_ADMIN -> {
                            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                            dpm.removeActiveAdmin(ComponentName(this, NafsDeviceAdmin::class.java))
                            finish(); return
                        }
                        MODE_SETTINGS_ACCESS -> { finish(); return }
                    }
                    setupStep = 2; resetInput()
                    tvTitle.text = "নতুন PIN দিন"; tvSubtitle.text = ""
                }
                is PinResult.Wrong     -> showError("পুরনো PIN ভুল")
                is PinResult.LockedOut -> showError("অ্যাকাউন্ট লক")
                else -> {}
            }
            2 -> { firstPin = pin; setupStep = 3; resetInput(); tvTitle.text = "নতুন PIN নিশ্চিত করুন" }
            3 -> if (pin == firstPin) {
                pinManager.setPin(pin); showSuccess("PIN পরিবর্তন হয়েছে ✅"); finish()
            } else {
                showError(getString(R.string.pin_mismatch)); setupStep = 2
            }
        }
    }

    private fun setupBiometric() {
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

    private fun updateDots() {
        dots.forEachIndexed { i, dot ->
            dot.setImageResource(
                if (i < currentPin.length) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }

    private fun resetInput() { currentPin.clear(); updateDots(); hideMessage() }

    private fun showError(msg: String) {
        tvMessage.text = msg
        tvMessage.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        tvMessage.visibility = View.VISIBLE
        tvMessage.animate().translationX(-12f).setDuration(40)
            .withEndAction {
                tvMessage.animate().translationX(12f).setDuration(40)
                    .withEndAction {
                        tvMessage.animate().translationX(0f).setDuration(40).start()
                    }.start()
            }.start()
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
            MODE_SETUP  -> { tvTitle.text = getString(R.string.setup_pin_title); tvSubtitle.text = getString(R.string.setup_pin_subtitle) }
            MODE_VERIFY -> { tvTitle.text = getString(R.string.verify_pin_title); tvSubtitle.text = getString(R.string.verify_pin_subtitle) }
            MODE_CHANGE -> { tvTitle.text = "পুরনো PIN দিন"; tvSubtitle.text = "নিশ্চিত করতে আগের PIN দিন" }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // PIN screen থেকে back press block
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* blocked */ }

    override fun onDestroy() {
        lockoutTimer?.cancel()
        super.onDestroy()
    }
}
