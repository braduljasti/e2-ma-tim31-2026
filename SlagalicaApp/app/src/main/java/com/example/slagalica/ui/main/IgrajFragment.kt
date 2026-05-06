package com.example.slagalica.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.slagalica.databinding.FragmentIgrajBinding
import com.example.slagalica.viewmodel.IgrajViewModel

class IgrajFragment : Fragment() {

    private var _binding: FragmentIgrajBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: IgrajViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIgrajBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[IgrajViewModel::class.java]
        observeChanges()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnIgraj.setOnClickListener {
            binding.cardIgraj.visibility = View.GONE
            binding.cardCekanje.visibility = View.VISIBLE
            viewModel.searchOpponent()
        }

        binding.btnOtkaziCekanje.setOnClickListener {
            binding.cardCekanje.visibility = View.GONE
            binding.cardIgraj.visibility = View.VISIBLE
            viewModel.cancelSearch()
        }
    }

    private fun observeChanges() {
        viewModel.tokens.observe(viewLifecycleOwner) { count ->
            binding.tvTokeniMain.text = count.toString()
        }
        viewModel.stars.observe(viewLifecycleOwner) { count ->
            binding.tvZvjezdiceMain.text = count.toString()
        }
        viewModel.league.observe(viewLifecycleOwner) { name ->
            binding.tvLigaMain.text = name
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
