package com.chunyan.devpulsestudio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.chunyan.devpulsestudio.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15 edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // System bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updatePadding(top = bars.top)
            binding.bottomNavigation.updatePadding(bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        // Navigation
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        binding.bottomNavigation.setupWithNavController(navHost.navController)

        // Hide bottom nav on detail screen
        navHost.navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.isVisible = destination.id != R.id.articleDetailFragment
        }
    }
}
