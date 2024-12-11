package com.hhst.youtubelite.Downloader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.IconCompat;

import com.hhst.youtubelite.R;

import java.io.File;

public class DownloadNotification {

    Context context;
    private final NotificationManager notificationManager;
    private static final String CHANNEL_ID = "download_channel";
    private NotificationCompat.Builder builder = null;
    private final int notificationId;


    public DownloadNotification(Context context, int notificationId) {
        this.context = context;
        this.notificationId = notificationId;
        this.notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Download Channel",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Channel for download notifications");
        notificationManager.createNotificationChannel(channel);
    }

    public void showNotification(String content, int progress) {
        Intent cancelIntent = new Intent(context, DownloadService.class);
        cancelIntent.setAction("CANCEL_DOWNLOAD");
        cancelIntent.putExtra("taskId", notificationId);
        PendingIntent cancelPendingIntent = PendingIntent.getService(
                context, notificationId, cancelIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Intent retryIntent = new Intent(context, DownloadService.class);
        retryIntent.setAction("RETRY_DOWNLOAD");
        retryIntent.putExtra("taskId", notificationId);
        PendingIntent retryPendingIntent = PendingIntent.getService(
                context, notificationId, retryIntent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action cancelAction = new NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, R.drawable.ic_cancel),
                context.getString(R.string.cancel), 
                cancelPendingIntent
        ).build();

        NotificationCompat.Action retryAction = new NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, R.drawable.ic_retry),
                context.getString(R.string.retry),
                retryPendingIntent
        ).build();


        builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.downloading))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(cancelAction)
                .addAction(retryAction)
                .setProgress(100, progress, false);

        notificationManager.notify(notificationId, builder.build());
    }

    public void updateProgress(int progress) {
        if (builder != null) {
            builder.setProgress(100, progress, false)
                    .setSubText(context.getString(R.string.progress) + progress + "%");
            notificationManager.notify(notificationId, builder.build());
        }
    }

    public void completeDownload(String content, File file, String mimeType) {
        // refs: https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file),
                mimeType
        );
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);


        Intent deleteIntent = new Intent(context, DownloadService.class);
        deleteIntent.setAction("DELETE_DOWNLOAD");
        deleteIntent.putExtra("taskId", notificationId);
        PendingIntent deletePendingIntent = PendingIntent.getService(
                context, notificationId, deleteIntent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action deleteAction = new NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, R.drawable.ic_delete),
                context.getString(R.string.delete),
                deletePendingIntent
        ).build();


        if (builder != null) {
            builder.setContentTitle(context.getString(R.string.download_complete))
                    .setOngoing(false)
                    .setContentText(content)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setContentIntent(pendingIntent)
                    .clearActions()
                    .setAutoCancel(true)
                    .addAction(deleteAction)
                    .setProgress(100, 100, false);
            notificationManager.notify(notificationId, builder.build());
        }
    }

    public void cancelDownload(String content) {
        if (builder != null) {
            Intent retryIntent = new Intent(context, DownloadService.class);
            retryIntent.setAction("RETRY_DOWNLOAD");
            retryIntent.putExtra("taskId", notificationId);
            PendingIntent retryPendingIntent = PendingIntent.getService(
                    context, notificationId, retryIntent, PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Action retryAction = new NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_retry),
                    context.getString(R.string.retry),
                    retryPendingIntent
            ).build();

            builder.setContentTitle(context.getString(R.string.download_canceled) + content)
                    .setOngoing(false)
                    .clearActions()
                    .addAction(retryAction);
            notificationManager.notify(notificationId, builder.build());
        }

    }

    // this will call during the video-audio merge after the download
    public void afterDownload() {
        if (builder != null) {
            builder.setContentTitle(context.getString(R.string.download_finished_merging))
                    .setProgress(100, 100, true);

            notificationManager.notify(notificationId, builder.build());

        }

    }

    public void clearDownload() {
        notificationManager.cancel(notificationId);
    }

}
