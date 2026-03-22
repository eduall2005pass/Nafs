package com.nafsshield.ui.settings

import android.content.pm.PackageManager
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nafsshield.R
import com.nafsshield.data.model.AllowedBrowser

class BrowserAdapter(
    private val onRemove: (AllowedBrowser) -> Unit
) : ListAdapter<AllowedBrowser, BrowserAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_browser_row, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView     = itemView.findViewById(R.id.ivBrowserIcon)
        private val tvName: TextView      = itemView.findViewById(R.id.tvBrowserName)
        private val tvPkg: TextView       = itemView.findViewById(R.id.tvBrowserPkg)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemoveBrowser)

        fun bind(b: AllowedBrowser) {
            tvName.text = b.browserName
            tvPkg.text  = b.packageName
            
            // Load app icon
            try {
                ivIcon.setImageDrawable(
                    itemView.context.packageManager.getApplicationIcon(b.packageName)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            
            btnRemove.setOnClickListener { onRemove(b) }
        }
    }

    class Diff : DiffUtil.ItemCallback<AllowedBrowser>() {
        override fun areItemsTheSame(a: AllowedBrowser, b: AllowedBrowser) = a.packageName == b.packageName
        override fun areContentsTheSame(a: AllowedBrowser, b: AllowedBrowser) = a == b
    }
}
