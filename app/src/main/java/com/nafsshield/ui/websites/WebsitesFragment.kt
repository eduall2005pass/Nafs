package com.nafsshield.ui.websites

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.nafsshield.R
import com.nafsshield.util.PinManager
import com.nafsshield.util.PinResult

class WebsitesFragment : Fragment() {

    private lateinit var pinManager: PinManager
    private lateinit var etWebsite: TextInputEditText
    private lateinit var recycler: RecyclerView
    private lateinit var tvNoWebsites: TextView
    private lateinit var adapter: WebsiteAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_websites, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        pinManager   = PinManager(requireContext())
        etWebsite    = v.findViewById(R.id.etWebsite)
        recycler     = v.findViewById(R.id.recyclerWebsites)
        tvNoWebsites = v.findViewById(R.id.tvNoWebsites)

        adapter = WebsiteAdapter { domain -> verifyPinThenRemove(domain) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        v.findViewById<MaterialButton>(R.id.btnAddWebsite).setOnClickListener { addWebsite() }
        etWebsite.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { addWebsite(); true } else false
        }
        refreshList()
    }

    override fun onResume() { super.onResume(); refreshList() }

    private fun refreshList() {
        val sites = pinManager.getBlockedWebsites().toList().sorted()
        adapter.submitList(sites)
        tvNoWebsites.visibility = if (sites.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addWebsite() {
        val raw = etWebsite.text?.toString()?.trim() ?: ""
        if (raw.isBlank()) {
            Snackbar.make(requireView(), "URL বা domain দিন", Snackbar.LENGTH_SHORT).show()
            return
        }
        val dv = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val et = dv.findViewById<TextInputEditText>(R.id.etPinVerify)
        val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage(raw + " block করতে PIN দিন")
            .setView(dv).setPositiveButton("Block করুন", null)
            .setNegativeButton("বাতিল", null).create()
        dlg.show(); et.requestFocus()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when (val r = pinManager.verifyPin(et.text?.toString() ?: "")) {
                is PinResult.Correct -> {
                    pinManager.addBlockedWebsite(raw)
                    etWebsite.text?.clear()
                    dlg.dismiss(); refreshList()
                    Snackbar.make(requireView(), raw + " block হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
                }
                is PinResult.Wrong -> { et.text?.clear(); et.error = "❌ ভুল PIN! বাকি: " + r.attemptsLeft }
                is PinResult.LockedOut -> {
                    dlg.dismiss()
                    Snackbar.make(requireView(), "🔒 " + (r.secondsRemaining/60) + " মিনিট লক", Snackbar.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun verifyPinThenRemove(domain: String) {
        val dv = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val et = dv.findViewById<TextInputEditText>(R.id.etPinVerify)
        val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage(domain + " unblock করতে PIN দিন")
            .setView(dv).setPositiveButton("Unblock", null)
            .setNegativeButton("বাতিল", null).create()
        dlg.show(); et.requestFocus()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when (val r = pinManager.verifyPin(et.text?.toString() ?: "")) {
                is PinResult.Correct -> {
                    pinManager.removeBlockedWebsite(domain)
                    dlg.dismiss(); refreshList()
                    Snackbar.make(requireView(), domain + " unblock হয়েছে", Snackbar.LENGTH_SHORT).show()
                }
                is PinResult.Wrong -> { et.text?.clear(); et.error = "❌ ভুল PIN! বাকি: " + r.attemptsLeft }
                is PinResult.LockedOut -> {
                    dlg.dismiss()
                    Snackbar.make(requireView(), "🔒 " + (r.secondsRemaining/60) + " মিনিট লক", Snackbar.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}

class WebsiteAdapter(private val onRemove: (String) -> Unit) :
    RecyclerView.Adapter<WebsiteAdapter.VH>() {
    private val items = mutableListOf<String>()
    fun submitList(list: List<String>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val domain: TextView = v.findViewById(R.id.tvDomain)
        val remove: TextView = v.findViewById(R.id.tvRemoveSite)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_website_row, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, i: Int) {
        h.domain.text = "🌐 " + items[i]
        h.remove.setOnClickListener { onRemove(items[i]) }
    }
}
