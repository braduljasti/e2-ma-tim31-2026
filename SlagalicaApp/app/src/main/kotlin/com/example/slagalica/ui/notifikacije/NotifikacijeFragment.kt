package com.example.slagalica.ui.notifikacije

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slagalica.adapter.NotifikacijeAdapter
import com.example.slagalica.databinding.FragmentNotifikacijeBinding
import com.example.slagalica.model.NotificationFilter
import com.example.slagalica.viewmodel.NotifikacijeViewModel

class NotifikacijeFragment : Fragment() {

    private var _binding: FragmentNotifikacijeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotifikacijeViewModel
    private lateinit var adapter: NotifikacijeAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotifikacijeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[NotifikacijeViewModel::class.java]
        setupRecyclerView()
        setupFilterChips()
        observeChanges()
    }

    private fun setupRecyclerView() {
        adapter = NotifikacijeAdapter { id -> viewModel.markAsRead(id) }
        binding.rvNotifikacije.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NotifikacijeFragment.adapter
        }
    }

    private fun setupFilterChips() {
        binding.chipSve.setOnClickListener { viewModel.setFilter(NotificationFilter.ALL) }
        binding.chipProcitane.setOnClickListener { viewModel.setFilter(NotificationFilter.READ) }
        binding.chipNeprocitane.setOnClickListener { viewModel.setFilter(NotificationFilter.UNREAD) }
    }

    private fun observeChanges() {
        viewModel.filteredNotifications.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.llPraznoStanje.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.rvNotifikacije.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
