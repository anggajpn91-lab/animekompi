package com.animekompi.app

import android.app.DownloadManager
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DlItem(val id: Long, val title: String, val status: Int, val done: Long, val total: Long)

class DownloadAdapter(
    private val onOpen: (DlItem) -> Unit,
    private val onDelete: (DlItem) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    var items: List<DlItem> = emptyList()
        set(v) { if (v != field) { field = v; notifyDataSetChanged() } }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val pb: ProgressBar = v.findViewById(R.id.pb)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val open: Button = v.findViewById(R.id.btnOpen)
        val del: Button = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_download, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val d = items[i]
        val ctx = h.itemView.context
        h.title.text = d.title
        val pct = if (d.total > 0) (d.done * 100 / d.total).toInt() else 0
        h.pb.progress = pct
        val size = "${Formatter.formatShortFileSize(ctx, d.done)} / " +
            if (d.total > 0) Formatter.formatShortFileSize(ctx, d.total) else "?"
        h.status.text = when (d.status) {
            DownloadManager.STATUS_RUNNING -> "Mengunduh $pct% • $size"
            DownloadManager.STATUS_PAUSED -> "Dijeda • $size"
            DownloadManager.STATUS_PENDING -> "Menunggu…"
            DownloadManager.STATUS_SUCCESSFUL -> "Selesai • $size"
            else -> "Gagal"
        }
        val ok = d.status == DownloadManager.STATUS_SUCCESSFUL
        h.open.isEnabled = ok
        h.open.setOnClickListener { onOpen(d) }
        h.del.text = if (ok) "Hapus" else "Batal"
        h.del.setOnClickListener { onDelete(d) }
    }
}
