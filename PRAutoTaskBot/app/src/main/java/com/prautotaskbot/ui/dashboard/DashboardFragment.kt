package com.prautotaskbot.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prautotaskbot.PRAutoTaskBotApp
import com.prautotaskbot.data.model.Task
import com.prautotaskbot.data.model.TaskStatus
import com.prautotaskbot.data.model.TaskType
import com.prautotaskbot.databinding.FragmentDashboardBinding
import com.prautotaskbot.integration.PrSocialHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as PRAutoTaskBotApp

        // Live counts
        lifecycleScope.launch {
            app.taskRepository.countByStatus(TaskStatus.PENDING).collectLatest {
                binding.tvPendingCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            app.taskRepository.countByStatus(TaskStatus.COMPLETED).collectLatest {
                binding.tvCompletedCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            app.taskRepository.countByStatus(TaskStatus.FAILED).collectLatest {
                binding.tvFailedCount.text = it.toString()
            }
        }
        lifecycleScope.launch {
            app.taskRepository.countByStatus(TaskStatus.IN_PROGRESS).collectLatest {
                binding.tvInProgressCount.text = it.toString()
            }
        }

        // Queue state + current task
        lifecycleScope.launch {
            app.taskQueueManager.queueState.collectLatest { state ->
                binding.tvStatus.text = "Status: $state"
            }
        }
        lifecycleScope.launch {
            app.taskQueueManager.currentTask.collectLatest { task ->
                if (task != null) {
                    binding.tvCurrentTask.text = "Sedang: ${task.type} @${task.targetUsername ?: "-"}"
                    binding.tvCurrentTask.visibility = View.VISIBLE
                } else {
                    binding.tvCurrentTask.text = "Tidak ada tugas aktif"
                    binding.tvCurrentTask.visibility = View.VISIBLE
                }
            }
        }

        binding.tvMonitoring.text = "Monitoring aktif saat antrean kosong (60–120 dtk)"

        // Tambah tugas Follow cepat
        binding.btnAddFollow.setOnClickListener {
            val username = binding.etUsername.text.toString().trim().removePrefix("@")
            if (username.isEmpty()) {
                Toast.makeText(requireContext(), "Isi username target dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val task = Task(
                    platform = "instagram",
                    type = TaskType.FOLLOW,
                    targetUsername = username,
                    targetUrl = "https://www.instagram.com/$username/",
                    reward = binding.etReward.text.toString().ifBlank { null },
                    status = TaskStatus.PENDING
                )
                app.taskQueueManager.addTask(task)
                binding.etUsername.text?.clear()
                Toast.makeText(requireContext(), "FOLLOW @$username masuk antrean", Toast.LENGTH_SHORT).show()
            }
        }

        // Tambah tugas Like cepat
        binding.btnAddLike.setOnClickListener {
            val username = binding.etUsername.text.toString().trim().removePrefix("@")
            if (username.isEmpty()) {
                Toast.makeText(requireContext(), "Isi username / link dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val task = Task(
                    platform = "instagram",
                    type = TaskType.LIKE,
                    targetUsername = username,
                    targetUrl = if (username.startsWith("http")) username else "https://www.instagram.com/$username/",
                    reward = binding.etReward.text.toString().ifBlank { null },
                    status = TaskStatus.PENDING
                )
                app.taskQueueManager.addTask(task)
                binding.etUsername.text?.clear()
                Toast.makeText(requireContext(), "LIKE masuk antrean", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenPr.setOnClickListener {
            PrSocialHelper.openPrSocial(requireContext())
        }

        binding.btnStartQueue.setOnClickListener {
            lifecycleScope.launch {
                app.taskQueueManager.start()
                Toast.makeText(requireContext(), "Antrean START", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
