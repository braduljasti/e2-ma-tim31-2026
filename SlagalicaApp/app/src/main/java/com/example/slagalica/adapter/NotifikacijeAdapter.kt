package com.example.slagalica.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slagalica.R
import com.example.slagalica.databinding.ItemNotifikacijaBinding
import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationCategory

class NotifikacijeAdapter(
    private val onMarkRead: (Long) -> Unit
) : ListAdapter<AppNotification, NotifikacijeAdapter.NotificationViewHolder>(DiffCallback()) {

    inner class NotificationViewHolder(
        private val binding: ItemNotifikacijaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: AppNotification) {
            binding.tvIkonaEmoji.text = notification.emoji()
            setCategoryColor(notification.category)

            binding.tvKategorijaNotif.text = notification.category.label()
            binding.tvNaslovNotif.text = notification.title
            binding.tvSadrzajNotif.text = notification.content
            binding.tvVrijemeNotif.text = notification.relativeTime()

            binding.viewNeprocitanaIndikator.visibility =
                if (!notification.read) View.VISIBLE else View.GONE

            binding.root.setCardBackgroundColor(
                binding.root.context.getColor(
                    if (!notification.read) R.color.notif_neprocitana_bg else android.R.color.white
                )
            )

            binding.btnOznaciProcitano.visibility =
                if (!notification.read) View.VISIBLE else View.INVISIBLE

            binding.btnOznaciProcitano.setOnClickListener { onMarkRead(notification.id) }
            binding.root.setOnClickListener { if (!notification.read) onMarkRead(notification.id) }
        }

        private fun setCategoryColor(category: NotificationCategory) {
            val colorRes = when (category) {
                NotificationCategory.CHAT -> R.color.notif_chat
                NotificationCategory.RANK -> R.color.notif_rang
                NotificationCategory.REWARDS -> R.color.notif_nagrade
                NotificationCategory.OTHER -> R.color.notif_ostalo
            }
            binding.tvKategorijaNotif.setTextColor(binding.root.context.getColor(colorRes))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotifikacijaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem == newItem
    }
}

fun NotificationCategory.label(): String = when (this) {
    NotificationCategory.CHAT -> "Message"
    NotificationCategory.RANK -> "Ranking"
    NotificationCategory.REWARDS -> "Reward"
    NotificationCategory.OTHER -> "Notification"
}
