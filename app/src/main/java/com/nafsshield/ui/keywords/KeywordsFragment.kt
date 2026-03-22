package com.nafsshield.ui.keywords

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.nafsshield.R
import com.nafsshield.util.PinManager
import com.nafsshield.viewmodel.MainViewModel

class KeywordsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: KeywordsAdapter
    private lateinit var pinManager: PinManager
    private lateinit var tvEmpty: TextView
    private lateinit var etKeyword: TextInputEditText
    private lateinit var switchOcr: SwitchMaterial

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_keywords, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinManager = PinManager(requireContext())
        tvEmpty    = view.findViewById(R.id.tvEmpty)
        etKeyword  = view.findViewById(R.id.etKeyword)
        switchOcr  = view.findViewById(R.id.switchOcr)

        switchOcr.isChecked = pinManager.isOcrEnabled
        switchOcr.setOnCheckedChangeListener { _, checked -> pinManager.isOcrEnabled = checked }

        adapter = KeywordsAdapter(
            onDelete = { kw -> verifyPinAndDelete(kw) },
            onToggle = { kw, active -> verifyPinAndToggle(kw, active) }
        )

        view.findViewById<RecyclerView>(R.id.recyclerKeywords).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@KeywordsFragment.adapter
        }

        view.findViewById<MaterialButton>(R.id.btnAddKeyword).setOnClickListener { addKeyword() }
        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addKeyword(); true } else false
        }

        viewModel.keywords.observe(viewLifecycleOwner) { kws ->
            adapter.submitList(kws)
            tvEmpty.visibility = if (kws.isEmpty()) View.VISIBLE else View.GONE
        }
    }
    
    private fun verifyPinAndDelete(keyword: Keyword) {
        // Show PIN verification dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val etPin = dialogView.findViewById<TextInputEditText>(R.id.etPinVerify)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage("\"${keyword.word}\" মুছতে PIN দিন")
            .setView(dialogView)
            .setPositiveButton("মুছুন") { _, _ ->
                val enteredPin = etPin.text?.toString() ?: ""
                if (pinManager.verifyPin(enteredPin)) {
                    viewModel.removeKeyword(keyword)
                    Snackbar.make(requireView(), "Keyword মুছে গেছে ✅", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "❌ ভুল PIN! Keyword মুছা হয়নি", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("বাতিল", null)
            .create()
        
        dialog.show()
        etPin.requestFocus()
    }
    
    private fun verifyPinAndToggle(keyword: Keyword, newState: Boolean) {
        // If turning ON, allow without PIN
        if (newState) {
            viewModel.toggleKeyword(keyword, true)
            return
        }
        
        // If turning OFF, require PIN
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin_verify, null)
        val etPin = dialogView.findViewById<TextInputEditText>(R.id.etPinVerify)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 PIN নিশ্চিত করুন")
            .setMessage("\"${keyword.word}\" বন্ধ করতে PIN দিন")
            .setView(dialogView)
            .setPositiveButton("বন্ধ করুন") { _, _ ->
                val enteredPin = etPin.text?.toString() ?: ""
                if (pinManager.verifyPin(enteredPin)) {
                    viewModel.toggleKeyword(keyword, false)
                    Snackbar.make(requireView(), "Keyword বন্ধ করা হয়েছে", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "❌ ভুল PIN!", Snackbar.LENGTH_LONG).show()
                    // Refresh list to reset switch
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("বাতিল") { _, _ ->
                // Reset switch back to ON
                adapter.notifyDataSetChanged()
            }
            .setOnCancelListener {
                // Reset switch back to ON if dialog cancelled
                adapter.notifyDataSetChanged()
            }
            .create()
        
        dialog.show()
        etPin.requestFocus()
    }

    private fun addKeyword() {
        val word = etKeyword.text?.toString()?.trim() ?: ""
        if (word.isEmpty()) {
            Snackbar.make(requireView(), getString(R.string.keyword_empty_error), Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.addKeyword(word)
        etKeyword.text?.clear()
        Snackbar.make(requireView(), "\"$word\" যোগ হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
    }
}
