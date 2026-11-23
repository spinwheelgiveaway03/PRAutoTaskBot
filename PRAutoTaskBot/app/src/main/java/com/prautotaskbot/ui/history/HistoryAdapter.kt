package com.prautotaskbot.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskStatus
import com.prautotaskbot.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : ListAdapter<Task, HistoryAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.tvPlatform.text = task.platform
            binding.tvType.text = task.type.name
            binding.tvTarget.text = task.targetUsername ?: task.targetUrl ?: "-"
            binding.tvReward.text = task.reward ?: "-"
            binding.tvStatus.text = task.status.name
            binding.tvTime.text = dateFormat.format(Date(task.finishedAt ?: task.foundAt))

            val color = when (task.status) {
                TaskStatus.COMPLETED -> 0xFF4CAF50.toInt()
                TaskStatus.FAILED -> 0xFFF44336.toInt()
                TaskStatus.SKIPPED -> 0xFF9E9E9E.toInt()
                else -> 0xFFFFC107.toInt()
            }
            binding.tvStatus.setTextColor(color)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(old: Task, new: Task) = old.id == new.id
        override fun areContentsTheSame(old: Task, new: Task) = old == new
    }
}
