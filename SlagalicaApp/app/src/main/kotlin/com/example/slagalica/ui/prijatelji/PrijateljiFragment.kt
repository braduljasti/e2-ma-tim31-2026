package com.example.slagalica.ui.prijatelji

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slagalica.R
import com.example.slagalica.adapter.PrijateljiAdapter
import com.example.slagalica.databinding.FragmentPrijateljiBinding
import com.example.slagalica.viewmodel.PrijateljiViewModel
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
    private lateinit var adapter: PrijateljiAdapter

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
        setupRecyclerView()
        setupPretraga()
        observeChanges()
    }

    private fun setupRecyclerView() {
        adapter = PrijateljiAdapter(
            onDodaj = { uid -> viewModel.dodaj(uid) },
            onUkloni = { uid -> viewModel.ukloni(uid) }
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

    private fun observeChanges() {
        viewModel.stavke.observe(viewLifecycleOwner) { adapter.submitList(it) }
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
            setOrientationLocked(false)
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
        _binding = null
    }
}
