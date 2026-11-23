package com.prautotaskbot.ui.platform

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.prautotaskbot.data.model.PlatformInfo
import com.prautotaskbot.databinding.ItemPlatformBinding

class PlatformAdapter(
    private val onClick: (PlatformInfo) -> Unit
) : ListAdapter<PlatformInfo, PlatformAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlatformBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPlatformBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlatformInfo) {
            binding.tvPlatformName.text = item.displayName

            // Placeholder icon (nanti diganti dengan icon asli)
            binding.ivPlatformIcon.setImageResource(android.R.drawable.ic_menu_gallery)

            if (item.isImplemented) {
                binding.tvLocked.visibility = View.GONE
                binding.root.alpha = 1.0f
                binding.root.isEnabled = true
            } else {
                binding.tvLocked.visibility = View.VISIBLE
                binding.root.alpha = 0.45f
                binding.root.isEnabled = true // tetap bisa diklik untuk pesan "belum tersedia"
            }

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PlatformInfo>() {
        override fun areItemsTheSame(old: PlatformInfo, new: PlatformInfo) = old.id == new.id
        override fun areContentsTheSame(old: PlatformInfo, new: PlatformInfo) = old == new
    }
}
