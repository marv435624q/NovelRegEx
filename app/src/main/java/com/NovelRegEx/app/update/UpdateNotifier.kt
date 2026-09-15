package com.NovelRegEx.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.NovelRegEx.app.R
import com.NovelRegEx.app.activity.SettingsActivity
import com.NovelRegEx.app.receiver.UpdateDownloadReceiver
import java.io.File

object UpdateNotifier {
  private const val CHANNEL_ID = "novelregex_updates"
  private const val NOTIFY_AVAILABLE = 2001
  private const val NOTIFY_READY = 2002
  private const val NOTIFY_FAILED = 2003

  fun initChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, context.getString(R.string.noti_channel_update), NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = context.getString(R.string.noti_channel_update_desc)
      },
    )
  }

  fun showUpdateAvailable(context: Context, info: UpdateInfo) {
    initChannel(context)
    val actionIntent =
      Intent(context, UpdateDownloadReceiver::class.java).apply {
        putExtra(UpdateDownloadReceiver.EXTRA_DOWNLOAD_URL, info.downloadUrl)
        putExtra(UpdateDownloadReceiver.EXTRA_VERSION, info.version)
      }
    val action = PendingIntent.getBroadcast(context, NOTIFY_AVAILABLE, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val open = PendingIntent.getActivity(
      context,
      0,
      Intent(context, SettingsActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    notify(
      context,
      NOTIFY_AVAILABLE,
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_novelregex)
        .setContentTitle(context.getString(R.string.noti_update_available_title))
        .setContentText(context.getString(R.string.noti_update_available_text, info.version))
        .setContentIntent(open)
        .setAutoCancel(true)
        .addAction(0, context.getString(R.string.noti_action_download), action),
    )
  }

  fun showInstallReady(context: Context, apkFile: File) {
    initChannel(context)
    val intent = UpdateChecker.createInstallIntent(context, apkFile)
    val pending = PendingIntent.getActivity(context, NOTIFY_READY, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    notify(
      context,
      NOTIFY_READY,
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_novelregex)
        .setContentTitle(context.getString(R.string.noti_update_ready_title))
        .setContentText(context.getString(R.string.noti_update_ready_text))
        .setContentIntent(pending)
        .setAutoCancel(true),
    )
  }

  fun showDownloadFailed(context: Context, downloadUrl: String, version: String) {
    initChannel(context)
    val retryIntent =
      Intent(context, UpdateDownloadReceiver::class.java).apply {
        putExtra(UpdateDownloadReceiver.EXTRA_DOWNLOAD_URL, downloadUrl)
        putExtra(UpdateDownloadReceiver.EXTRA_VERSION, version)
      }
    val retry = PendingIntent.getBroadcast(context, NOTIFY_FAILED, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    notify(
      context,
      NOTIFY_FAILED,
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_novelregex)
        .setContentTitle(context.getString(R.string.noti_download_failed_title))
        .setContentText(context.getString(R.string.noti_download_failed_text))
        .setAutoCancel(true)
        .addAction(0, context.getString(R.string.noti_action_retry), retry),
    )
  }

  private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
    if (
      android.os.Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    NotificationManagerCompat.from(context).notify(id, builder.build())
  }
}
