package ru.landilf.memeorganizer.data.indexing

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import ru.landilf.memeorganizer.MainActivity
import ru.landilf.memeorganizer.R

/** Posts one replaceable status notification so a large import does not create notification spam. */
@Singleton
class IndexingNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    @SuppressLint("MissingPermission")
    fun showProgress(completedCount: Int, totalCount: Int) {
        if (!canPostNotifications()) return
        if (completedCount >= totalCount) {
            clearProgress()
            return
        }
        createChannel()
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.indexing_notification_progress_title))
                .setContentText(
                    context.getString(
                        R.string.indexing_notification_progress_message,
                        completedCount,
                        totalCount
                    )
                )
                .setContentIntent(appPendingIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(totalCount, completedCount, false)
                .build()
        )
    }

    fun clearProgress() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun completed() = show(
        title = context.getString(R.string.indexing_notification_completed_title),
        text = context.getString(R.string.indexing_notification_completed_message)
    )

    fun failed() = show(
        title = context.getString(R.string.indexing_notification_failed_title),
        text = context.getString(R.string.indexing_notification_failed_message)
    )

    private fun show(title: String, text: String) {
        if (!canPostNotifications()) return
        createChannel()
        notify(title, text)
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.indexing_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(title: String, text: String) {
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(appPendingIntent())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        )
    }

    private fun appPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val CHANNEL_ID = "media_indexing"
        const val NOTIFICATION_ID = 501
    }
}
