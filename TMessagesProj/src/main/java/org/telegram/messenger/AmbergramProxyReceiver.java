package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AmberGram fork: fires every 6 hours to refresh + retest the in-app free
 * proxy list (see AmbergramProxyManager).
 */
public class AmbergramProxyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            AmbergramProxyManager.getInstance().onAlarmReceived();
        } catch (Throwable ignore) {
        }
    }
}
