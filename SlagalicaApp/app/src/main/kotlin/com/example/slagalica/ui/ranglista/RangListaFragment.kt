package com.example.slagalica.ui.ranglista

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slagalica.R
import com.example.slagalica.adapter.RangListaAdapter
import com.example.slagalica.databinding.FragmentRangListaBinding
import com.example.slagalica.model.RangCiklus
import com.example.slagalica.viewmodel.RangListaViewModel
import com.google.android.material.tabs.TabLayout

class RangListaFragment : Fragment() {

    private var _binding: FragmentRangListaBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RangListaViewModel
    private lateinit var adapter: RangListaAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRangListaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[RangListaViewModel::class.java]

        adapter = RangListaAdapter { viewModel.uid }
        binding.rvRangLista.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRangLista.adapter = adapter

        binding.tabRangCiklus.addTab(binding.tabRangCiklus.newTab().setText(getString(R.string.tab_nedeljno)))
        binding.tabRangCiklus.addTab(binding.tabRangCiklus.newTab().setText(getString(R.string.tab_mesecno)))

        binding.tabRangCiklus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.promeniCiklus(if (tab.position == 0) RangCiklus.NEDELJNI else RangCiklus.MESECNI)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewModel.opsegDatuma.observe(viewLifecycleOwner) { binding.tvRangOpsegDatuma.text = it }
        viewModel.ucitavanje.observe(viewLifecycleOwner) { binding.progressRangLista.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.stavke.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvRangPrazno.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.pokreni()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
