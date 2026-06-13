package com.example.slagalica.data

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.slagalica.R
import com.example.slagalica.model.AppNotification
import com.example.slagalica.model.NotificationCategory
import com.example.slagalica.ui.main.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SlagalicaMessagingService : FirebaseMessagingService() {

    private val notifRepo = NotifikacijeRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        val uid = FirebaseProvider.currentUid ?: return
        FirebaseProvider.db.collection(FirestoreCollections.USERS)
            .document(uid).update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"] ?: message.notification?.title ?: "Slagalica"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        val category = runCatching {
            NotificationCategory.valueOf(message.data["category"] ?: NotificationCategory.OTHER.name)
        }.getOrDefault(NotificationCategory.OTHER)

        showSystemNotification(title, body, category)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                notifRepo.add(
                    AppNotification(
                        id = "",
                        title = title,
                        content = body,
                        category = category,
                        timestampMs = System.currentTimeMillis(),
                        read = false
                    )
                )
            }
        }
    }

    private fun showSystemNotification(title: String, body: String, category: NotificationCategory) {
        NotificationChannels.createAll(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.channelId(category))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
