package com.nafsshield.ui.apps

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nafsshield.R
import com.nafsshield.viewmodel.AppInfo

class AppPickerAdapter(
    private val packageManager: PackageManager,
    private val onAppClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppPickerAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = view.findViewById(R.id.tvAppName)
        private val tvPackage: TextView = view.findViewById(R.id.tvPackageName)

        fun bind(app: AppInfo) {
            tvName.text = app.appName
            tvPackage.text = app.packageName
            
            // Load app icon
            try {
                val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                val icon = packageManager.getApplicationIcon(appInfo)
                ivIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }
            
            itemView.setOnClickListener { onAppClick(app) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(old: AppInfo, new: AppInfo) = 
                old.packageName == new.packageName
            override fun areContentsTheSame(old: AppInfo, new: AppInfo) = old == new
        }
    }
}
