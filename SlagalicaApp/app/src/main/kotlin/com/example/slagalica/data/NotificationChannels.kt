package com.example.slagalica.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.slagalica.model.NotificationCategory

/**
 * 11.a - posebni kanali za sistemske notifikacije:
 *   i.   ČAT
 *   ii.  RANGIRANJE
 *   iii. NAGRADE
 *   iv.  OSTALO
 *
 * Kanale kreiramo jednom (najbolje u Application/MainActivity onCreate).
 */
object NotificationChannels {

    fun channelId(category: NotificationCategory): String = when (category) {
        NotificationCategory.CHAT -> "channel_chat"
        NotificationCategory.RANK -> "channel_rang"
        NotificationCategory.REWARDS -> "channel_nagrade"
        NotificationCategory.OTHER -> "channel_ostalo"
    }

    private fun channelName(category: NotificationCategory): String = when (category) {
        NotificationCategory.CHAT -> "Obavještenja u četu"
        NotificationCategory.RANK -> "Obavještenja o rangiranju"
        NotificationCategory.REWARDS -> "Obavještenja o nagradama"
        NotificationCategory.OTHER -> "Ostalo"
    }

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationCategory.values().forEach { category ->
            val channel = NotificationChannel(
                channelId(category),
                channelName(category),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }
}
