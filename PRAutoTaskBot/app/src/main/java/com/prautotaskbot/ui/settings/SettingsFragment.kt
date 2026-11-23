package com.prautotaskbot.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prautotaskbot.PRAutoTaskBotApp
import com.prautotaskbot.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as PRAutoTaskBotApp

        lifecycleScope.launch {
            binding.etInterval.setText(app.settingsDataStore.monitoringIntervalSec.first().toString())
            binding.switchNotifications.isChecked = app.settingsDataStore.notificationsEnabled.first()
            binding.switchDarkTheme.isChecked = app.settingsDataStore.darkTheme.first()
        }

        binding.btnSaveInterval.setOnClickListener {
            val value = binding.etInterval.text.toString().toIntOrNull() ?: 90
            lifecycleScope.launch {
                app.settingsDataStore.setMonitoringInterval(value)
                Toast.makeText(requireContext(), "Interval disimpan: $value detik", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                app.settingsDataStore.setNotificationsEnabled(isChecked)
            }
        }

        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                app.settingsDataStore.setDarkTheme(isChecked)
                Toast.makeText(requireContext(), "Tema akan diterapkan setelah restart activity", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetStats.setOnClickListener {
            lifecycleScope.launch {
                app.taskRepository.clearCompletedAndFailed()
                Toast.makeText(requireContext(), "Statistik selesai/gagal dihapus", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
