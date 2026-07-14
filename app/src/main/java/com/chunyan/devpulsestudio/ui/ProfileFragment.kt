package com.chunyan.devpulsestudio.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chunyan.devpulsestudio.PulseApplication
import com.chunyan.devpulsestudio.R
import com.chunyan.devpulsestudio.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val repository
        get() = (requireActivity().application as PulseApplication).container.repository

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                requireContext().contentResolver
                    .openOutputStream(uri)
                    ?.bufferedWriter()
                    ?.use { it.write(repository.exportSaved()) }
            }
                .onSuccess { toast(R.string.export_success) }
                .onFailure { toast(R.string.data_error) }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val text = requireContext().contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("empty")
                repository.importSaved(text)
            }
                .onSuccess { count -> toast(getString(R.string.import_success, count)) }
                .onFailure { toast(R.string.data_error) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentProfileBinding.bind(view)

        // Dark mode switch
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        b.darkMode.isChecked = isDark
        b.darkMode.setOnCheckedChangeListener { _, checked ->
            val mode = if (checked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Clear cache
        b.clearCache.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.clearCaches()
                toast(R.string.cache_cleared)
            }
        }

        // Export / import
        b.exportData.setOnClickListener {
            exportLauncher.launch("devpulse-backup.json")
        }
        b.importData.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        }

        // Contact
        b.contactDeveloper.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("mailto:2477574245@qq.com?subject=开源脉搏%20反馈"),
            )
            runCatching { startActivity(intent) }
                .onFailure { toast(R.string.data_error) }
        }
    }

    private fun toast(message: Any) {
        val text = if (message is Int) getString(message) else message.toString()
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }
}
