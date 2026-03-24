package com.nafsshield.ui.apps

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.nafsshield.R
import com.nafsshield.data.model.BlockedApp
import com.nafsshield.util.PinManager
import com.nafsshield.util.PinResult
import com.nafsshield.viewmodel.AppInfo
import androidx.navigation.fragment.findNavController
import com.nafsshield.R as NavR
import com.nafsshield.viewmodel.MainViewModel

class AppsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: BlockedAppsAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var pinManager: PinManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_apps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        pinManager = PinManager(requireContext())
        tvEmpty = view.findViewById(R.id.tvEmpty)

        adapter = BlockedAppsAdapter { app -> verifyPinAndUnblock(app) }

        view.findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AppsFragment.adapter
        }

        viewModel.blockedApps.observe(viewLifecycleOwner) { apps ->
            adapter.submitList(apps)
            tvEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }

        view.findViewById<FloatingActionButton>(R.id.fabAddApp).setOnClickListener {
            showAppPicker()
        }

        view.findViewById<android.widget.Button>(R.id.btnSchedule)?.setOnClickListener {
            findNavController().navigate(R.id.scheduleFragment)
        }
    }
    
    private fun verifyPinAndUnblock(app: BlockedApp) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val etPin = dialogView.findViewById<TextInputEditText>(R.id.etPinVerify)
        
        AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage("\"${app.appName}\" unblock করতে PIN দিন")
            .setView(dialogView)
            .setPositiveButton("Unblock করুন") { _, _ ->
                val enteredPin = etPin.text?.toString() ?: ""
                if (pinManager.verifyPin(enteredPin) == PinResult.Correct) {
                    viewModel.unblockApp(app)
                    Snackbar.make(requireView(), "${app.appName} unblock হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "❌ ভুল PIN! Unblock হয়নি", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        etPin.requestFocus()
    }

    private fun showAppPicker() {
        val apps = viewModel.installedApps.value ?: emptyList()
        if (apps.isEmpty()) {
            viewModel.loadInstalledApps()
            viewModel.installedApps.observe(viewLifecycleOwner) { loaded ->
                if (loaded.isNotEmpty()) {
                    viewModel.installedApps.removeObservers(viewLifecycleOwner)
                    openAppPickerNow(loaded)
                }
            }
            return
        }
        openAppPickerNow(apps)
    }

    private fun openAppPickerNow(apps: List<AppInfo>) {

        // Create dialog with RecyclerView
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerAppPicker)
        val searchView = dialogView.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchApps)
        
        val adapter = AppPickerAdapter(requireContext().packageManager) { app ->
            currentDialog?.dismiss()
            confirmBlock(app)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.submitList(apps)
        
        // Search functionality
        var allApps = apps
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
        
        currentDialog = AlertDialog.Builder(requireContext())
            .setTitle("📱 কোন App block করবেন?")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
        
        currentDialog?.show()
    }
    
    private var currentDialog: AlertDialog? = null

    private fun confirmBlock(app: AppInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle("Block করবেন?")
            .setMessage("${app.appName} block করলে এটি আর খোলা যাবে না।")
            .setPositiveButton("Block করুন") { _, _ ->
                viewModel.blockApp(app.packageName, app.appName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
