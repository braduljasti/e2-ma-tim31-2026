package com.example.slagalica.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slagalica.R
import com.example.slagalica.databinding.ItemRegionRangBinding
import com.example.slagalica.model.RegionRangRed

/** Mjesečna rang lista po regionima; region ulogovanog igrača je istaknut. Klik → statistika. */
class RegionRangAdapter(
    private val onKlik: (String) -> Unit
) : ListAdapter<RegionRangRed, RegionRangAdapter.RegionViewHolder>(DiffCallback()) {

    inner class RegionViewHolder(
        private val binding: ItemRegionRangBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(red: RegionRangRed, mjesto: Int) {
            val ctx = binding.root.context
            binding.tvRegionRang.text = "$mjesto."
            binding.tvRegionIkona.text = red.emoji
            binding.tvRegionNaziv.text = red.regionNaziv
            binding.tvRegionBrojIgraca.text = ctx.getString(R.string.fmt_broj_igraca, red.brojIgraca)
            binding.tvRegionZvezde.text = ctx.getString(R.string.fmt_region_zvezde, red.ukupnoZvezda)

            binding.cardRegion.strokeWidth = if (red.mojRegion) dp(ctx, 2) else 0
            binding.cardRegion.setStrokeColor(ctx.getColorStateList(R.color.primary))
            binding.cardRegion.setOnClickListener { onKlik(red.regionNaziv) }
        }

        private fun dp(ctx: android.content.Context, value: Int): Int =
            (value * ctx.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegionViewHolder {
        val binding = ItemRegionRangBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RegionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RegionViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class DiffCallback : DiffUtil.ItemCallback<RegionRangRed>() {
        override fun areItemsTheSame(oldItem: RegionRangRed, newItem: RegionRangRed) =
            oldItem.regionNaziv == newItem.regionNaziv
        override fun areContentsTheSame(oldItem: RegionRangRed, newItem: RegionRangRed) =
            oldItem == newItem
    }
}
