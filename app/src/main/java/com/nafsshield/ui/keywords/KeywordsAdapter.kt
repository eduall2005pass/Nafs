package com.nafsshield.ui.keywords

import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nafsshield.R
import com.nafsshield.data.model.Keyword

class KeywordsAdapter(
    private val onDelete: (Keyword) -> Unit,
    private val onToggle: (Keyword, Boolean) -> Unit
) : ListAdapter<Keyword, KeywordsAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_keyword_row, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWord: TextView         = itemView.findViewById(R.id.tvKeyword)
        private val switchActive: SwitchMaterial = itemView.findViewById(R.id.switchActive)
        private val btnDelete: ImageButton   = itemView.findViewById(R.id.btnDelete)

        fun bind(kw: Keyword) {
            tvWord.text = kw.word
            // listener null করে set করো — recycled view এর ghost callback এড়াতে
            switchActive.setOnCheckedChangeListener(null)
            switchActive.isChecked = kw.isActive
            switchActive.setOnCheckedChangeListener { _, checked -> onToggle(kw, checked) }
            btnDelete.setOnClickListener { onDelete(kw) }
        }
    }

    class Diff : DiffUtil.ItemCallback<Keyword>() {
        override fun areItemsTheSame(a: Keyword, b: Keyword) = a.id == b.id
        override fun areContentsTheSame(a: Keyword, b: Keyword) = a == b
    }
}
