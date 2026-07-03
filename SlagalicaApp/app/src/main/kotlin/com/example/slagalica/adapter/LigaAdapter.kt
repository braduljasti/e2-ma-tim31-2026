package com.example.slagalica.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slagalica.R
import com.example.slagalica.databinding.ItemLigaBinding
import com.example.slagalica.model.LigaRed

class LigaAdapter : ListAdapter<LigaRed, LigaAdapter.LigaViewHolder>(DiffCallback()) {

    inner class LigaViewHolder(
        private val binding: ItemLigaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(red: LigaRed) {
            val ctx = binding.root.context
            binding.tvLigaIkona.text = red.liga.emoji
            binding.tvLigaNaziv.text = red.liga.displayName
            binding.tvLigaPrag.text = ctx.getString(R.string.fmt_liga_prag, red.prag, red.tokeniDan)
            binding.tvLigaTrenutna.visibility = if (red.jeTrenutna) View.VISIBLE else View.GONE
            binding.cardLiga.strokeWidth = if (red.jeTrenutna) dp(ctx, 2) else 0
            binding.cardLiga.setStrokeColor(ctx.getColorStateList(R.color.success))
        }

        private fun dp(ctx: android.content.Context, value: Int): Int =
            (value * ctx.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LigaViewHolder {
        val binding = ItemLigaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LigaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LigaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<LigaRed>() {
        override fun areItemsTheSame(oldItem: LigaRed, newItem: LigaRed) = oldItem.liga == newItem.liga
        override fun areContentsTheSame(oldItem: LigaRed, newItem: LigaRed) = oldItem == newItem
    }
}
