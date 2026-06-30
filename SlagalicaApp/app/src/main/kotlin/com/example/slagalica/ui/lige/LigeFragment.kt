package com.example.slagalica.ui.lige

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slagalica.R
import com.example.slagalica.adapter.LigaAdapter
import com.example.slagalica.databinding.FragmentLigeBinding
import com.example.slagalica.model.LigaPregled
import com.example.slagalica.viewmodel.LigeViewModel

/**
 * Ekran "Lige" (spec 6): trenutna liga + napredak ka sljedećoj, i lista svih
 * liga sa pragovima i dnevnim benefitom tokena.
 */
class LigeFragment : Fragment() {

    private var _binding: FragmentLigeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LigeViewModel
    private lateinit var adapter: LigaAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLigeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[LigeViewModel::class.java]
        adapter = LigaAdapter()
        binding.rvLige.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LigeFragment.adapter
        }
        viewModel.pregled.observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(p: LigaPregled) {
        binding.tvTrenutnaLigaIkona.text = p.trenutnaLiga.emoji
        binding.tvTrenutnaLigaNaziv.text = p.trenutnaLiga.displayName
        binding.tvTrenutneZvezde.text = getString(R.string.fmt_zvezde, p.stars)
        binding.progressLiga.progress = p.progressPercent
        binding.tvDoSledece.text = if (p.sledeciPrag == null) {
            getString(R.string.lbl_max_liga)
        } else {
            getString(R.string.fmt_do_sledece, p.doSledece)
        }
        adapter.submitList(p.redovi)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
