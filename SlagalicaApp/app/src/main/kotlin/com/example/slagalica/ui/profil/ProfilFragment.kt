package com.example.slagalica.ui.profil

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.slagalica.R
import com.example.slagalica.databinding.DialogAvatarPickerBinding
import com.example.slagalica.databinding.FragmentProfilBinding
import com.example.slagalica.databinding.ItemGameStatistikaBinding
import com.example.slagalica.model.GameStatistic
import com.example.slagalica.model.PlayerStats
import com.example.slagalica.model.UserProfile
import com.example.slagalica.ui.auth.LoginActivity
import com.example.slagalica.viewmodel.ProfilViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Fragment za prikaz profila korisnika sa statistikom.
 *
 * Specifikacija KT1.1:
 *  - Osnovni podaci (ime, email, avatar sa okvirom, tokeni, zvezde, liga, region)
 *  - QR kod za poziv prijatelja
 *  - Promena avatara (dijalog)
 *  - Statistika po svim igrama
 *  - Logout
 */
class ProfilFragment : Fragment() {

    // ViewBinding po projektnoj konvenciji (vidi: NotifikacijeFragment)
    private var _binding: FragmentProfilBinding? = null
    private val binding get() = _binding!!

    // by viewModels() - delegate koji vraca ViewModel vezan za lifecycle ovog Fragmenta
    private val viewModel: ProfilViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupClickListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null  // sprecava memory leak (binding drzi reference na View-ove)
    }

    // ============================================================
    // POSMATRANJE VIEWMODEL-A
    // ============================================================

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            renderProfile(profile)
        }
        viewModel.playerStats.observe(viewLifecycleOwner) { stats ->
            renderStats(stats)
        }
    }

    private fun renderProfile(profile: UserProfile) {
        with(binding) {
            tvKorisnickoIme.text = profile.username
            tvEmail.text = profile.email
            tvRegion.text = "🌍 ${profile.region}"
            tvTokeni.text = "🪙 ${profile.tokens}"
            tvZvezde.text = "⭐ ${profile.totalStars}"
            tvLiga.text = "${profile.league.emoji} ${profile.league.displayName}"
            ivAvatar.setImageResource(profile.avatarResId)
        }
        generateQrCode(profile.qrPayload)
    }

    private fun renderStats(stats: PlayerStats) {
        binding.tvUkupnoPartija.text = "Ukupno partija: ${stats.totalGamesPlayed}"

        // Pobede - format string iz strings.xml: "Pobede: %1$.0f%%"
        binding.tvPobedeLabel.text = getString(R.string.fmt_pobede, stats.winPercent)
        binding.pbPobede.progress = stats.winPercent.toInt()

        // Porazi
        binding.tvPoraziLabel.text = getString(R.string.fmt_porazi, stats.lossPercent)
        binding.pbPorazi.progress = stats.lossPercent.toInt()

        // Statistika za svaku od 6 igara
        bindGameStat(binding.statKoZnaZna, stats.koZnaZna)
        bindGameStat(binding.statMojBroj, stats.mojBroj)
        bindGameStat(binding.statKorakPoKorak, stats.korakPoKorak)
        bindGameStat(binding.statAsocijacije, stats.asocijacije)
        bindGameStat(binding.statSkocko, stats.skocko)
        bindGameStat(binding.statSpojnice, stats.spojnice)
    }

    /**
     * Pomocna funkcija - punimo jedan game stat blok (item_game_statistika).
     * itemBinding je auto-generisani binding za ono sto smo includirali u layoutu.
     */
    private fun bindGameStat(itemBinding: ItemGameStatistikaBinding, stat: GameStatistic) {
        itemBinding.tvGameName.text = stat.gameName
        itemBinding.tvAveragePoints.text = stat.averagePointsLabel
        itemBinding.tvGamesPlayed.text = "${stat.gamesPlayed} partija"
        itemBinding.tvMetricLabel.text = stat.mainMetricLabel
        itemBinding.tvMetricValue.text = "${stat.mainMetricPercent.toInt()}%"
        itemBinding.pbMetric.progress = stat.mainMetricPercent.toInt()
    }

    // ============================================================
    // QR KOD
    // ============================================================

    /**
     * Pretvara payload string u QR kod bitmap.
     * BitMatrix je 2D matrica boolean vrednosti (true = crna, false = bela).
     * Pravimo Bitmap pixel-by-pixel i postavljamo ga u ImageView.
     */
    private fun generateQrCode(payload: String) {
        try {
            val size = 512  // 512x512 px - dovoljno ostro za skeniranje
            val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQrKod.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            Log.e(TAG, "Greška pri generisanju QR koda", e)
        }
    }

    // ============================================================
    // KLIK HENDLERI I DIJALOZI
    // ============================================================

    private fun setupClickListeners() {
        binding.btnPromeniAvatar.setOnClickListener { showAvatarPicker() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    private fun showAvatarPicker() {
        val pickerBinding = DialogAvatarPickerBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dlg_avatar_title)
            .setView(pickerBinding.root)
            .setNegativeButton(R.string.dlg_logout_cancel, null)
            .create()

        // Mapiranje: koja ImageView -> koji avatar resurs
        val picks = listOf(
            pickerBinding.ivPick1 to R.drawable.avatar_1,
            pickerBinding.ivPick2 to R.drawable.avatar_2,
            pickerBinding.ivPick3 to R.drawable.avatar_3,
            pickerBinding.ivPick4 to R.drawable.avatar_4
        )

        picks.forEach { (imageView, avatarRes) ->
            imageView.setOnClickListener {
                viewModel.changeAvatar(avatarRes)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dlg_logout_title)
            .setMessage(R.string.dlg_logout_msg)
            .setPositiveButton(R.string.dlg_logout_confirm) { _, _ -> performLogout() }
            .setNegativeButton(R.string.dlg_logout_cancel, null)
            .show()
    }

    /**
     * Vodimo korisnika nazad na LoginActivity i zatvaramo MainActivity
     * iz back stack-a (FLAG_ACTIVITY_CLEAR_TASK), tako da pritisak na "back"
     * iz Login-a ne moze da vrati ovde.
     */
    private fun performLogout() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }

    companion object {
        private const val TAG = "ProfilFragment"
    }
}