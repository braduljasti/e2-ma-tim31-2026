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
    private var headerListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.example.slagalica.data.NotificationChannels.createAll(this)

        lifecycleScope.launch {
            runCatching { com.example.slagalica.data.GameDataRepository().seedIfEmpty() }
        }
        lifecycleScope.launch {
            runCatching {
                com.example.slagalica.data.FirebaseProvider.currentUid?.let { uid ->
                    val nagrade = com.example.slagalica.data.RangListaRepository().pripremiZavrsetakCiklusaAkoTreba(uid)
                    nagrade.forEach { NagradaAnimacija.prikazi(this@MainActivity, it) }
                }
            }
            val outcome = runCatching {
                com.example.slagalica.data.ProgressionRepository().reconcileOnStart()
            }.getOrNull()
            if (outcome != null && outcome.tokensAdded > 0) {
                Snackbar.make(binding.root,
                    getString(R.string.msg_dnevni_tokeni, outcome.tokensAdded),
                    Snackbar.LENGTH_LONG).show()
            }
            runCatching { com.example.slagalica.data.RegionRepository().arhivirajProsliCiklusAkoTreba() }
        }
        setupToolbar()
        setupNavigation()
        setupNavHeader()
        setupPoziviNaPartiju()
    }

    private fun setupPoziviNaPartiju() {
        val mp = androidx.lifecycle.ViewModelProvider(this)[com.example.slagalica.viewmodel.MultiplayerViewModel::class.java]
        mp.slusajDolaznePozive()

        mp.dolazniPoziv.observe(this) { poziv ->
            if (poziv != null) prikaziDolazniPoziv(mp, poziv)
        }
        mp.prijateljskaSpremna.observe(this) { matchId ->
            if (matchId != null) {
                mp.consumePrijateljskaSpremna()
                navController.navigate(R.id.nav_partija_mp)
            }
        }
    }

    private fun prikaziDolazniPoziv(
        mp: com.example.slagalica.viewmodel.MultiplayerViewModel,
        poziv: com.example.slagalica.model.PozivNaPartiju
    ) {
        var odgovoreno = false
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.poziv_naslov, poziv.fromName))
            .setMessage(getString(R.string.poziv_poruka, 10))
            .setPositiveButton(R.string.poziv_prihvati) { _, _ ->
                odgovoreno = true
                mp.prihvatiPoziv(poziv)
            }
            .setNegativeButton(R.string.poziv_odbij) { _, _ ->
                odgovoreno = true
                mp.odbijPoziv(poziv)
            }
            .setCancelable(false)
            .create()
        dialog.show()

        object : android.os.CountDownTimer(10_000L, 1_000L) {
            override fun onTick(ms: Long) {
                if (dialog.isShowing) {
                    dialog.setMessage(getString(R.string.poziv_poruka, (ms / 1000).toInt() + 1))
                } else {
                    cancel()
                }
            }

            override fun onFinish() {
                if (dialog.isShowing && !odgovoreno) {
                    dialog.dismiss()
                    mp.odbijPoziv(poziv)
                }
            }
        }.start()
    }

    private fun setupNavHeader() {
        val header = binding.navView.getHeaderView(0)
        val tvIme = header.findViewById<android.widget.TextView>(R.id.tvKorisnickoImeNav)
        val tvEmail = header.findViewById<android.widget.TextView>(R.id.tvEmailNav)
        val ivAvatar = header.findViewById<android.widget.ImageView>(R.id.ivAvatar)
        val tvTokeni = header.findViewById<android.widget.TextView>(R.id.tvTokeniNav)
        val tvZvijezde = header.findViewById<android.widget.TextView>(R.id.tvZvjezdiceNav)

        headerListener = com.example.slagalica.data.ProfilRepository().slusajKorisnika { user ->
            if (user == null) return@slusajKorisnika
            runOnUiThread {
                tvIme.text = user.username.ifBlank { "Korisnik" }
                tvEmail.text = user.email
                tvTokeni?.text = "🪙 ${user.tokens} tokena"
                tvZvijezde?.text = "⭐ ${user.stars} zvjezdica"
                ivAvatar.setImageResource(when (user.avatarId) {
                    2 -> R.drawable.avatar_2
                    3 -> R.drawable.avatar_3
                    4 -> R.drawable.avatar_4
                    else -> R.drawable.avatar_1
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        headerListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching { com.example.slagalica.data.PresenceRepository().azuriraj() }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.appBarMain.toolbar)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_igraj, R.id.nav_notifikacije, R.id.nav_rang_lista, R.id.nav_chat),
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
            R.id.nav_prijatelji -> navController.navigate(R.id.nav_prijatelji)
            R.id.nav_lige -> navController.navigate(R.id.nav_lige)
            R.id.nav_regioni -> navController.navigate(R.id.nav_regioni)
            R.id.nav_rang_lista -> navController.navigate(R.id.nav_rang_lista)
            R.id.nav_chat -> navController.navigate(R.id.nav_chat)
            R.id.nav_korak_po_korak -> navController.navigate(R.id.nav_korak_po_korak)
            R.id.nav_ko_zna_zna -> navController.navigate(R.id.kzzFragment)
            R.id.nav_spojnice -> navController.navigate(R.id.spojniceFragment)
            R.id.nav_asocijacije -> navController.navigate(R.id.asocijacijeFragment)
            R.id.nav_moj_broj -> navController.navigate(R.id.nav_moj_broj)
            R.id.nav_skocko -> navController.navigate(R.id.nav_skocko)
            R.id.nav_odjava -> {
                com.example.slagalica.data.FirebaseProvider.auth.signOut()
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
