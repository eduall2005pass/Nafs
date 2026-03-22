package com.nafsshield.ui.apps

import android.content.pm.PackageManager
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nafsshield.R
import com.nafsshield.data.model.BlockedApp

class BlockedAppsAdapter(
    private val onUnblock: (BlockedApp) -> Unit
) : ListAdapter<BlockedApp, BlockedAppsAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_app_row, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView  = itemView.findViewById(R.id.tvAppName)
        private val tvPkg: TextView   = itemView.findViewById(R.id.tvPackageName)
        private val btnUnblock: View  = itemView.findViewById(R.id.btnUnblock)

        fun bind(app: BlockedApp) {
            tvName.text = app.appName
            tvPkg.text  = app.packageName
            try {
                ivIcon.setImageDrawable(
                    itemView.context.packageManager.getApplicationIcon(app.packageName)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            btnUnblock.setOnClickListener { onUnblock(app) }
        }
    }

    class Diff : DiffUtil.ItemCallback<BlockedApp>() {
        override fun areItemsTheSame(a: BlockedApp, b: BlockedApp) = a.packageName == b.packageName
        override fun areContentsTheSame(a: BlockedApp, b: BlockedApp) = a == b
    }
}
