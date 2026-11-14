package org.telegram.messenger;

/*
 * AmberGram fork: ghost mode.
 * While enabled, chats that are currently open are NOT marked as read on the
 * server until the user actually leaves the chat, and typing indicators are
 * never sent. Reads triggered while the chat is open are stashed here and
 * replayed once ChatActivity pauses (ghost off or chat closed).
 */

import android.content.SharedPreferences;

import java.util.HashMap;

public class AmberGhost {

    private static final Object lock = new Object();
    private static Boolean enabled;
    private static volatile long openDialog = 0;
    private static final HashMap<Long, PendingRead> pendingReads = new HashMap<>();

    public static class PendingRead {
        public long threadId;
        public int maxPositiveId;
        public int maxNegativeId;
        public int maxDate;
        public int scheduledCount;
    }

    public static boolean isEnabled() {
        Boolean value = enabled;
        if (value == null) {
            synchronized (lock) {
                if (enabled == null) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    enabled = preferences.getBoolean("amberGhostMode", false);
                }
                value = enabled;
            }
        }
        return value;
    }

    public static void setEnabled(boolean value) {
        synchronized (lock) {
            enabled = value;
            MessagesController.getGlobalMainSettings().edit().putBoolean("amberGhostMode", value).apply();
        }
    }

    public static void chatOpened(long dialogId) {
        openDialog = dialogId;
    }

    public static void chatClosed(long dialogId) {
        if (openDialog == dialogId) {
            openDialog = 0;
        }
    }

    public static boolean shouldDeferRead(long dialogId) {
        return openDialog != 0 && openDialog == dialogId && isEnabled();
    }

    public static void deferRead(long dialogId, int maxPositiveId, int maxNegativeId, int maxDate, long threadId, int scheduledCount) {
        synchronized (lock) {
            PendingRead pending = pendingReads.get(dialogId);
            if (pending == null) {
                pending = new PendingRead();
                pendingReads.put(dialogId, pending);
            }
            pending.maxPositiveId = maxPositiveId;
            pending.maxNegativeId = maxNegativeId;
            pending.maxDate = maxDate;
            pending.threadId = threadId;
            pending.scheduledCount = scheduledCount;
        }
    }

    public static boolean flushRead(long dialogId, int account) {
        PendingRead pending;
        synchronized (lock) {
            pending = pendingReads.remove(dialogId);
        }
        if (pending == null) {
            return false;
        }
        MessagesController.getInstance(account).amberGhostMarkDialogAsRead(
                dialogId,
                pending.maxPositiveId,
                pending.maxNegativeId,
                pending.maxDate,
                false,
                pending.threadId,
                0,
                true,
                pending.scheduledCount
        );
        return true;
    }
}
