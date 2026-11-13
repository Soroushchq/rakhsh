package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

/**
 * Amber fork: foreground service (dataSync) that keeps the sequential
 * download queue running while the phone is locked / the app is in background.
 */
public class AmberDownloadService extends Service {

    public static final String ACTION_CANCEL = "org.telegram.amber.QUEUE_CANCEL";
    public static final String EXTRA_ACCOUNT = "account";

    private static final int NOTIFICATION_ID = 91231;
    private static final String CHANNEL_ID = "amber_queue";

    private NotificationManager notificationManager;
    private int account = 0;

    public static void start(Context context, int account) {
        try {
            Intent intent = new Intent(context, AmberDownloadService.class);
            intent.putExtra(EXTRA_ACCOUNT, account);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable e) {
            // FGS launch restrictions when the app is in background: keep downloading anyway
            FileLog.e(e);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, AmberDownloadService.class));
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, LocaleController.getString(R.string.AmberQueueNotificationTitle), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        account = intent != null ? intent.getIntExtra(EXTRA_ACCOUNT, UserConfig.selectedAccount) : UserConfig.selectedAccount;
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(0, 0, "", 0, 0));
            } catch (Throwable ignore) {
            }
            DownloadQueueController.getInstance(account).cancelAll();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification(0, 0, "", 0, 0));
        DownloadQueueController.getInstance(account).attachService(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DownloadQueueController.getInstance(account).detachService(this);
        super.onDestroy();
    }

    /** Called by the controller whenever queue progress changes. */
    public void update(int finished, int total, String name, long loadedBytes, long totalBytes) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(finished, total, name, loadedBytes, totalBytes));
        } catch (Throwable ignore) {
        }
    }

    private Notification buildNotification(int finished, int total, String name, long loadedBytes, long totalBytes) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent contentIntent = null;
        if (launchIntent != null) {
            contentIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE);
        }
        Intent cancelIntent = new Intent(this, AmberDownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        cancelIntent.putExtra(EXTRA_ACCOUNT, account);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.notification)
                .setColor(0xFFF97316)
                .setContentTitle(LocaleController.getString(R.string.AmberQueueNotificationTitle))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(null, LocaleController.getString(R.string.AmberQueueCancelAll), cancelPendingIntent).build());

        StringBuilder text = new StringBuilder();
        if (total > 0) {
            text.append(LocaleController.formatString("AmberQueueProgressLine", R.string.AmberQueueProgressLine, finished, total));
        }
        if (!TextUtils.isEmpty(name)) {
            if (text.length() > 0) {
                text.append(" • ");
            }
            text.append(name);
        }
        boolean indeterminate = totalBytes <= 0;
        if (!indeterminate && totalBytes > 0 && loadedBytes > 0) {
            if (text.length() > 0) {
                text.append(" • ");
            }
            text.append(AndroidUtilities.formatFileSize(loadedBytes)).append(" / ").append(AndroidUtilities.formatFileSize(totalBytes));
        }
        builder.setContentText(text.length() > 0 ? text.toString() : null);
        builder.setProgress(100, indeterminate ? 0 : (int) (loadedBytes * 100 / Math.max(1, totalBytes)), indeterminate);
        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
