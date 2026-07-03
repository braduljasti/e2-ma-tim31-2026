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

class ProfilFragment : Fragment() {

    private var _binding: FragmentProfilBinding? = null
    private val binding get() = _binding!!

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
        _binding = null
    }

    private fun observeViewModel() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            renderProfile(profile)
        }
        viewModel.playerStats.observe(viewLifecycleOwner) { stats ->
            renderStats(stats)
        }
        // Spec 5.e - boja okvira avatara po plasmanu regiona u prošlom ciklusu
        viewModel.okvirBoja.observe(viewLifecycleOwner) { colorRes ->
            binding.ivAvatar.backgroundTintList = colorRes?.let {
                android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), it)
                )
            }
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

        binding.tvPobedeLabel.text = getString(R.string.fmt_pobede, stats.winPercent)
        binding.pbPobede.progress = stats.winPercent.toInt()

        binding.tvPoraziLabel.text = getString(R.string.fmt_porazi, stats.lossPercent)
        binding.pbPorazi.progress = stats.lossPercent.toInt()

        bindGameStat(binding.statKoZnaZna, stats.koZnaZna)
        bindGameStat(binding.statMojBroj, stats.mojBroj)
        bindGameStat(binding.statKorakPoKorak, stats.korakPoKorak)
        bindGameStat(binding.statAsocijacije, stats.asocijacije)
        bindGameStat(binding.statSkocko, stats.skocko)
        bindGameStat(binding.statSpojnice, stats.spojnice)
    }

    private fun bindGameStat(itemBinding: ItemGameStatistikaBinding, stat: GameStatistic) {
        itemBinding.tvGameName.text = stat.gameName
        itemBinding.tvAveragePoints.text = stat.averagePointsLabel
        itemBinding.tvGamesPlayed.text = "${stat.gamesPlayed} partija"
        itemBinding.tvMetricLabel.text = stat.mainMetricLabel
        itemBinding.tvMetricValue.text = "${stat.mainMetricPercent.toInt()}%"
        itemBinding.pbMetric.progress = stat.mainMetricPercent.toInt()
    }

    private fun generateQrCode(payload: String) {
        try {
            val size = 512
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

    private fun setupClickListeners() {
        binding.btnPromeniAvatar.setOnClickListener { showAvatarPicker() }
        binding.btnPromeniLozinku.setOnClickListener { showChangePasswordDialog() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    /** Spec 1.e: promjena lozinke unosom stare lozinke i nove lozinke dva puta (potvrda). */
    private fun showChangePasswordDialog() {
        val dialogBinding = com.example.slagalica.databinding.DialogPromeniLozinkuBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dlg_promeni_lozinku_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dlg_promeni_lozinku_potvrdi, null)
            .setNegativeButton(R.string.dlg_logout_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val stara = dialogBinding.etStaraLozinka.text?.toString().orEmpty()
                val nova = dialogBinding.etNovaLozinka.text?.toString().orEmpty()
                val potvrda = dialogBinding.etPotvrdiNovuLozinku.text?.toString().orEmpty()

                val greska = when {
                    stara.isEmpty() -> getString(R.string.err_prazno_polje)
                    nova.length < 6 -> "Nova lozinka mora imati bar 6 karaktera"
                    nova != potvrda -> getString(R.string.err_lozinke_ne_poklapaju)
                    else -> null
                }
                if (greska != null) {
                    dialogBinding.tvGreskaLozinka.text = greska
                    dialogBinding.tvGreskaLozinka.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                viewModel.promeniLozinku(stara, nova)
            }
        }

        viewModel.lozinkaPromenjena.observe(viewLifecycleOwner) { uspjeh ->
            if (uspjeh == null) return@observe
            viewModel.consumeLozinkaPromenjena()
            if (uspjeh) {
                dialog.dismiss()
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root, R.string.msg_lozinka_promenjena,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            } else {
                dialogBinding.tvGreskaLozinka.text = viewModel.lozinkaGreska.value
                dialogBinding.tvGreskaLozinka.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun showAvatarPicker() {
        val pickerBinding = DialogAvatarPickerBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dlg_avatar_title)
            .setView(pickerBinding.root)
            .setNegativeButton(R.string.dlg_logout_cancel, null)
            .create()

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

    private fun performLogout() {
        viewModel.logout()
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
