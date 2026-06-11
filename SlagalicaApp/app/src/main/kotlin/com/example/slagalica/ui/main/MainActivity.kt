package com.example.slagalica.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.slagalica.R
import com.example.slagalica.databinding.ActivityMainBinding
import com.example.slagalica.ui.auth.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 11.a - registracija sistemskih notifikacionih kanala (čat, rangiranje, nagrade, ostalo)
        com.example.slagalica.data.NotificationChannels.createAll(this)
        // Jednokratno punjenje baze podacima za igre (ako su kolekcije prazne)
        lifecycleScope.launch {
            runCatching { com.example.slagalica.data.GameDataRepository().seedIfEmpty() }
        }
        setupToolbar()
        setupNavigation()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.appBarMain.toolbar)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_igraj, R.id.nav_notifikacije),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)
        binding.navView.setNavigationItemSelectedListener(this)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifikacije -> { navController.navigate(R.id.nav_notifikacije); true }
            R.id.action_profil -> {
                navController.navigate(R.id.profilFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_igraj -> navController.navigate(R.id.nav_igraj)
            R.id.nav_notifikacije -> navController.navigate(R.id.nav_notifikacije)
            R.id.nav_korak_po_korak -> navController.navigate(R.id.nav_korak_po_korak)
            R.id.nav_ko_zna_zna -> navController.navigate(R.id.kzzFragment)
            R.id.nav_spojnice -> navController.navigate(R.id.spojniceFragment)
            R.id.nav_asocijacije -> navController.navigate(R.id.asocijacijeFragment)
            R.id.nav_moj_broj -> navController.navigate(R.id.nav_moj_broj)
            R.id.nav_skocko -> navController.navigate(R.id.nav_skocko)
            R.id.nav_odjava -> {
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
