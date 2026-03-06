package dev.shiori.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LinkListAdapter : RecyclerView.Adapter<LinkListAdapter.LinkViewHolder>() {
    private val items = mutableListOf<LinkCardModel>()

    fun currentItems(): List<LinkCardModel> = items.toList()

    fun submitItems(newItems: List<LinkCardModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false)
        return LinkViewHolder(view)
    }

    override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.link_title_text)
        private val domainText: TextView = itemView.findViewById(R.id.link_domain_text)
        private val summaryText: TextView = itemView.findViewById(R.id.link_summary_text)
        private val statusText: TextView = itemView.findViewById(R.id.link_status_text)
        private val timestampsText: TextView = itemView.findViewById(R.id.link_timestamps_text)

        fun bind(item: LinkCardModel) {
            titleText.text = item.title
            domainText.text = item.domain

            summaryText.text = item.summary
            summaryText.visibility = if (item.summary == null) View.GONE else View.VISIBLE

            val statusParts = listOfNotNull(item.readState, item.status)
            statusText.text = statusParts.joinToString(separator = "  •  ")
            statusText.visibility = if (statusParts.isEmpty()) View.GONE else View.VISIBLE

            val timestampParts = listOfNotNull(item.createdAt, item.updatedAt)
            timestampsText.text = timestampParts.joinToString(separator = "  •  ")
            timestampsText.visibility = if (timestampParts.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}
