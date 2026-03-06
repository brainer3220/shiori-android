package dev.shiori.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class LinkListAdapter(
    private val onSelectionChanged: (LinkCardModel, Boolean) -> Unit = { _, _ -> },
    private val onReadToggleClicked: (LinkCardModel) -> Unit = {},
    private val onEditClicked: (LinkCardModel) -> Unit = {},
    private val onDeleteClicked: (LinkCardModel) -> Unit = {},
    private val onRestoreClicked: (LinkCardModel) -> Unit = {},
) : RecyclerView.Adapter<LinkListAdapter.LinkViewHolder>() {
    private val items = mutableListOf<LinkCardModel>()
    private var selectedIds: Set<String> = emptySet()
    private var itemActionsEnabled: Boolean = true
    private var selectionEnabled: Boolean = true
    private var destination: LinkBrowseDestination = LinkBrowseDestination.Inbox

    fun currentItems(): List<LinkCardModel> = items.toList()

    fun submitItems(
        newItems: List<LinkCardModel>,
        selectedIds: Set<String>,
        itemActionsEnabled: Boolean,
        selectionEnabled: Boolean,
        destination: LinkBrowseDestination,
    ) {
        items.clear()
        items.addAll(newItems)
        this.selectedIds = selectedIds
        this.itemActionsEnabled = itemActionsEnabled
        this.selectionEnabled = selectionEnabled
        this.destination = destination
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false)
        return LinkViewHolder(view)
    }

    override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            selected = selectedIds.contains(items[position].id),
            itemActionsEnabled = itemActionsEnabled,
            selectionEnabled = selectionEnabled,
            destination = destination,
            onSelectionChanged = onSelectionChanged,
            onReadToggleClicked = onReadToggleClicked,
            onEditClicked = onEditClicked,
            onDeleteClicked = onDeleteClicked,
            onRestoreClicked = onRestoreClicked,
        )
    }

    override fun getItemCount(): Int = items.size

    class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val selectionCheckbox: CheckBox = itemView.findViewById(R.id.link_select_checkbox)
        private val titleText: TextView = itemView.findViewById(R.id.link_title_text)
        private val domainText: TextView = itemView.findViewById(R.id.link_domain_text)
        private val summaryText: TextView = itemView.findViewById(R.id.link_summary_text)
        private val statusText: TextView = itemView.findViewById(R.id.link_status_text)
        private val timestampsText: TextView = itemView.findViewById(R.id.link_timestamps_text)
        private val toggleReadButton: MaterialButton = itemView.findViewById(R.id.link_toggle_read_button)
        private val editButton: MaterialButton = itemView.findViewById(R.id.link_edit_button)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.link_delete_button)

        fun bind(
            item: LinkCardModel,
            selected: Boolean,
            itemActionsEnabled: Boolean,
            selectionEnabled: Boolean,
            destination: LinkBrowseDestination,
            onSelectionChanged: (LinkCardModel, Boolean) -> Unit,
            onReadToggleClicked: (LinkCardModel) -> Unit,
            onEditClicked: (LinkCardModel) -> Unit,
            onDeleteClicked: (LinkCardModel) -> Unit,
            onRestoreClicked: (LinkCardModel) -> Unit,
        ) {
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

            selectionCheckbox.setOnCheckedChangeListener(null)
            selectionCheckbox.isChecked = selected
            selectionCheckbox.isEnabled = selectionEnabled
            selectionCheckbox.visibility = if (selectionEnabled) View.VISIBLE else View.GONE
            selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onSelectionChanged(item, isChecked)
            }

            toggleReadButton.text = if (destination == LinkBrowseDestination.Trash) {
                itemView.context.getString(R.string.action_restore_link)
            } else if (item.read == true) {
                itemView.context.getString(R.string.action_mark_unread)
            } else {
                itemView.context.getString(R.string.action_mark_read)
            }
            toggleReadButton.isEnabled = itemActionsEnabled
            toggleReadButton.visibility = View.VISIBLE
            toggleReadButton.setOnClickListener {
                if (destination == LinkBrowseDestination.Trash) {
                    onRestoreClicked(item)
                } else {
                    onReadToggleClicked(item)
                }
            }

            editButton.isEnabled = itemActionsEnabled
            editButton.visibility = if (destination == LinkBrowseDestination.Trash) View.GONE else View.VISIBLE
            editButton.setOnClickListener { onEditClicked(item) }

            deleteButton.isEnabled = itemActionsEnabled
            deleteButton.visibility = if (destination == LinkBrowseDestination.Trash) View.GONE else View.VISIBLE
            deleteButton.setOnClickListener { onDeleteClicked(item) }
        }
    }
}
