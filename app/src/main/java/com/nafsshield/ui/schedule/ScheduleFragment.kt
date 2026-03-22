package com.nafsshield.ui.schedule

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.nafsshield.R
import com.nafsshield.data.model.Schedule
import com.nafsshield.viewmodel.MainViewModel
import java.util.*

class ScheduleFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ScheduleAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Header
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(20, 20, 20, 20)
        }
        
        val title = TextView(requireContext()).apply {
            text = "⏰ কাস্টম শিডিউল"
            textSize = 22f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        
        view.addView(header)
        
        // Empty state
        tvEmpty = TextView(requireContext()).apply {
            text = "কোনো শিডিউল নেই\nউপরে + চাপুন"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(40, 40, 40, 40)
            visibility = View.GONE
        }
        view.addView(tvEmpty)
        
        // RecyclerView
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(12, 12, 12, 12)
            clipToPadding = false
        }
        view.addView(recyclerView)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = ScheduleAdapter(
            onToggle = { schedule, isActive -> viewModel.toggleSchedule(schedule.id, isActive) },
            onDelete = { schedule ->
                AlertDialog.Builder(requireContext())
                    .setTitle("মুছে ফেলবেন?")
                    .setMessage("\"${schedule.name}\" শিডিউল মুছে যাবে।")
                    .setPositiveButton("হ্যাঁ") { _, _ -> viewModel.deleteSchedule(schedule) }
                    .setNegativeButton("না", null)
                    .show()
            }
        )
        
        view.findViewById<RecyclerView>(android.R.id.list)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ScheduleFragment.adapter
        }
        
        viewModel.schedules.observe(viewLifecycleOwner) { schedules ->
            adapter.submitList(schedules)
            tvEmpty.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
        }
        
        // FAB would be added in layout
    }
    
    private fun showScheduleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_schedule, null)
        
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etScheduleName)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tvStartTime)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tvEndTime)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btnPickStartTime)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btnPickEndTime)
        
        var startHour = 22
        var startMinute = 0
        var endHour = 6
        var endMinute = 0
        
        tvStartTime.text = String.format("%02d:%02d", startHour, startMinute)
        tvEndTime.text = String.format("%02d:%02d", endHour, endMinute)
        
        btnStartTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                startHour = h
                startMinute = m
                tvStartTime.text = String.format("%02d:%02d", h, m)
            }, startHour, startMinute, true).show()
        }
        
        btnEndTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                endHour = h
                endMinute = m
                tvEndTime.text = String.format("%02d:%02d", h, m)
            }, endHour, endMinute, true).show()
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("নতুন শিডিউল")
            .setView(dialogView)
            .setPositiveButton("সেভ করুন") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Snackbar.make(requireView(), "নাম দিন", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                viewModel.addSchedule(
                    Schedule(
                        name = name,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        daysOfWeek = "0,1,2,3,4,5,6" // All days
                    )
                )
                Snackbar.make(requireView(), "শিডিউল যোগ হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }
}
