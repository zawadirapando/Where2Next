package com.example.where2next

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val eventId = remoteMessage.data["eventId"]
        val title = remoteMessage.notification?.title ?: "New Event"
        val body = remoteMessage.notification?.body ?: ""

        // Build intent that carries the eventId to NavigationActivity
        val intent = Intent(this, NavigationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_EVENT_ID", eventId)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(this, "EVENT_CHANNEL_ID")
            .setSmallIcon(R.drawable.ic_notif_w)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)  // <- tap opens the activity
            .setAutoCancel(true)

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
    }
}