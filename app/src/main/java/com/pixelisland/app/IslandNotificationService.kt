package com.pixelisland.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        val isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                notification.category == Notification.CATEGORY_TRANSPORT

        if (isMedia && title.isNotBlank()) {
            val mediaInfo = if (text.isNotBlank()) "$title - $text" else title
            IslandStateBus.setMedia(mediaInfo.trim(' ', '-'))
        } else if (!sbn.isOngoing && (title.isNotBlank() || text.isNotBlank())) {
            val notifText = when {
                title.isNotBlank() && text.isNotBlank() -> "$title: $text"
                title.isNotBlank() -> title
                else -> text
            }
            IslandStateBus.setNotification(notifText)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        val isMedia = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
                notification.category == Notification.CATEGORY_TRANSPORT

        if (isMedia) {
            IslandStateBus.setMedia(null)
        }
    }
}
