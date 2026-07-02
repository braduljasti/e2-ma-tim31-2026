package com.example.slagalica.ui.prijatelji

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AlertDialog
import com.example.slagalica.R
import com.example.slagalica.adapter.PrijateljiAdapter
import com.example.slagalica.databinding.FragmentPrijateljiBinding
import com.example.slagalica.model.PozivNaPartiju
import com.example.slagalica.viewmodel.MultiplayerViewModel
import com.example.slagalica.viewmodel.PrijateljiViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Ekran prijatelja (spec 7.a, 7.b): lista prijatelja kad je pretraga prazna,
 * rezultati pretrage dok se kuca, dodavanje preko korisničkog imena ili QR koda.
 */
class PrijateljiFragment : Fragment() {

    private var _binding: FragmentPrijateljiBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PrijateljiViewModel
    private lateinit var mpViewModel: MultiplayerViewModel
    private lateinit var adapter: PrijateljiAdapter
    private var cekanjeDialog: AlertDialog? = null

    // QR skener (ZXing). Po skeniranju pokušava da izvuče uid iz slagalica://invite/{uid}.
    private val qrSkener = registerForActivityResult(ScanContract()) { rezultat ->
        val sadrzaj = rezultat.contents
        if (sadrzaj != null) obradiQr(sadrzaj)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrijateljiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[PrijateljiViewModel::class.java]
        mpViewModel = ViewModelProvider(requireActivity())[MultiplayerViewModel::class.java]
        setupRecyclerView()
        setupPretraga()
        observeChanges()
    }

    private fun setupRecyclerView() {
        adapter = PrijateljiAdapter(
            onDodaj = { uid -> viewModel.dodaj(uid) },
            onUkloni = { uid -> viewModel.ukloni(uid) },
            onPozovi = { uid -> pozoviNaPartiju(uid) }
        )
        binding.rvPrijatelji.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PrijateljiFragment.adapter
        }
    }

    private fun setupPretraga() {
        binding.etPretraga.addTextChangedListener { text ->
            viewModel.pretrazi(text?.toString().orEmpty())
        }
        binding.tilPretraga.setEndIconOnClickListener { pokreniQrSkener() }
    }

    /** Šalje poziv na prijateljsku partiju (spec 7.c) i prikazuje čekanje sa Otkaži (7.e). */
    private fun pozoviNaPartiju(friendUid: String) {
        mpViewModel.posaljiPozivPrijatelju(friendUid)
        cekanjeDialog?.dismiss()
        cekanjeDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.poziv_poslat_naslov)
            .setMessage(R.string.poziv_poslat_poruka)
            .setNegativeButton(R.string.dlg_logout_cancel) { _, _ ->
                mpViewModel.otkaziPoslatiPoziv()
            }
            .setCancelable(false)
            .show()
    }

    private fun observeChanges() {
        viewModel.stavke.observe(viewLifecycleOwner) { adapter.submitList(it) }

        // Ishod mog poslatog poziva: odbijen/otkazan -> poruka; prihvaćen ->
        // MainActivity preuzima navigaciju u partiju (prijateljskaSpremna).
        mpViewModel.poslatiPoziv.observe(viewLifecycleOwner) { poziv ->
            when (poziv?.status) {
                PozivNaPartiju.DECLINED, PozivNaPartiju.CANCELLED -> {
                    cekanjeDialog?.dismiss(); cekanjeDialog = null
                    mpViewModel.consumePoslatiPoziv()
                    Snackbar.make(binding.root, R.string.poziv_odbijen, Snackbar.LENGTH_LONG).show()
                }
                PozivNaPartiju.ACCEPTED -> {
                    cekanjeDialog?.dismiss(); cekanjeDialog = null
                    mpViewModel.consumePoslatiPoziv()
                }
                else -> Unit
            }
        }
        viewModel.prazno.observe(viewLifecycleOwner) { prazno ->
            binding.llPraznoPrijatelji.visibility = if (prazno) View.VISIBLE else View.GONE
            // Tekst praznog stanja zavisi od moda (lista vs pretraga)
            binding.tvPraznoPrijatelji.setText(
                if (binding.etPretraga.text.isNullOrBlank()) R.string.lbl_nema_prijatelja
                else R.string.lbl_nema_rezultata
            )
        }
        viewModel.ucitavanje.observe(viewLifecycleOwner) { ucitava ->
            binding.progressPrijatelji.visibility = if (ucitava) View.VISIBLE else View.GONE
        }
    }

    // ===== QR =====

    private fun pokreniQrSkener() {
        val opcije = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.qr_skeniraj_prompt))
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCaptureActivity(PortraitCaptureActivity::class.java)   // uspravna kamera
        }
        qrSkener.launch(opcije)
    }

    /** Iz QR sadržaja "slagalica://invite/{uid}" izvuče uid i doda prijatelja. */
    private fun obradiQr(sadrzaj: String) {
        val prefiks = "slagalica://invite/"
        val uid = sadrzaj.removePrefix(prefiks).trim()
        if (sadrzaj.startsWith(prefiks) && uid.isNotBlank()) {
            viewModel.dodaj(uid)
            Snackbar.make(binding.root, R.string.msg_prijatelj_dodat, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(binding.root, R.string.err_qr_nevalidan, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cekanjeDialog?.dismiss()
        cekanjeDialog = null
        _binding = null
    }
}
