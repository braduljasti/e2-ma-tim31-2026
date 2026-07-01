package com.example.slagalica.ui.regioni

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slagalica.R
import com.example.slagalica.adapter.RegionRangAdapter
import com.example.slagalica.data.Regioni
import com.example.slagalica.databinding.FragmentRegioniBinding
import com.example.slagalica.model.IgracTacka
import com.example.slagalica.model.RegionStatistika
import com.example.slagalica.viewmodel.RegioniViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Ekran regiona (spec 5.a, 5.b): mapa Srbije (OpenStreetMap / osmdroid) sa
 * tačkom svakog igrača u njegovom regionu, i mjesečna rang lista po regionima.
 */
class RegioniFragment : Fragment() {

    private var _binding: FragmentRegioniBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RegioniViewModel
    private lateinit var adapter: RegionRangAdapter
    private val map get() = binding.mapRegioni

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // osmdroid zahtijeva konfiguraciju (user-agent) prije inflate-a MapView-a
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentRegioniBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupRang()

        viewModel = ViewModelProvider(this)[RegioniViewModel::class.java]
        viewModel.tacke.observe(viewLifecycleOwner) { prikaziTacke(it) }
        viewModel.rang.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.statistika.observe(viewLifecycleOwner) { stat ->
            if (stat != null) { prikaziStatistiku(stat); viewModel.consumeStatistika() }
        }
    }

    /** Dijalog statistike regiona (spec 5.d): registrovani, aktivni, broj 1./2./3. mjesta. */
    private fun prikaziStatistiku(s: RegionStatistika) {
        val poruka = getString(
            R.string.fmt_region_statistika,
            s.registrovani, s.aktivni, s.prvaMjesta, s.drugaMjesta, s.trecaMjesta
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${s.emoji} ${s.naziv}")
            .setMessage(poruka)
            .setPositiveButton(R.string.mp_zatvori, null)
            .show()
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(Regioni.POCETNI_ZOOM)
        map.controller.setCenter(GeoPoint(Regioni.SRBIJA_LAT, Regioni.SRBIJA_LNG))
    }

    private fun setupRang() {
        adapter = RegionRangAdapter { naziv -> viewModel.ucitajStatistiku(naziv) }
        binding.rvRegionRang.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RegioniFragment.adapter
        }
    }

    private fun prikaziTacke(tacke: List<IgracTacka>) {
        map.overlays.clear()
        tacke.forEach { t ->
            val marker = Marker(map).apply {
                position = GeoPoint(t.lat, t.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = if (t.jaSam) getString(R.string.lbl_ja_marker, t.username) else t.username
                subDescription = t.regionNaziv
            }
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    // osmdroid traži ručno prosljeđivanje lifecycle poziva
    override fun onResume() {
        super.onResume()
        if (_binding != null) map.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (_binding != null) map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
