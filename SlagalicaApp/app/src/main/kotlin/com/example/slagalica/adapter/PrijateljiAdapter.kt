package com.example.slagalica.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slagalica.R
import com.example.slagalica.databinding.ItemPrijateljBinding
import com.example.slagalica.model.Liga
import com.example.slagalica.model.PrijateljItem

/**
 * Lista prijatelja / rezultata pretrage. Dugme na kartici je "Dodaj" ili
 * "Ukloni" zavisno od [PrijateljItem.jePrijatelj]; klik zove odgovarajući callback.
 */
class PrijateljiAdapter(
    private val onDodaj: (String) -> Unit,
    private val onUkloni: (String) -> Unit
) : ListAdapter<PrijateljItem, PrijateljiAdapter.PrijateljViewHolder>(DiffCallback()) {

    inner class PrijateljViewHolder(
        private val binding: ItemPrijateljBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PrijateljItem) {
            val user = item.user
            binding.tvImePrijatelj.text = user.username
            binding.ivAvatarPrijatelj.setImageResource(avatarRes(user.avatarId))

            val liga = Liga.fromIndex(user.league)
            binding.tvDetaljiPrijatelj.text =
                "${liga.emoji} ${liga.displayName} • ⭐ ${user.stars} • 🌍 ${user.region}"

            if (item.jePrijatelj) {
                binding.btnAkcijaPrijatelj.setText(R.string.btn_ukloni_prijatelja)
                binding.btnAkcijaPrijatelj.setOnClickListener { onUkloni(user.uid) }
            } else {
                binding.btnAkcijaPrijatelj.setText(R.string.btn_dodaj_prijatelja)
                binding.btnAkcijaPrijatelj.setOnClickListener { onDodaj(user.uid) }
            }
        }

        private fun avatarRes(avatarId: Int): Int = when (avatarId) {
            2 -> R.drawable.avatar_2
            3 -> R.drawable.avatar_3
            4 -> R.drawable.avatar_4
            else -> R.drawable.avatar_1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrijateljViewHolder {
        val binding = ItemPrijateljBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PrijateljViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PrijateljViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<PrijateljItem>() {
        override fun areItemsTheSame(oldItem: PrijateljItem, newItem: PrijateljItem) =
            oldItem.user.uid == newItem.user.uid
        override fun areContentsTheSame(oldItem: PrijateljItem, newItem: PrijateljItem) =
            oldItem == newItem
    }
}
