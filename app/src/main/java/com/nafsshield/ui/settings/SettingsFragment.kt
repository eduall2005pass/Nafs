package com.nafsshield.ui.settings

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.nafsshield.R
import com.nafsshield.admin.NafsDeviceAdmin
import com.nafsshield.data.model.AllowedBrowser
import com.nafsshield.service.NafsVpnService
import com.nafsshield.ui.MainActivity
import com.nafsshield.ui.pin.PinActivity
import com.nafsshield.util.Constants
import com.nafsshield.util.PinManager
import com.nafsshield.viewmodel.MainViewModel
import android.content.Intent

class SettingsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var pinManager: PinManager
    private lateinit var browserAdapter: BrowserAdapter

    private lateinit var etPrimaryDns: TextInputEditText
    private lateinit var etSecondaryDns: TextInputEditText
    private lateinit var tvCurrentDns: TextView
    private lateinit var tvAdminStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pinManager = PinManager(requireContext())

        etPrimaryDns   = view.findViewById(R.id.etPrimaryDns)
        etSecondaryDns = view.findViewById(R.id.etSecondaryDns)
        tvCurrentDns   = view.findViewById(R.id.tvCurrentDns)
        tvAdminStatus  = view.findViewById(R.id.tvAdminStatus)

        setupDns(view)
        setupBrowsers(view)
        setupSecurity(view)
    }

    private fun setupDns(view: View) {
        etPrimaryDns.setText(pinManager.primaryDns)
        etSecondaryDns.setText(pinManager.secondaryDns)
        updateDnsStatus()

        view.findViewById<MaterialButton>(R.id.btnSaveDns).setOnClickListener {
            val primary   = etPrimaryDns.text.toString().trim()
            val secondary = etSecondaryDns.text.toString().trim()

            if (!isValidIp(primary)) {
                Snackbar.make(requireView(), "Primary DNS ঠিক নেই", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (secondary.isNotEmpty() && !isValidIp(secondary)) {
                Snackbar.make(requireView(), "Secondary DNS ঠিক নেই", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pinManager.primaryDns = primary
            if (secondary.isNotEmpty()) pinManager.secondaryDns = secondary
            Snackbar.make(requireView(), "DNS save হয়েছে। VPN restart করুন।", Snackbar.LENGTH_LONG).show()
            updateDnsStatus()
        }
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    private fun updateDnsStatus() {
        tvCurrentDns.text = if (NafsVpnService.isRunning)
            "${NafsVpnService.currentDns} (${NafsVpnService.currentDnsState.name})"
        else "${pinManager.primaryDns} (VPN বন্ধ)"
    }

    private fun setupBrowsers(view: View) {
        browserAdapter = BrowserAdapter { browser ->
            verifyPinAndRemoveBrowser(browser)
        }

        view.findViewById<RecyclerView>(R.id.recyclerBrowsers).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = browserAdapter
        }

        viewModel.allowedBrowsers.observe(viewLifecycleOwner) { browsers ->
            browserAdapter.submitList(browsers)
        }

        view.findViewById<MaterialButton>(R.id.btnAddBrowser).setOnClickListener {
            showBrowserPicker()
        }
    }
    
    private fun verifyPinAndRemoveBrowser(browser: AllowedBrowser) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val etPin = dialogView.findViewById<TextInputEditText>(R.id.etPinVerify)
        
        AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage("\"${browser.browserName}\" সরিয়ে দিতে PIN দিন")
            .setView(dialogView)
            .setPositiveButton("সরিয়ে দিন") { _, _ ->
                val enteredPin = etPin.text?.toString() ?: ""
                if (pinManager.verifyPin(enteredPin)) {
                    viewModel.removeBrowser(browser)
                    Snackbar.make(requireView(), "${browser.browserName} সরিয়ে দেওয়া হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "❌ ভুল PIN!", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
        
        etPin.requestFocus()
    }

    private fun showBrowserPicker() {
        // Get all installed apps (not just known browsers)
        val pm = requireContext().packageManager
        val myPackage = requireContext().packageName
        val alreadyAllowed = viewModel.allowedBrowsers.value?.map { it.packageName }?.toSet() ?: emptySet()
        
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { 
                // Only non-system apps
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                // Not NafsShield itself
                it.packageName != myPackage &&
                // Not already in allowed list
                it.packageName !in alreadyAllowed
            }
            .map { 
                com.nafsshield.viewmodel.AppInfo(
                    it.packageName, 
                    pm.getApplicationLabel(it).toString()
                ) 
            }
            .sortedBy { it.appName }
        
        if (installed.isEmpty()) {
            Snackbar.make(requireView(), "কোনো app পাওয়া যায়নি", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        // Create dialog with RecyclerView and search
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerAppPicker)
        val searchView = dialogView.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchApps)
        
        val adapter = com.nafsshield.ui.apps.AppPickerAdapter(pm) { app ->
            currentBrowserDialog?.dismiss()
            viewModel.allowBrowser(app.packageName, app.appName)
            Snackbar.make(requireView(), "${app.appName} যোগ হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.submitList(installed)
        
        // Search functionality
        var allApps = installed
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = if (newText.isNullOrBlank()) {
                    allApps
                } else {
                    allApps.filter { 
                        it.appName.contains(newText, ignoreCase = true) ||
                        it.packageName.contains(newText, ignoreCase = true)
                    }
                }
                adapter.submitList(filtered)
                return true
            }
        })
        
        currentBrowserDialog = AlertDialog.Builder(requireContext())
            .setTitle("🌐 Browser হিসেবে যোগ করুন")
            .setView(dialogView)
            .setNegativeButton("বাতিল") { dialog, _ -> dialog.dismiss() }
            .create()
        
        currentBrowserDialog?.show()
    }
    
    private var currentBrowserDialog: AlertDialog? = null

    private fun setupSecurity(view: View) {
        view.findViewById<View>(R.id.rowChangePin).setOnClickListener {
            startActivity(Intent(requireContext(), PinActivity::class.java).apply {
                putExtra(PinActivity.MODE, PinActivity.MODE_CHANGE)
            })
        }
        view.findViewById<View>(R.id.rowDeviceAdmin).setOnClickListener {
            (requireActivity() as MainActivity).activateDeviceAdmin()
        }
        updateAdminStatus()
    }

    private fun updateAdminStatus() {
        val dpm    = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin  = ComponentName(requireContext(), NafsDeviceAdmin::class.java)
        val active = dpm.isAdminActive(admin)
        tvAdminStatus.text = if (active) "সক্রিয় ✅" else "নিষ্ক্রিয় — tap করে activate করুন"
        tvAdminStatus.setTextColor(
            requireContext().getColor(if (active) R.color.accent_green else R.color.accent_red)
        )
    }

    override fun onResume() {
        super.onResume()
        updateAdminStatus()
        updateDnsStatus()
    }
}
