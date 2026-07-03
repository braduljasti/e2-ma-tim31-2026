package com.example.slagalica.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slagalica.databinding.ItemChatMineBinding
import com.example.slagalica.databinding.ItemChatTheirsBinding
import com.example.slagalica.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val myUid: () -> String
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TIP_MOJA = 1
        private const val TIP_TUDJA = 2
        private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val DATE_FMT = SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault())

        fun formatVrijeme(timestampMs: Long): String {
            val poruka = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
            val danas = java.util.Calendar.getInstance()
            val istiDan = poruka.get(java.util.Calendar.YEAR) == danas.get(java.util.Calendar.YEAR) &&
                    poruka.get(java.util.Calendar.DAY_OF_YEAR) == danas.get(java.util.Calendar.DAY_OF_YEAR)
            val vrijeme = TIME_FMT.format(Date(timestampMs))
            return if (istiDan) vrijeme else "${DATE_FMT.format(Date(timestampMs))} $vrijeme"
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).senderUid == myUid()) TIP_MOJA else TIP_TUDJA

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIP_MOJA) {
            MojaViewHolder(ItemChatMineBinding.inflate(inflater, parent, false))
        } else {
            TudjaViewHolder(ItemChatTheirsBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val poruka = getItem(position)
        when (holder) {
            is MojaViewHolder -> holder.bind(poruka)
            is TudjaViewHolder -> holder.bind(poruka)
        }
    }

    class MojaViewHolder(private val binding: ItemChatMineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(poruka: ChatMessage) {
            binding.tvChatTekst.text = poruka.text
            binding.tvChatVrijeme.text = ChatAdapter.formatVrijeme(poruka.timestampMs)
        }
    }

    class TudjaViewHolder(private val binding: ItemChatTheirsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(poruka: ChatMessage) {
            binding.tvChatPosiljalac.text = poruka.senderName
            binding.tvChatTekst.text = poruka.text
            binding.tvChatVrijeme.text = ChatAdapter.formatVrijeme(poruka.timestampMs)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }
}
