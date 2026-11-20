package com.example.cachecleaner

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppItem(val pkg: String, val label: String, val icon: Drawable, @Volatile var cache: Long = -1, var checked: Boolean = false)

@SuppressLint("NotifyDataSetChanged")
class AppAdapter(private val onChange: () -> Unit) : RecyclerView.Adapter<AppAdapter.VH>() {
    var items: List<AppItem> = emptyList()
        private set
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val pkg: TextView = v.findViewById(R.id.pkg)
        val cache: TextView = v.findViewById(R.id.cache)
        val check: CheckBox = v.findViewById(R.id.check)
    }
    fun submit(list: List<AppItem>) { items = list; notifyDataSetChanged() }
    fun refresh() { notifyDataSetChanged() }
    fun setAllShown(v: Boolean) { items.forEach { it.checked = v }; notifyDataSetChanged(); onChange() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, position: Int) {
        val a = items[position]
        h.icon.setImageDrawable(a.icon)
        h.name.text = a.label
        h.pkg.text = a.pkg
        h.cache.text = if (a.cache >= 0) Formatter.formatShortFileSize(h.itemView.context, a.cache) else ""
        h.check.isChecked = a.checked
        h.itemView.setOnClickListener { a.checked = !a.checked; h.check.isChecked = a.checked; onChange() }
    }
}
