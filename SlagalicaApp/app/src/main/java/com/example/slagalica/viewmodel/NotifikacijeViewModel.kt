package com.example.slagalica.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationCategory
import com.example.slagalica.model.NotificationFilter

class NotifikacijeViewModel : ViewModel() {

    private val allNotifications = MutableLiveData<List<AppNotification>>(sampleNotifications())

    private val _filteredNotifications = MutableLiveData<List<AppNotification>>()
    val filteredNotifications: LiveData<List<AppNotification>> = _filteredNotifications

    private var currentFilter = NotificationFilter.ALL

    init { updateFilter() }

    fun setFilter(filter: NotificationFilter) {
        currentFilter = filter
        updateFilter()
    }

    fun markAsRead(id: Long) {
        val list = allNotifications.value?.toMutableList() ?: return
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(read = true)
            allNotifications.value = list
            updateFilter()
        }
    }

    fun markAllAsRead() {
        allNotifications.value = allNotifications.value?.map { it.copy(read = true) }
        updateFilter()
    }

    private fun updateFilter() {
        val all = allNotifications.value ?: emptyList()
        _filteredNotifications.value = when (currentFilter) {
            NotificationFilter.ALL -> all
            NotificationFilter.READ -> all.filter { it.read }
            NotificationFilter.UNREAD -> all.filter { !it.read }
        }
    }

    private fun sampleNotifications(): List<AppNotification> {
        val now = System.currentTimeMillis()
        return listOf(
            AppNotification(1L, "Nova poruka u četu", "Igrač 'MarkoM' vam je poslao poruku u regionalnom četu.", NotificationCategory.CHAT, now - 2 * 60_000L, false),
            AppNotification(2L, "Napredak na rang listi!", "Nalazite se na 3. mjestu nedeljne rang liste. Samo 50 zvjezdica do 2. mjesta!", NotificationCategory.RANK, now - 45 * 60_000L, false),
            AppNotification(3L, "🎁 Nagrada za 2. mjesto!", "Čestitamo! Završili ste nedeljni ciklus na 2. mjestu. Dobili ste 3 tokena!", NotificationCategory.REWARDS, now - 3 * 3600_000L, true),
            AppNotification(4L, "Poziv za partiju", "Igrač 'JelenaK' vas je pozvala na prijateljsku partiju. Prihvatate li?", NotificationCategory.OTHER, now - 24 * 3600_000L, true),
            AppNotification(5L, "Napredak u ligi!", "Unaprijeđeni ste u Ligu 1! Sada dobijate 6 tokena dnevno.", NotificationCategory.REWARDS, now - 2 * 24 * 3600_000L, true),
            AppNotification(6L, "Dnevni tokeni", "Stiglo je 5 novih tokena. Spremi se za igru!", NotificationCategory.OTHER, now - 8 * 3600_000L, false)
        )
    }
}
