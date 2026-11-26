package com.songplus.cluster.media

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Android only lets an app read other apps' media sessions
 * ([android.media.session.MediaSessionManager.getActiveSessions]) if the app has an enabled notification listener.
 * The user grants that once under Settings > Notification access. The app never reads notification content.
 */
class MediaListenerService : NotificationListenerService()
