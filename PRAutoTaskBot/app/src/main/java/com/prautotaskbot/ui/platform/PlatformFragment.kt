package com.prautotaskbot.ui.platform

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.prautotaskbot.MainActivity
import com.prautotaskbot.PRAutoTaskBotApp
import com.prautotaskbot.data.model.Platforms
import com.prautotaskbot.databinding.FragmentPlatformBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlatformFragment : Fragment() {

    private var _binding: FragmentPlatformBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: PlatformAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlatformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as PRAutoTaskBotApp

        adapter = PlatformAdapter { platform ->
            if (!platform.isImplemented) {
                Toast.makeText(requireContext(), "Platform ${platform.displayName} belum diimplementasikan", Toast.LENGTH_SHORT).show()
                return@PlatformAdapter
            }
            lifecycleScope.launch {
                app.settingsDataStore.setActivePlatform(platform.id)
                Toast.makeText(requireContext(), "Platform aktif: ${platform.displayName}", Toast.LENGTH_SHORT).show()
                updateActiveLabel()
            }
        }

        binding.recyclerPlatforms.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerPlatforms.adapter = adapter
        adapter.submitList(Platforms.ALL)

        binding.btnActivateBubble.setOnClickListener {
            (activity as? MainActivity)?.requestOverlayPermission()
        }

        binding.btnOpenPrSocial.setOnClickListener {
            (activity as? MainActivity)?.openPrSocial()
        }

        updateActiveLabel()
    }

    private fun updateActiveLabel() {
        val app = requireActivity().application as PRAutoTaskBotApp
        lifecycleScope.launch {
            val activeId = app.settingsDataStore.activePlatform.first()
            val platform = Platforms.getById(activeId)
            binding.tvActivePlatform.text = "Platform aktif: ${platform?.displayName ?: activeId}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
