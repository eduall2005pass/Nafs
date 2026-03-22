package com.nafsshield.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nafsshield.R
import com.nafsshield.data.model.Schedule

class ScheduleAdapter(
    private val onToggle: (Schedule, Boolean) -> Unit,
    private val onDelete: (Schedule) -> Unit
) : ListAdapter<Schedule, ScheduleAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvScheduleName)
        private val tvTime: TextView = view.findViewById(R.id.tvScheduleTime)
        private val switchActive: Switch = view.findViewById(R.id.switchScheduleActive)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteSchedule)

        fun bind(schedule: Schedule) {
            tvName.text = schedule.name
            tvTime.text = String.format(
                "%02d:%02d - %02d:%02d",
                schedule.startHour, schedule.startMinute,
                schedule.endHour, schedule.endMinute
            )
            
            switchActive.setOnCheckedChangeListener(null)
            switchActive.isChecked = schedule.isActive
            switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggle(schedule, isChecked)
            }
            
            btnDelete.setOnClickListener { onDelete(schedule) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(old: Schedule, new: Schedule) = old.id == new.id
            override fun areContentsTheSame(old: Schedule, new: Schedule) = old == new
        }
    }
}
