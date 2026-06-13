package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.slagalica.data.NotifikacijeRepository
import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationFilter
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * Notifikacije se ucitavaju iz Firestore baze u realnom vremenu (zahtjev 11.b).
 * - 11.c: oznacavanje kao procitano upisuje se u bazu
 * - 11.d: filtriranje na sve/procitane/neprocitane radi se lokalno nad ucitanom listom
 */
class NotifikacijeViewModel(
    private val repo: NotifikacijeRepository = NotifikacijeRepository()
) : ViewModel() {

    private val allNotifications = MutableLiveData<List<AppNotification>>(emptyList())

    private val _filteredNotifications = MutableLiveData<List<AppNotification>>()
    val filteredNotifications: LiveData<List<AppNotification>> = _filteredNotifications

    private var currentFilter = NotificationFilter.ALL
    private var listener: ListenerRegistration? = null

    init {
        // Ako korisnik nema notifikacija, ubacujemo par primjera, pa pocinjemo da osluskujemo
        viewModelScope.launch {
            runCatching { repo.seedIfEmpty() }
            startListening()
        }
    }

    private fun startListening() {
        listener?.remove()
        listener = repo.listen { list ->
            allNotifications.postValue(list)
            updateFilter(list)
        }
    }

    fun setFilter(filter: NotificationFilter) {
        currentFilter = filter
        updateFilter(allNotifications.value ?: emptyList())
    }

    fun markAsRead(id: String) {
        viewModelScope.launch { runCatching { repo.markAsRead(id) } }
    }

    fun markAllAsRead() {
        val ids = allNotifications.value?.filter { !it.read }?.map { it.id } ?: return
        if (ids.isEmpty()) return
        viewModelScope.launch { runCatching { repo.markAllAsRead(ids) } }
    }

    private fun updateFilter(all: List<AppNotification>) {
        _filteredNotifications.postValue(
            when (currentFilter) {
                NotificationFilter.ALL -> all
                NotificationFilter.READ -> all.filter { it.read }
                NotificationFilter.UNREAD -> all.filter { !it.read }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
