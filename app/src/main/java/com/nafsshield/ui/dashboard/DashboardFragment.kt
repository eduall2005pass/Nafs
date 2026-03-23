package com.nafsshield.ui.dashboard

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nafsshield.R
import com.nafsshield.service.MasterService
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import com.nafsshield.service.NafsAccessibilityService
import com.nafsshield.service.NafsVpnService
import com.nafsshield.ui.MainActivity
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
import com.nafsshield.viewmodel.MainViewModel

class DashboardFragment : Fragment() {

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpn()
        else switchVpn.isChecked = false
    }

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var pinManager: PinManager

    private lateinit var switchGuard: SwitchMaterial
    private lateinit var switchVpn: SwitchMaterial
    private lateinit var tvGuardStatus: TextView
    private lateinit var tvTodayCount: TextView
    private lateinit var tvAppsCount: TextView
    private lateinit var tvKeywordsCount: TextView
    private lateinit var tvVpnStatus: TextView
    private lateinit var cardAccessibilityWarning: View
    private lateinit var cardOverlayWarning: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pinManager = PinManager(requireContext())

        switchGuard              = view.findViewById(R.id.switchGuard)
        switchVpn                = view.findViewById(R.id.switchVpn)
        tvGuardStatus            = view.findViewById(R.id.tvGuardStatus)
        tvTodayCount             = view.findViewById(R.id.tvTodayCount)
        tvAppsCount              = view.findViewById(R.id.tvAppsCount)
        tvKeywordsCount          = view.findViewById(R.id.tvKeywordsCount)
        tvVpnStatus              = view.findViewById(R.id.tvVpnStatus)
        cardAccessibilityWarning = view.findViewById(R.id.cardAccessibilityWarning)
        cardOverlayWarning       = view.findViewById(R.id.cardOverlayWarning)

        view.findViewById<View>(R.id.btnEnableAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        view.findViewById<View>(R.id.btnGrantOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }

        setupSwitches()
        observeData()

        view.findViewById<android.view.View>(R.id.cardReport).setOnClickListener {
            androidx.navigation.fragment.NavHostFragment
                .findNavController(this)
                .navigate(R.id.reportFragment)
        }
    }

    private fun setupSwitches() {
        // Guard switch — listener আগে remove করো তারপর set করো (infinite loop এড়াতে)
        switchGuard.setOnCheckedChangeListener(null)
        switchGuard.isChecked = MasterService.isRunning
        switchGuard.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                (requireActivity() as MainActivity).checkAndStartGuard()
            } else {
                requireContext().startService(
                    Intent(requireContext(), MasterService::class.java).apply {
                        action = Constants.ACTION_STOP_MASTER
                    }
                )
                pinManager.isMasterEnabled = false
            }
            updateGuardStatus(checked)
        }

        switchVpn.setOnCheckedChangeListener(null)
        switchVpn.isChecked = NafsVpnService.isRunning
        switchVpn.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val vpnIntent = VpnService.prepare(requireContext())
                if (vpnIntent != null) vpnLauncher.launch(vpnIntent)
                else startVpn()
            } else {
                stopVpn()
            }
            updateVpnStatus(checked)
        }
    }

    private fun startVpn() {
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), NafsVpnService::class.java).apply {
                action = Constants.ACTION_START_VPN
            }
        )
        pinManager.isVpnEnabled = true
    }

    private fun stopVpn() {
        requireContext().startService(
            Intent(requireContext(), NafsVpnService::class.java).apply {
                action = Constants.ACTION_STOP_VPN
            }
        )
        pinManager.isVpnEnabled = false
    }

    private fun observeData() {
        viewModel.blockedApps.observe(viewLifecycleOwner) { apps ->
            tvAppsCount.text = apps.size.toString()
        }
        viewModel.keywords.observe(viewLifecycleOwner) { kws ->
            tvKeywordsCount.text = kws.count { it.isActive }.toString()
        }
        viewModel.todayCount.observe(viewLifecycleOwner) { count ->
            tvTodayCount.text = getString(R.string.today_count, count)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        updateGuardStatus(MasterService.isRunning)
        updateVpnStatus(NafsVpnService.isRunning)
        viewModel.loadStats()
    }

    private fun checkPermissions() {
        cardAccessibilityWarning.visibility =
            if (NafsAccessibilityService.isRunning) View.GONE else View.VISIBLE
        cardOverlayWarning.visibility =
            if (Settings.canDrawOverlays(requireContext())) View.GONE else View.VISIBLE
    }

    private fun updateGuardStatus(on: Boolean) {
        tvGuardStatus.text = if (on) getString(R.string.guard_status_on)
                             else getString(R.string.guard_status_off)
        tvGuardStatus.setTextColor(
            ContextCompat.getColor(requireContext(),
                if (on) R.color.accent_green else R.color.accent_red)
        )
    }

    private fun updateVpnStatus(on: Boolean) {
        tvVpnStatus.text = if (on)
            "Active — DNS: ${NafsVpnService.currentDns} (${NafsVpnService.currentDnsState.name})"
        else "Inactive"
        tvVpnStatus.setTextColor(
            ContextCompat.getColor(requireContext(),
                if (on) R.color.accent_green else R.color.text_secondary)
        )
    }
}
