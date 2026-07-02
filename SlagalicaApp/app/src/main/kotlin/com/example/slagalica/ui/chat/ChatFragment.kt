package com.example.slagalica.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slagalica.adapter.ChatAdapter
import com.example.slagalica.databinding.FragmentChatBinding
import com.example.slagalica.viewmodel.ChatViewModel

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        adapter = ChatAdapter { viewModel.uid }
        binding.rvChatPoruke.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatPoruke.adapter = adapter

        binding.btnChatPosalji.setOnClickListener {
            val tekst = binding.etChatPoruka.text?.toString().orEmpty()
            if (tekst.isNotBlank()) {
                viewModel.posalji(tekst)
                binding.etChatPoruka.text?.clear()
            }
        }

        viewModel.region.observe(viewLifecycleOwner) { region ->
            binding.tvChatRegion.text = if (region.isNullOrBlank()) {
                "Čet"
            } else {
                "Čet — Region: $region"
            }
        }

        viewModel.poruke.observe(viewLifecycleOwner) { lista ->
            binding.tvChatPrazno.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(lista) {
                if (lista.isNotEmpty()) binding.rvChatPoruke.scrollToPosition(lista.size - 1)
            }
        }

        viewModel.pokreni()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
