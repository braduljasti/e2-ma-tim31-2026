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
            AppNotification(1L, "New chat message", "Player 'MarkoM' sent you a message in the regional chat.", NotificationCategory.CHAT, now - 2 * 60_000L, false),
            AppNotification(2L, "Ranking update!", "You are 3rd on the weekly leaderboard. Only 50 stars to 2nd place!", NotificationCategory.RANK, now - 45 * 60_000L, false),
            AppNotification(3L, "🎁 2nd place reward!", "Congratulations! You finished the weekly cycle in 2nd place. You received 3 tokens!", NotificationCategory.REWARDS, now - 3 * 3600_000L, true),
            AppNotification(4L, "Match invitation", "Player 'JelenaK' invited you to a friendly match. Do you accept?", NotificationCategory.OTHER, now - 24 * 3600_000L, true),
            AppNotification(5L, "League promotion!", "You have been promoted to League 1! You now receive 6 tokens per day.", NotificationCategory.REWARDS, now - 2 * 24 * 3600_000L, true),
            AppNotification(6L, "Daily tokens", "You have received 5 new tokens. Get ready to play!", NotificationCategory.OTHER, now - 8 * 3600_000L, false)
        )
    }
}
