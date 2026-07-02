package com.example.slagalica.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slagalica.R
import com.example.slagalica.databinding.ItemRangListaBinding
import com.example.slagalica.model.Liga
import com.example.slagalica.model.RangListaStavka

class RangListaAdapter(
    private val myUid: () -> String
) : ListAdapter<RangListaStavka, RangListaAdapter.RangViewHolder>(DiffCallback()) {

    inner class RangViewHolder(private val binding: ItemRangListaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stavka: RangListaStavka, mesto: Int) {
            binding.tvRangMesto.text = "$mesto."
            binding.tvRangLigaIkona.text = Liga.fromIndex(stavka.league).emoji
            binding.tvRangKorisnickoIme.text = stavka.username
            binding.tvRangZvezde.text = "${stavka.stars} ⭐"

            val jaSam = stavka.uid == myUid()
            val bgColor = when {
                jaSam -> R.color.notif_neprocitana_bg
                else -> R.color.surface
            }
            binding.root.setCardBackgroundColor(binding.root.context.getColor(bgColor))

            // Medalje za prva tri mjesta (koristi iste boje kao okviri regiona, spec 5.e)
            val strokeColor = when (mesto) {
                1 -> R.color.medalja_zlato
                2 -> R.color.medalja_srebro
                3 -> R.color.medalja_bronza
                else -> R.color.primary_light
            }
            binding.root.strokeColor = binding.root.context.getColor(strokeColor)
            binding.root.strokeWidth = if (mesto <= 3) 4 else 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RangViewHolder {
        val binding = ItemRangListaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RangViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RangViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class DiffCallback : DiffUtil.ItemCallback<RangListaStavka>() {
        override fun areItemsTheSame(oldItem: RangListaStavka, newItem: RangListaStavka) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: RangListaStavka, newItem: RangListaStavka) = oldItem == newItem
    }
}
