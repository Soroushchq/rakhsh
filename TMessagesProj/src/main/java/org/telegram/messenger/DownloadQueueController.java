package org.telegram.messenger;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * Amber fork: strictly sequential download queue.
 *
 * The user selects multiple files in a chat (multi-select -> "sequential download")
 * and they are downloaded ONE AT A TIME, in order, backed by a foreground
 * dataSync service so the queue keeps running while the screen is locked.
 * The queue is persisted to SharedPreferences (the TLRPC.Document itself is
 * serialized) so it survives process death and resumes on next app start.
 */
public class DownloadQueueController implements NotificationCenter.NotificationCenterDelegate {

    public static final int STATE_PENDING = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_FAILED = 3;

    private static volatile DownloadQueueController[] Instance = new DownloadQueueController[UserConfig.MAX_ACCOUNT_COUNT];

    public static DownloadQueueController getInstance(int num) {
        DownloadQueueController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (DownloadQueueController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new DownloadQueueController(num);
                }
            }
        }
        return localInstance;
    }

    public static class QueueItem {
        public long dialogId;
        public int messageId;
        public String fileName;
        public String displayName;
        public long size;
        public long loadedBytes;
        public int state = STATE_PENDING;
        String documentBase64;

        QueueItem(long dialogId, int messageId, String fileName, String displayName, long size, TLRPC.Document document) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.fileName = fileName;
            this.displayName = displayName;
            this.size = size;
            this.documentBase64 = documentToString(document);
        }
    }

    private final int currentAccount;
    private final ArrayList<QueueItem> queue = new ArrayList<>();
    private QueueItem current;
    private AmberDownloadService attachedService;
    private long lastNotifyTime;

    private DownloadQueueController(int account) {
        currentAccount = account;
        loadQueue();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    /** Called once on app init: loads persisted queues and resumes any pending downloads. */
    public static void resumeAllQueues() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            DownloadQueueController controller = getInstance(a);
            if (!controller.queue.isEmpty()) {
                controller.startNextIfNeeded();
            }
        }
    }

    /**
     * Adds the given message objects (documents/videos/music) to the queue.
     * Returns the number of items actually added. Must be called on the UI thread.
     */
    public int enqueue(ArrayList<MessageObject> messageObjects) {
        int added = 0;
        for (int a = 0; a < messageObjects.size(); a++) {
            MessageObject messageObject = messageObjects.get(a);
            TLRPC.Document document = messageObject.getDocument();
            if (document == null) {
                continue;
            }
            String fileName = FileLoader.getAttachFileName(document);
            if (TextUtils.isEmpty(fileName)) {
                continue;
            }
            if (hasActiveItemForFileName(fileName)) {
                continue;
            }
            String displayName = FileLoader.getDocumentFileName(document);
            if (TextUtils.isEmpty(displayName)) {
                displayName = fileName;
            }
            queue.add(new QueueItem(messageObject.getDialogId(), messageObject.getId(), fileName, displayName, document.size, document));
            added++;
        }
        if (added > 0) {
            persist();
            startNextIfNeeded();
        }
        return added;
    }

    /** Cancels the whole queue (current download included) and stops the service. */
    public void cancelAll() {
        if (current != null) {
            TLRPC.Document document = stringToDocument(current.documentBase64);
            if (document != null) {
                FileLoader.getInstance(currentAccount).cancelLoadFile(document);
            }
        }
        queue.clear();
        current = null;
        persist();
        AmberDownloadService.stop(ApplicationLoader.applicationContext);
        updateNotification(true);
    }

    /** True if a PENDING/DOWNLOADING item with this name exists (terminal items may be re-queued). */
    private boolean hasActiveItemForFileName(String fileName) {
        if (current != null && fileName.equals(current.fileName)) {
            return true;
        }
        for (int a = 0; a < queue.size(); a++) {
            QueueItem item = queue.get(a);
            if ((item.state == STATE_PENDING || item.state == STATE_DOWNLOADING) && fileName.equals(item.fileName)) {
                return true;
            }
        }
        return false;
    }

    private void startNextIfNeeded() {
        if (current != null) {
            updateNotification(true);
            return;
        }
        QueueItem next = null;
        for (int a = 0; a < queue.size(); a++) {
            QueueItem item = queue.get(a);
            if (item.state == STATE_PENDING) {
                next = item;
                break;
            }
        }
        if (next == null) {
            for (int a = queue.size() - 1; a >= 0; a--) {
                QueueItem item = queue.get(a);
                if (item.state == STATE_DONE || item.state == STATE_FAILED) {
                    queue.remove(a);
                }
            }
            persist();
            AmberDownloadService.stop(ApplicationLoader.applicationContext);
            updateNotification(true);
            return;
        }
        current = next;
        current.state = STATE_DOWNLOADING;
        current.loadedBytes = 0;
        persist();
        AmberDownloadService.start(ApplicationLoader.applicationContext, currentAccount);

        TLRPC.Document document = stringToDocument(current.documentBase64);
        if (document == null) {
            current.state = STATE_FAILED;
            persist();
            current = null;
            AndroidUtilities.runOnUIThread(this::startNextIfNeeded);
            return;
        }
        File cached = FileLoader.getInstance(currentAccount).getPathToAttach(document);
        if (cached != null && cached.exists()) {
            finishCurrentWithFile(cached);
            return;
        }
        FileLoader.getInstance(currentAccount).loadFile(document, null, FileLoader.PRIORITY_NORMAL, 0);
        updateNotification(true);
    }

    private void finishCurrentWithFile(File finalFile) {
        final QueueItem item = current;
        updateNotification(true);
        Utilities.globalQueue.postRunnable(() -> {
            final boolean ok = copyToPublicDownloadsSafely(item, finalFile);
            AndroidUtilities.runOnUIThread(() -> {
                if (current == item) {
                    item.state = ok ? STATE_DONE : STATE_FAILED;
                    persist();
                    current = null;
                    startNextIfNeeded();
                }
            });
        });
    }

    private boolean copyToPublicDownloadsSafely(QueueItem item, File finalFile) {
        try {
            return copyToPublicDownloads(item, finalFile);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Copies the downloaded file into public Downloads/Telegram and registers it with the system. */
    private boolean copyToPublicDownloads(QueueItem item, File sourceFile) throws Throwable {
        if (sourceFile == null || !sourceFile.exists()) {
            return false;
        }
        String name = !TextUtils.isEmpty(item.displayName) ? item.displayName : item.fileName;
        if (Build.VERSION.SDK_INT >= 29) {
            return copyToPublicDownloadsQ(sourceFile, name);
        }
        return copyToPublicDownloadsLegacy(name, sourceFile);
    }

    private String mimeForName(String name) {
        try {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            int idx = name.lastIndexOf('.');
            String extension = idx >= 0 ? name.substring(idx + 1) : "";
            String mime = mimeTypeMap.getMimeTypeFromExtension(extension.toLowerCase());
            if (TextUtils.isEmpty(mime)) {
                return "application/octet-stream";
            }
            return mime;
        } catch (Throwable e) {
            return "application/octet-stream";
        }
    }

    /** Scoped storage path for Android 10+: goes through MediaStore.Downloads. */
    private boolean copyToPublicDownloadsQ(File sourceFile, String name) throws Throwable {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeForName(name));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Telegram");
        Uri uri = ApplicationLoader.applicationContext.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return false;
        }
        try (FileInputStream inputStream = new FileInputStream(sourceFile); OutputStream outputStream = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                return false;
            }
            byte[] buffer = new byte[1024 * 512];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
        return true;
    }

    /** Legacy direct-file path for Android 9 and below. */
    private boolean copyToPublicDownloadsLegacy(String name, File sourceFile) throws Throwable {
        File destDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Telegram");
        destDir.mkdirs();
        File destFile = new File(destDir, name);
        if (destFile.exists()) {
            destFile = new File(destDir, System.currentTimeMillis() + "_" + name);
        }
        try (FileInputStream inputStream = new FileInputStream(sourceFile); FileOutputStream outputStream = new FileOutputStream(destFile)) {
            FileChannel source = inputStream.getChannel();
            FileChannel destination = outputStream.getChannel();
            long size = source.size();
            for (long offset = 0; offset < size; offset += destination.transferFrom(source, offset, Math.min(4 * 1024 * 1024, size - offset))) {
                // sequential chunked copy
            }
            destination.force(true);
        }
        try {
            DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadManager.addCompletedDownload(destFile.getName(), destFile.getName(), false, mimeForName(name), destFile.getAbsolutePath(), destFile.length(), true);
        } catch (Throwable ignore) {
            // best-effort registration only
        }
        return true;
    }

    private void updateNotification(boolean force) {
        AmberDownloadService service = attachedService;
        if (service == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastNotifyTime < 400) {
            return;
        }
        lastNotifyTime = now;

        int total = queue.size();
        int finished = 0;
        for (int a = 0; a < queue.size(); a++) {
            QueueItem item = queue.get(a);
            if (item.state == STATE_DONE || item.state == STATE_FAILED) {
                finished++;
            }
        }
        String name = current != null && !TextUtils.isEmpty(current.displayName) ? current.displayName : "";
        long loaded = current != null ? current.loadedBytes : 0;
        long size = current != null ? current.size : 0;
        service.update(finished, total, name, loaded, size);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) {
            return;
        }
        if (id == NotificationCenter.fileLoadProgressChanged) {
            if (current != null && current.fileName.equals(args[0])) {
                current.loadedBytes = (Long) args[1];
                updateNotification(false);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            if (current != null && current.fileName.equals(args[0])) {
                finishCurrentWithFile((File) args[1]);
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            if (current != null && current.fileName.equals(args[0])) {
                current.state = STATE_FAILED;
                persist();
                current = null;
                updateNotification(true);
                startNextIfNeeded();
            }
        }
    }

    private String persistName() {
        return "amber_queue_" + currentAccount;
    }

    private void persist() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(persistName(), Context.MODE_PRIVATE);
            org.json.JSONArray array = new org.json.JSONArray();
            ArrayList<QueueItem> all = new ArrayList<>(queue);
            if (current != null) {
                all.add(current);
            }
            for (int a = 0; a < all.size(); a++) {
                QueueItem item = all.get(a);
                org.json.JSONObject object = new org.json.JSONObject();
                object.put("dialogId", item.dialogId);
                object.put("msgId", item.messageId);
                object.put("fileName", item.fileName);
                object.put("displayName", item.displayName);
                object.put("size", item.size);
                object.put("loaded", item.loadedBytes);
                object.put("state", item.state);
                object.put("doc", item.documentBase64);
                array.put(object);
            }
            preferences.edit().putString("queue", array.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void loadQueue() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(persistName(), Context.MODE_PRIVATE);
            String raw = preferences.getString("queue", null);
            if (TextUtils.isEmpty(raw)) {
                return;
            }
            org.json.JSONArray array = new org.json.JSONArray(raw);
            queue.clear();
            current = null;
            for (int a = 0; a < array.length(); a++) {
                org.json.JSONObject object = array.getJSONObject(a);
                QueueItem item = new QueueItem(
                        object.getLong("dialogId"),
                        object.getInt("msgId"),
                        object.getString("fileName"),
                        object.getString("displayName"),
                        object.getLong("size"),
                        stringToDocument(object.getString("doc"))
                );
                item.documentBase64 = object.getString("doc");
                item.state = object.optInt("state", STATE_PENDING);
                item.loadedBytes = object.optLong("loaded", 0);
                if (item.state == STATE_DOWNLOADING) {
                    // interrupted by process death: try again from the beginning
                    item.state = STATE_PENDING;
                    item.loadedBytes = 0;
                }
                if (item.state == STATE_PENDING) {
                    queue.add(item);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            queue.clear();
        }
    }

    private static String documentToString(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        try {
            SerializedData serializedData = new SerializedData();
            document.serializeToStream(serializedData);
            return Base64.encodeToString(serializedData.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static TLRPC.Document stringToDocument(String base64) {
        if (TextUtils.isEmpty(base64)) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            SerializedData serializedData = new SerializedData(bytes);
            return TLRPC.Document.TLdeserialize(serializedData, serializedData.readInt32(false), false);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    void attachService(AmberDownloadService service) {
        attachedService = service;
        updateNotification(true);
    }

    void detachService(AmberDownloadService service) {
        if (attachedService == service) {
            attachedService = null;
        }
    }
}
