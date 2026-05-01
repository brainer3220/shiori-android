package dev.shiori.android

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs

class LinkListAdapter(
    private val onOpenClicked: (LinkCardModel) -> Unit = {},
    private val onSelectionChanged: (LinkCardModel, Boolean) -> Unit = { _, _ -> },
    private val onReadToggleClicked: (LinkCardModel) -> Unit = {},
    private val onEditClicked: (LinkCardModel) -> Unit = {},
    private val onDeleteClicked: (LinkCardModel) -> Unit = {},
    private val onRestoreClicked: (LinkCardModel) -> Unit = {},
) : RecyclerView.Adapter<LinkListAdapter.LinkViewHolder>() {
    private val items = mutableListOf<LinkCardModel>()
    private var selectedIds: Set<String> = emptySet()
    private var hasSelection: Boolean = false
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
        this.hasSelection = selectedIds.isNotEmpty()
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
            hasSelection = hasSelection,
            itemActionsEnabled = itemActionsEnabled,
            selectionEnabled = selectionEnabled,
            destination = destination,
            onOpenClicked = onOpenClicked,
            onSelectionChanged = onSelectionChanged,
            onReadToggleClicked = onReadToggleClicked,
            onEditClicked = onEditClicked,
            onDeleteClicked = onDeleteClicked,
            onRestoreClicked = onRestoreClicked,
        )
    }

    override fun getItemCount(): Int = items.size

    class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val selectionCheckbox: CheckBox = itemView.findViewById(R.id.link_select_checkbox)
        private val iconContainer: MaterialCardView = itemView.findViewById(R.id.link_icon_container)
        private val faviconImage: ImageView = itemView.findViewById(R.id.link_favicon_image)
        private val iconText: TextView = itemView.findViewById(R.id.link_icon_text)
        private val titleText: TextView = itemView.findViewById(R.id.link_title_text)
        private val domainText: TextView = itemView.findViewById(R.id.link_domain_text)
        private val summaryText: TextView = itemView.findViewById(R.id.link_summary_text)
        private val statusText: TextView = itemView.findViewById(R.id.link_status_text)
        private val timestampsText: TextView = itemView.findViewById(R.id.link_timestamps_text)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.link_overflow_button)
        private val toggleReadButton: MaterialButton = itemView.findViewById(R.id.link_toggle_read_button)
        private val editButton: MaterialButton = itemView.findViewById(R.id.link_edit_button)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.link_delete_button)

        fun bind(
            item: LinkCardModel,
            selected: Boolean,
            hasSelection: Boolean,
            itemActionsEnabled: Boolean,
            selectionEnabled: Boolean,
            destination: LinkBrowseDestination,
            onOpenClicked: (LinkCardModel) -> Unit,
            onSelectionChanged: (LinkCardModel, Boolean) -> Unit,
            onReadToggleClicked: (LinkCardModel) -> Unit,
            onEditClicked: (LinkCardModel) -> Unit,
            onDeleteClicked: (LinkCardModel) -> Unit,
            onRestoreClicked: (LinkCardModel) -> Unit,
        ) {
            titleText.text = item.title
            val displayDomain = item.domain.removePrefix("www.")
            domainText.text = displayDomain
            domainText.visibility = View.GONE

            val summary = item.summary?.takeIf { it.isNotBlank() }
            summaryText.text = summary
            summaryText.visibility = View.GONE

            val statusLine = buildStatusLine(item, destination)
            statusText.text = statusLine
            statusText.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    when {
                        destination == LinkBrowseDestination.Trash -> R.color.shiori_text_muted
                        item.read == true -> R.color.shiori_text_muted
                        else -> R.color.shiori_accent_dark
                    },
                ),
            )
            statusText.visibility = View.GONE

            val timestampLine = item.updatedAt ?: item.createdAt
            timestampsText.text = timestampLine
            timestampsText.visibility = View.GONE

            bindIcon(item)
            bindSelectionState(selected)
            bindAccessibility(item, selected, statusLine, timestampLine)

            selectionCheckbox.setOnCheckedChangeListener(null)
            selectionCheckbox.isChecked = selected
            selectionCheckbox.isEnabled = selectionEnabled
            selectionCheckbox.visibility = if (selectionEnabled && hasSelection) View.VISIBLE else View.GONE
            selectionCheckbox.contentDescription = itemView.context.getString(
                if (selected) R.string.action_deselect_link else R.string.action_select_link,
            )
            selectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onSelectionChanged(item, isChecked)
            }

            overflowButton.visibility = if (selectionEnabled && hasSelection) View.GONE else View.VISIBLE
            overflowButton.isEnabled = itemActionsEnabled
            overflowButton.contentDescription = itemView.context.getString(R.string.label_link_actions, item.title)
            overflowButton.setOnClickListener {
                showOverflowMenu(
                    item = item,
                    selected = selected,
                    selectionEnabled = selectionEnabled,
                    destination = destination,
                    onOpenClicked = onOpenClicked,
                    onSelectionChanged = onSelectionChanged,
                    onReadToggleClicked = onReadToggleClicked,
                    onEditClicked = onEditClicked,
                    onDeleteClicked = onDeleteClicked,
                    onRestoreClicked = onRestoreClicked,
                )
            }

            cardView.isEnabled = itemActionsEnabled
            itemView.isClickable = itemActionsEnabled
            itemView.isFocusable = itemActionsEnabled
            itemView.alpha = if (itemActionsEnabled) 1f else 0.66f
            itemView.setOnLongClickListener {
                if (!selectionEnabled || !itemActionsEnabled) {
                    return@setOnLongClickListener false
                }
                onSelectionChanged(item, !selected)
                true
            }
            itemView.setOnClickListener {
                if (!itemActionsEnabled) {
                    return@setOnClickListener
                }
                if (selectionEnabled && hasSelection) {
                    onSelectionChanged(item, !selected)
                } else {
                    onOpenClicked(item)
                }
            }

            toggleReadButton.text = if (destination == LinkBrowseDestination.Trash) {
                itemView.context.getString(R.string.action_restore_link)
            } else if (item.read == true) {
                itemView.context.getString(R.string.action_mark_unread)
            } else {
                itemView.context.getString(R.string.action_mark_read)
            }
            toggleReadButton.isEnabled = itemActionsEnabled
            toggleReadButton.visibility = View.GONE
            toggleReadButton.setOnClickListener {
                if (destination == LinkBrowseDestination.Trash) {
                    onRestoreClicked(item)
                } else {
                    onReadToggleClicked(item)
                }
            }

            editButton.isEnabled = itemActionsEnabled
            editButton.visibility = View.GONE
            editButton.setOnClickListener { onEditClicked(item) }

            deleteButton.isEnabled = itemActionsEnabled
            deleteButton.visibility = View.GONE
            deleteButton.setOnClickListener { onDeleteClicked(item) }
        }

        private fun bindSelectionState(selected: Boolean) {
            val backgroundColor = if (selected) {
                ContextCompat.getColor(itemView.context, R.color.shiori_selection)
            } else {
                Color.TRANSPARENT
            }
            cardView.setCardBackgroundColor(backgroundColor)
            cardView.strokeWidth = if (selected) itemView.dp(1) else 0
            cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.shiori_line)
        }

        private fun bindIcon(item: LinkCardModel) {
            val iconStyle = item.iconStyle(itemView)
            faviconImage.load(null)
            faviconImage.setImageDrawable(null)

            if (item.faviconUrl != null) {
                bindFallbackIcon(iconStyle)
                faviconImage.load(item.faviconUrl) {
                    crossfade(120)
                    listener(
                        onSuccess = { _, _ ->
                            bindLoadedFavicon()
                        },
                        onError = { _, _ ->
                            bindFallbackIcon(iconStyle)
                        },
                    )
                }
                return
            }

            bindFallbackIcon(iconStyle)
        }

        private fun bindLoadedFavicon() {
            iconContainer.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.shiori_white))
            iconContainer.strokeWidth = itemView.dp(1)
            iconContainer.strokeColor = ContextCompat.getColor(itemView.context, R.color.shiori_line)
            iconText.visibility = View.GONE
            faviconImage.visibility = View.VISIBLE
        }

        private fun bindFallbackIcon(iconStyle: IconStyle) {
            iconText.text = iconStyle.label
            iconText.setTextColor(iconStyle.textColor)
            iconText.visibility = View.VISIBLE
            faviconImage.visibility = View.GONE
            iconContainer.setCardBackgroundColor(iconStyle.backgroundColor)
            iconContainer.strokeWidth = if (iconStyle.strokeColor != null) itemView.dp(1) else 0
            iconContainer.strokeColor = iconStyle.strokeColor
                ?: ContextCompat.getColor(itemView.context, android.R.color.transparent)
        }

        private fun bindAccessibility(
            item: LinkCardModel,
            selected: Boolean,
            statusLine: String,
            timestampLine: String?,
        ) {
            val contentParts = buildList {
                add(item.title)
                if (item.domain.isNotBlank()) add(item.domain)
                item.summary?.takeIf { it.isNotBlank() }?.let(::add)
                if (statusLine.isNotBlank()) add(statusLine)
                if (statusLine.isBlank()) {
                    timestampLine?.takeIf { it.isNotBlank() }?.let(::add)
                }
                if (selected) add(itemView.context.getString(R.string.state_selected))
            }
            itemView.contentDescription = contentParts.joinToString(separator = ", ")
        }

        private fun buildStatusLine(item: LinkCardModel, destination: LinkBrowseDestination): String {
            val parts = mutableListOf<String>()
            if (destination == LinkBrowseDestination.Trash) {
                item.status?.let(parts::add)
            } else {
                item.readState?.let(parts::add)
            }
            (item.updatedAt ?: item.createdAt)?.let(parts::add)
            return parts.joinToString(separator = "  •  ")
        }

        private fun showOverflowMenu(
            item: LinkCardModel,
            selected: Boolean,
            selectionEnabled: Boolean,
            destination: LinkBrowseDestination,
            onOpenClicked: (LinkCardModel) -> Unit,
            onSelectionChanged: (LinkCardModel, Boolean) -> Unit,
            onReadToggleClicked: (LinkCardModel) -> Unit,
            onEditClicked: (LinkCardModel) -> Unit,
            onDeleteClicked: (LinkCardModel) -> Unit,
            onRestoreClicked: (LinkCardModel) -> Unit,
        ) {
            PopupMenu(itemView.context, overflowButton).apply {
                menu.add(0, MENU_OPEN, 0, R.string.action_open_link)

                if (selectionEnabled) {
                    menu.add(
                        0,
                        MENU_SELECT,
                        1,
                        if (selected) R.string.action_deselect_link else R.string.action_select_link,
                    )
                }

                if (destination == LinkBrowseDestination.Trash) {
                    menu.add(0, MENU_RESTORE, 2, R.string.action_restore_link)
                } else {
                    menu.add(
                        0,
                        MENU_TOGGLE_READ,
                        2,
                        if (item.read == true) R.string.action_mark_unread else R.string.action_mark_read,
                    )
                    menu.add(0, MENU_EDIT, 3, R.string.action_edit_link)
                    menu.add(0, MENU_DELETE, 4, R.string.action_move_to_trash)
                }

                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        MENU_OPEN -> {
                            onOpenClicked(item)
                            true
                        }

                        MENU_SELECT -> {
                            onSelectionChanged(item, !selected)
                            true
                        }

                        MENU_TOGGLE_READ -> {
                            onReadToggleClicked(item)
                            true
                        }

                        MENU_EDIT -> {
                            onEditClicked(item)
                            true
                        }

                        MENU_DELETE -> {
                            onDeleteClicked(item)
                            true
                        }

                        MENU_RESTORE -> {
                            onRestoreClicked(item)
                            true
                        }

                        else -> false
                    }
                }
                show()
            }
        }

        private data class IconStyle(
            val label: String,
            val backgroundColor: Int,
            val textColor: Int,
            val strokeColor: Int? = null,
        )

        private fun LinkCardModel.iconStyle(view: View): IconStyle {
            val context = view.context
            val domainValue = domain.lowercase()
            return when {
                domainValue.contains("youtube") || url.contains("youtu", ignoreCase = true) -> IconStyle(
                    label = "YT",
                    backgroundColor = ContextCompat.getColor(context, R.color.shiori_youtube),
                    textColor = ContextCompat.getColor(context, R.color.shiori_white),
                )

                domainValue.contains("openai") || title.contains("chatgpt", ignoreCase = true) -> IconStyle(
                    label = "AI",
                    backgroundColor = ContextCompat.getColor(context, R.color.shiori_openai),
                    textColor = ContextCompat.getColor(context, R.color.shiori_white),
                )

                domainValue.contains("arxiv") -> IconStyle(
                    label = "A",
                    backgroundColor = ContextCompat.getColor(context, R.color.shiori_arxiv),
                    textColor = Color.parseColor("#BA1731"),
                    strokeColor = ContextCompat.getColor(context, R.color.shiori_line),
                )

                status?.equals("Trashed", ignoreCase = true) == true -> IconStyle(
                    label = ">",
                    backgroundColor = ContextCompat.getColor(context, R.color.shiori_gray_icon),
                    textColor = ContextCompat.getColor(context, R.color.shiori_white),
                )

                else -> {
                    val palette = listOf(
                        Color.parseColor("#5971C8"),
                        Color.parseColor("#6C94D6"),
                        Color.parseColor("#C17262"),
                        Color.parseColor("#6E8B74"),
                        Color.parseColor("#9B7CA5"),
                        Color.parseColor("#A88E5E"),
                    )
                    val backgroundColor = palette[abs((domainValue.ifBlank { title }).hashCode()) % palette.size]
                    IconStyle(
                        label = buildMonogram(title, domainValue),
                        backgroundColor = backgroundColor,
                        textColor = ContextCompat.getColor(context, R.color.shiori_white),
                    )
                }
            }
        }

        private fun buildMonogram(title: String, domain: String): String {
            val cleanedTitle = title
                .replace(Regex("https?://", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                .trim()
            val words = cleanedTitle.split(Regex("\\s+")).filter { it.isNotBlank() }
            return when {
                words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
                words.isNotEmpty() -> words[0].take(2).uppercase()
                domain.isNotBlank() -> domain.take(2).uppercase()
                else -> "LK"
            }
        }

        private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        private companion object {
            const val MENU_OPEN = 1
            const val MENU_SELECT = 2
            const val MENU_TOGGLE_READ = 3
            const val MENU_EDIT = 4
            const val MENU_DELETE = 5
            const val MENU_RESTORE = 6
        }
    }
}
