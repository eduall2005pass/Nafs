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
import com.nafsshield.data.model.Schedule
import com.nafsshield.viewmodel.MainViewModel

class ScheduleFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ScheduleAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

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
            text = "কোনো শিডিউল নেই\n+ বাটন চাপুন"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(40, 80, 40, 40)
            visibility = View.GONE
        }
        view.addView(tvEmpty)

        // RecyclerView
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setPadding(12, 12, 12, 12)
            clipToPadding = false
            layoutManager = LinearLayoutManager(requireContext())
        }
        view.addView(recyclerView)
        root.addView(view)

        // FAB
        val fab = FloatingActionButton(requireContext()).apply {
            setImageDrawable(
                android.graphics.drawable.GradientDrawable().apply { /* placeholder */ }
            )
            contentDescription = "শিডিউল যোগ করুন"
            val params = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, 32, 32)
            }
            layoutParams = params
            setOnClickListener { showScheduleDialog() }
        }
        // Use text button as FAB fallback
        val addBtn = Button(requireContext()).apply {
            text = "＋ নতুন শিডিউল"
            val params = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, 32, 48)
            }
            layoutParams = params
            setOnClickListener { showScheduleDialog() }
        }
        root.addView(addBtn)

        return root
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

        recyclerView.adapter = adapter

        viewModel.schedules.observe(viewLifecycleOwner) { schedules ->
            adapter.submitList(schedules)
            tvEmpty.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showScheduleDialog() {
        val ctx = requireContext()
        val dialogLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etName = EditText(ctx).apply {
            hint = "শিডিউলের নাম"
        }
        dialogLayout.addView(etName)

        var startHour = 22; var startMinute = 0
        var endHour = 6; var endMinute = 0

        val tvStart = TextView(ctx).apply { text = "শুরু: 22:00"; setPadding(0, 16, 0, 4) }
        val tvEnd = TextView(ctx).apply { text = "শেষ: 06:00"; setPadding(0, 16, 0, 4) }

        val btnStart = Button(ctx).apply {
            text = "শুরুর সময় বেছে নিন"
            setOnClickListener {
                TimePickerDialog(ctx, { _, h, m ->
                    startHour = h; startMinute = m
                    tvStart.text = "শুরু: %02d:%02d".format(h, m)
                }, startHour, startMinute, true).show()
            }
        }
        val btnEnd = Button(ctx).apply {
            text = "শেষের সময় বেছে নিন"
            setOnClickListener {
                TimePickerDialog(ctx, { _, h, m ->
                    endHour = h; endMinute = m
                    tvEnd.text = "শেষ: %02d:%02d".format(h, m)
                }, endHour, endMinute, true).show()
            }
        }

        // Day selector
        val dayNames = listOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি")
        val selectedDays = BooleanArray(7) { true }
        val dayRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 8)
        }
        dayNames.forEachIndexed { i, name ->
            val cb = CheckBox(ctx).apply {
                text = name
                isChecked = true
                setOnCheckedChangeListener { _, checked -> selectedDays[i] = checked }
            }
            dayRow.addView(cb)
        }

        dialogLayout.addView(tvStart)
        dialogLayout.addView(btnStart)
        dialogLayout.addView(tvEnd)
        dialogLayout.addView(btnEnd)
        dialogLayout.addView(TextView(ctx).apply { text = "দিন বেছে নিন:"; setPadding(0,16,0,4) })
        dialogLayout.addView(dayRow)

        AlertDialog.Builder(ctx)
            .setTitle("নতুন শিডিউল")
            .setView(dialogLayout)
            .setPositiveButton("সেভ করুন") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Snackbar.make(requireView(), "নাম দিন", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val days = selectedDays.indices.filter { selectedDays[it] }.joinToString(",")
                viewModel.addSchedule(
                    Schedule(
                        name = name,
                        startHour = startHour, startMinute = startMinute,
                        endHour = endHour, endMinute = endMinute,
                        daysOfWeek = days
                    )
                )
                Snackbar.make(requireView(), "শিডিউল যোগ হয়েছে ✅", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }
}
