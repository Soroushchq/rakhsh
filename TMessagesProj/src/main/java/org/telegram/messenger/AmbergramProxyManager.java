package org.telegram.messenger;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AmberGram fork: in-app free MTProto proxy provider.
 * <p>
 * Pulls proxy lists from public GitHub collector repositories every 6 hours
 * (AlarmManager wakeup + app-start catch-up), merges/dedupes them, then runs a
 * REAL MTProto availability test through the native ConnectionsManager
 * (ConnectionsManager.checkProxy) without touching the user's current
 * connection. Results (ping / availability) are persisted locally and shown in
 * AmbergramProxyActivity. Tapping a proxy hands it over to the official proxy
 * system (SharedConfig + ConnectionsManager.setProxySettings).
 */
public class AmbergramProxyManager {

    private static final String TAG = "AmbergramProxy";

    public static final long UPDATE_INTERVAL = 6 * 60 * 60 * 1000L;
    private static final int MAX_PROXIES = 180;
    private static final int MAX_PER_SOURCE = 400;
    private static final int TEST_BATCH = 20;
    private static final int RECHECK_MIN_INTERVAL = 2 * 60 * 1000;

    // Auto-cleanup: a proxy that fails a real MTProto handshake is removed from
    // the list right away. A whole cycle where EVERY tested proxy failed (and at
    // least OFFLINE_GUARD_MIN_TESTED were tested) is treated as a likely device
    // network outage, so nothing is removed in that case.
    private static final int REMOVE_AFTER_FAILS = 1;
    private static final int OFFLINE_GUARD_MIN_TESTED = 10;

    public static final int EVENT_FETCH_STARTED = 0;
    public static final int EVENT_FETCH_DONE = 1;
    public static final int EVENT_TEST_PROGRESS = 2;
    public static final int EVENT_TEST_DONE = 3;
    public static final int EVENT_LIST_CLEANED = 4;

    public interface Listener {
        void onProxyEvent(int event, int done, int total);
    }

    public static class ProxyItem {
        public String host;
        public int port;
        public String secret;
        public String source;
        public int sourcePing = -1;
        public int ping = -1;
        public boolean available;
        public boolean checking;
        public long lastOkAt;
        public long lastCheckElapsed;
        public int deadStreak;

        public String key() {
            return host + ":" + port + ":" + secret;
        }

        public ProxySettings toSettings() {
            return ProxySettings.builder()
                    .setType(ProxySettings.Type.MTPROTO)
                    .setAddress(host)
                    .setPort(port)
                    .setSecret(secret)
                    .build();
        }
    }

    private static class Source {
        final String name;
        final String url;
        final int format;

        Source(String name, String url, int format) {
            this.name = name;
            this.url = url;
            this.format = format;
        }
    }

    private static final int FORMAT_LINKS = 0;
    private static final int FORMAT_JSON = 1;

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(?:(?:https?://)?t\\.me/proxy\\?|tg://proxy\\?)server=([^&\\s\"']+)&port=(\\d{1,5})&secret=([^&\\s\"']+)");

    private static final Source[] SOURCES = {
            new Source("ALIILAPRO", "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt", FORMAT_LINKS),
            new Source("ALIILAPRO-Latency", "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/proxies.json", FORMAT_JSON),
            new Source("SoliSpirit", "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt", FORMAT_LINKS),
            new Source("TG-MTProxy", "https://raw.githubusercontent.com/tgmtproxy/telegram-mtproto-proxy-list/main/proxies.txt", FORMAT_LINKS),
            new Source("Argh94", "https://raw.githubusercontent.com/Argh94/Proxy-List/main/MTProto.txt", FORMAT_LINKS),
            new Source("0xRadikal", "https://raw.githubusercontent.com/0xRadikal/Free-Telegram-Proxy/main/all/tg.txt", FORMAT_LINKS),
    };

    private static volatile AmbergramProxyManager Instance;

    public static AmbergramProxyManager getInstance() {
        AmbergramProxyManager local = Instance;
        if (local == null) {
            synchronized (AmbergramProxyManager.class) {
                local = Instance;
                if (local == null) {
                    Instance = local = new AmbergramProxyManager();
                }
            }
        }
        return local;
    }

    private final ArrayList<ProxyItem> proxies = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();

    private volatile boolean fetching;
    private volatile boolean testing;
    private volatile int processedCount;
    private volatile int testTotal;
    private volatile int failedSourceCount;
    private volatile int cycleTestedCount;
    private volatile int cycleSuccessCount;
    private volatile int lastRemovedCount;

    private long lastFetchAt;
    private long lastTestAt;
    private boolean started;

    private final Runnable testWatchdog = () -> {
        if (!testing) {
            return;
        }
        testing = false;
        synchronized (proxies) {
            for (int a = 0; a < proxies.size(); a++) {
                proxies.get(a).checking = false;
            }
        }
        autoRemoveDeadProxies();
        synchronized (proxies) {
            sortProxies();
        }
        saveToDisk();
        notifyListeners(EVENT_TEST_DONE, processedCount, testTotal);
    };

    private AmbergramProxyManager() {
    }

    // ---------------------------------------------------------------- lifecycle

    public void onAppStart() {
        if (started) {
            return;
        }
        started = true;
        loadFromDisk();
        AndroidUtilities.runOnUIThread(this::autoCycleIfNeeded, 15000);
        scheduleNextAlarm(UPDATE_INTERVAL);
    }

    public void onAlarmReceived() {
        FileLog.d(TAG + ": alarm fired");
        scheduleNextAlarm(UPDATE_INTERVAL);
        autoCycleIfNeeded();
    }

    private void autoCycleIfNeeded() {
        if (!fetching && System.currentTimeMillis() - lastFetchAt > UPDATE_INTERVAL) {
            runUpdateCycle();
        }
    }

    public void scheduleNextAlarm(long delay) {
        try {
            AlarmManager alarmManager = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                return;
            }
            Intent intent = new Intent(ApplicationLoader.applicationContext, AmbergramProxyReceiver.class);
            intent.setAction("org.telegram.messenger.AMBERGRAM_PROXY_UPDATE");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    ApplicationLoader.applicationContext, 4213, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delay, 60 * 60 * 1000L, pendingIntent);
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delay, pendingIntent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // ---------------------------------------------------------------- update cycle

    public void runUpdateCycle() {
        if (fetching) {
            return;
        }
        fetching = true;
        failedSourceCount = 0;
        notifyListeners(EVENT_FETCH_STARTED, 0, 0);
        Utilities.globalQueue.postRunnable(() -> {
            LinkedHashMap<String, ProxyItem> merged = new LinkedHashMap<>();
            synchronized (proxies) {
                for (ProxyItem item : proxies) {
                    merged.put(item.key(), item);
                }
            }
            AtomicInteger workingSources = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(SOURCES.length);
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, SOURCES.length));
            for (Source source : SOURCES) {
                pool.execute(() -> {
                    try {
                        String text = httpGet(source.url);
                        int parsed = parseInto(source, text, merged);
                        if (parsed > 0) {
                            workingSources.incrementAndGet();
                        } else {
                            failedSourceCount++;
                        }
                        FileLog.d(TAG + ": source " + source.name + " parsed " + parsed + " proxies");
                    } catch (Throwable t) {
                        failedSourceCount++;
                        FileLog.e(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
            pool.shutdown();

            synchronized (proxies) {
                proxies.clear();
                proxies.addAll(merged.values());
                sortProxies();
                if (proxies.size() > MAX_PROXIES) {
                    proxies.subList(MAX_PROXIES, proxies.size()).clear();
                }
            }
            lastFetchAt = System.currentTimeMillis();
            saveToDisk();
            fetching = false;
            notifyListeners(EVENT_FETCH_DONE, proxies.size(), workingSources.get());
            AndroidUtilities.runOnUIThread(this::startTesting, 500);
        });
    }

    private int parseInto(Source source, String text, LinkedHashMap<String, ProxyItem> out) {
        if (source.format == FORMAT_JSON) {
            return parseJsonSource(source, text, out);
        }
        String clean = text.replace("&amp;", "&");
        Matcher matcher = LINK_PATTERN.matcher(clean);
        int count = 0;
        while (matcher.find() && count < MAX_PER_SOURCE) {
            String host = matcher.group(1);
            int port;
            try {
                port = Integer.parseInt(matcher.group(2));
            } catch (Exception e) {
                continue;
            }
            String secret = decodeSecret(matcher.group(3));
            if (!validProxy(host, port, secret)) {
                continue;
            }
            addMerged(out, newProxy(host, port, secret, source.name, -1));
            count++;
        }
        return count;
    }

    private int parseJsonSource(Source source, String text, LinkedHashMap<String, ProxyItem> out) {
        try {
            JSONArray array = new JSONArray(text);
            int count = 0;
            for (int a = 0; a < array.length() && count < MAX_PER_SOURCE; a++) {
                JSONObject object = array.optJSONObject(a);
                if (object == null) {
                    continue;
                }
                String host = object.optString("host", "");
                int port = object.optInt("port", 0);
                String connectUrl = object.optString("connect_url", "").replace("&amp;", "&");
                String secret = null;
                Matcher matcher = LINK_PATTERN.matcher(connectUrl);
                if (matcher.find()) {
                    secret = decodeSecret(matcher.group(3));
                }
                int latency = object.optInt("latency", -1);
                if (!validProxy(host, port, secret)) {
                    continue;
                }
                addMerged(out, newProxy(host, port, secret, source.name,
                        latency >= 10 && latency < 3000 ? latency : -1));
                count++;
            }
            return count;
        } catch (Throwable t) {
            FileLog.e(t);
            return 0;
        }
    }

    private ProxyItem newProxy(String host, int port, String secret, String source, int sourcePing) {
        ProxyItem item = new ProxyItem();
        item.host = host;
        item.port = port;
        item.secret = secret;
        item.source = source;
        item.sourcePing = sourcePing;
        return item;
    }

    private void addMerged(LinkedHashMap<String, ProxyItem> map, ProxyItem item) {
        ProxyItem existing = map.get(item.key());
        if (existing == null) {
            map.put(item.key(), item);
        } else if (existing.sourcePing < 0 && item.sourcePing >= 0) {
            existing.sourcePing = item.sourcePing;
        }
    }

    private static String decodeSecret(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.indexOf('%') >= 0) {
            try {
                value = URLDecoder.decode(value, "UTF-8");
            } catch (Exception ignore) {
            }
        }
        return value;
    }

    private static boolean validProxy(String host, int port, String secret) {
        if (host == null || host.length() == 0 || host.length() > 200 || host.indexOf(' ') >= 0) {
            return false;
        }
        if (port <= 0 || port > 65535) {
            return false;
        }
        if (secret == null || secret.length() < 16 || secret.length() > 150) {
            return false;
        }
        for (int a = 0; a < secret.length(); a++) {
            char c = secret.charAt(a);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || c == '=' || c == '_' || c == '-' || c == '+';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "AmberGram (https://github.com/Soroushchq/amber)");
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new Exception("HTTP " + code);
        }
        InputStream input = connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) > 0) {
            output.write(buffer, 0, read);
        }
        input.close();
        connection.disconnect();
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- testing

    public void startTesting() {
        if (testing) {
            return;
        }
        final ArrayList<ProxyItem> snapshot;
        synchronized (proxies) {
            snapshot = new ArrayList<>(proxies);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        testing = true;
        processedCount = 0;
        testTotal = snapshot.size();
        cycleTestedCount = 0;
        cycleSuccessCount = 0;
        lastRemovedCount = 0;
        notifyListeners(EVENT_TEST_PROGRESS, 0, testTotal);
        long watchdogDelay = Math.min(testTotal * 6000L + 60000L, 12 * 60 * 1000L);
        AndroidUtilities.cancelRunOnUIThread(testWatchdog);
        AndroidUtilities.runOnUIThread(testWatchdog, watchdogDelay);
        testBatch(snapshot, 0);
    }

    private void testBatch(final ArrayList<ProxyItem> list, final int index) {
        if (!testing) {
            return;
        }
        final int end = Math.min(list.size(), index + TEST_BATCH);
        if (index >= end) {
            finishTesting();
            return;
        }
        final AtomicInteger remaining = new AtomicInteger(end - index);
        for (int a = index; a < end; a++) {
            final ProxyItem item = list.get(a);
            long now = SystemClock.elapsedRealtime();
            if (item.checking || now - item.lastCheckElapsed < RECHECK_MIN_INTERVAL) {
                processedCount++;
                notifyListeners(EVENT_TEST_PROGRESS, processedCount, testTotal);
                if (remaining.decrementAndGet() == 0) {
                    testBatch(list, end);
                }
                continue;
            }
            item.checking = true;
            item.lastCheckElapsed = now;
            final int account = UserConfig.selectedAccount;
            ConnectionsManager.getInstance(account).checkProxy(item.toSettings(), time -> AndroidUtilities.runOnUIThread(() -> {
                item.checking = false;
                if (time == -1) {
                    item.available = false;
                    item.ping = -1;
                    item.deadStreak++;
                    FileLog.d(TAG + ": DEAD " + item.host + ":" + item.port + " (streak " + item.deadStreak + ")");
                } else {
                    item.available = true;
                    item.ping = (int) time;
                    item.lastOkAt = System.currentTimeMillis();
                    item.deadStreak = 0;
                }
                cycleTestedCount++;
                if (time != -1) {
                    cycleSuccessCount++;
                }
                processedCount++;
                notifyListeners(EVENT_TEST_PROGRESS, processedCount, testTotal);
                if (remaining.decrementAndGet() == 0) {
                    synchronized (proxies) {
                        sortProxies();
                    }
                    saveToDisk();
                    testBatch(list, end);
                }
            }));
        }
    }

    private void finishTesting() {
        testing = false;
        lastTestAt = System.currentTimeMillis();
        AndroidUtilities.cancelRunOnUIThread(testWatchdog);
        autoRemoveDeadProxies();
        synchronized (proxies) {
            sortProxies();
            if (proxies.size() > MAX_PROXIES) {
                proxies.subList(MAX_PROXIES, proxies.size()).clear();
            }
        }
        saveToDisk();
        notifyListeners(EVENT_TEST_DONE, processedCount, testTotal);
    }

    // ---------------------------------------------------------------- auto cleanup

    /**
     * AmberGram fork: automatically removes proxies that failed their MTProto
     * handshake. Every removal also cleans the official SharedConfig proxy list
     * so dead entries never accumulate in the system proxy screen, and if the
     * currently active proxy died, the best surviving proxy (lowest ping) is
     * connected automatically instead; if nothing survived, the proxy is
     * switched off cleanly instead of leaving the user with a broken
     * "connected" state.
     */
    private void autoRemoveDeadProxies() {
        final int tested = cycleTestedCount;
        final int successes = cycleSuccessCount;
        boolean massFailure = tested >= OFFLINE_GUARD_MIN_TESTED && successes == 0;
        boolean offlineDevice = successes == 0 && tested > 0 && !isDeviceOnline();
        if (massFailure || offlineDevice) {
            // Not a single proxy answered — most likely the device lost
            // connectivity, not every proxy dying at once. Keep the list intact.
            FileLog.d(TAG + ": cleanup skipped, all " + tested + " tests failed (device offline or network outage)");
            return;
        }
        ArrayList<ProxyItem> removed = new ArrayList<>();
        ProxyItem best = null;
        synchronized (proxies) {
            Iterator<ProxyItem> iterator = proxies.iterator();
            while (iterator.hasNext()) {
                ProxyItem item = iterator.next();
                if (item.checking) {
                    continue;
                }
                if (!item.available && item.deadStreak >= REMOVE_AFTER_FAILS) {
                    removed.add(item);
                    iterator.remove();
                }
            }
            for (int a = 0; a < proxies.size(); a++) {
                ProxyItem item = proxies.get(a);
                if (item.available && item.ping >= 0 && (best == null || item.ping < best.ping)) {
                    best = item;
                }
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        lastRemovedCount = removed.size();
        FileLog.d(TAG + ": auto-removed " + removed.size() + " dead proxies");
        AndroidUtilities.runOnUIThread(() -> cleanupSharedConfig(removed, best));
        notifyListeners(EVENT_LIST_CLEANED, lastRemovedCount, 0);
    }

    private void cleanupSharedConfig(ArrayList<ProxyItem> removed, ProxyItem bestFallback) {
        try {
            SharedConfig.loadProxyList();
            SharedConfig.ProxyInfo diedCurrent = null;
            for (int a = 0; a < removed.size(); a++) {
                ProxyItem item = removed.get(a);
                SharedConfig.ProxyInfo match = findSharedProxy(item.host, item.port, item.secret);
                if (match == null) {
                    continue;
                }
                if (match == SharedConfig.currentProxy) {
                    diedCurrent = match;
                } else {
                    SharedConfig.deleteProxy(match);
                }
            }
            if (diedCurrent != null) {
                boolean wasEnabled = MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false);
                if (wasEnabled && bestFallback != null) {
                    FileLog.d(TAG + ": active proxy died, switching to " + bestFallback.host + ":" + bestFallback.port);
                    connectTo(bestFallback);
                } else {
                    FileLog.d(TAG + ": active proxy died, no replacement — proxy disabled");
                    SharedConfig.deleteProxy(diedCurrent);
                }
            }
            if (diedCurrent != null && SharedConfig.currentProxy != diedCurrent) {
                SharedConfig.deleteProxy(diedCurrent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static SharedConfig.ProxyInfo findSharedProxy(String host, int port, String secret) {
        for (int a = 0; a < SharedConfig.proxyList.size(); a++) {
            SharedConfig.ProxyInfo info = SharedConfig.proxyList.get(a);
            if (info != null && info.settings != null
                    && Objects.equals(info.settings.getAddress(), host)
                    && info.settings.getPort() == port
                    && Objects.equals(info.settings.getSecret(), secret)) {
                return info;
            }
        }
        return null;
    }

    private static boolean isDeviceOnline() {
        try {
            ConnectivityManager manager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return true;
            }
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    private void sortProxies() {
        Collections.sort(proxies, (o1, o2) -> Integer.compare(sortScore(o1), sortScore(o2)));
    }

    private int sortScore(ProxyItem item) {
        if (item.available && item.ping >= 0) {
            return item.ping;
        }
        if (item.available) {
            return 5000 + Math.max(0, item.sourcePing);
        }
        if (item.deadStreak > 0) {
            return 1_000_000 + Math.max(0, item.sourcePing);
        }
        return 100_000 + Math.max(0, item.sourcePing);
    }

    // ---------------------------------------------------------------- connect

    public void connectTo(ProxyItem item) {
        try {
            ProxySettings settings = item.toSettings();
            SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo(settings));
            SharedConfig.currentProxy = info;
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("proxy_enabled", true);
            settings.toSharedPreferences(editor);
            editor.commit();
            ConnectionsManager.setProxySettings(true, settings);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // ---------------------------------------------------------------- state

    public ArrayList<ProxyItem> getProxiesSnapshot() {
        synchronized (proxies) {
            return new ArrayList<>(proxies);
        }
    }

    public int getTotalCount() {
        synchronized (proxies) {
            return proxies.size();
        }
    }

    public int getWorkingCount() {
        int count = 0;
        ArrayList<ProxyItem> snapshot = getProxiesSnapshot();
        for (int a = 0; a < snapshot.size(); a++) {
            if (snapshot.get(a).available) {
                count++;
            }
        }
        return count;
    }

    public boolean isFetching() {
        return fetching;
    }

    public boolean isTesting() {
        return testing;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getTestTotal() {
        return testTotal;
    }

    public int getFailedSourceCount() {
        return failedSourceCount;
    }

    public int getLastRemovedCount() {
        return lastRemovedCount;
    }

    public long getLastFetchAt() {
        return lastFetchAt;
    }

    public long getLastTestAt() {
        return lastTestAt;
    }

    public void addListener(Listener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(int event, int done, int total) {
        ArrayList<Listener> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        for (int a = 0; a < copy.size(); a++) {
            Listener listener = copy.get(a);
            AndroidUtilities.runOnUIThread(() -> listener.onProxyEvent(event, done, total));
        }
    }

    // ---------------------------------------------------------------- persistence

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("ambergram_proxies", Context.MODE_PRIVATE);
    }

    private void saveToDisk() {
        try {
            JSONArray array = new JSONArray();
            synchronized (proxies) {
                for (int a = 0; a < proxies.size(); a++) {
                    ProxyItem item = proxies.get(a);
                    JSONObject object = new JSONObject();
                    object.put("h", item.host);
                    object.put("p", item.port);
                    object.put("s", item.secret);
                    object.put("src", item.source);
                    object.put("sp", item.sourcePing);
                    object.put("g", item.ping);
                    object.put("a", item.available ? 1 : 0);
                    object.put("ok", item.lastOkAt);
                    object.put("d", item.deadStreak);
                    array.put(object);
                }
            }
            prefs().edit()
                    .putString("data", array.toString())
                    .putLong("last_fetch", lastFetchAt)
                    .putLong("last_test", lastTestAt)
                    .putInt("failed_sources", failedSourceCount)
                    .putInt("last_removed", lastRemovedCount)
                    .apply();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void loadFromDisk() {
        try {
            lastFetchAt = prefs().getLong("last_fetch", 0);
            lastTestAt = prefs().getLong("last_test", 0);
            failedSourceCount = prefs().getInt("failed_sources", 0);
            lastRemovedCount = prefs().getInt("last_removed", 0);
            String raw = prefs().getString("data", null);
            if (raw == null) {
                return;
            }
            JSONArray array = new JSONArray(raw);
            synchronized (proxies) {
                proxies.clear();
                for (int a = 0; a < array.length(); a++) {
                    JSONObject object = array.optJSONObject(a);
                    if (object == null) {
                        continue;
                    }
                    ProxyItem item = new ProxyItem();
                    item.host = object.optString("h", "");
                    item.port = object.optInt("p", 0);
                    item.secret = object.optString("s", "");
                    item.source = object.optString("src", "");
                    item.sourcePing = object.optInt("sp", -1);
                    item.ping = object.optInt("g", -1);
                    item.available = object.optInt("a", 0) == 1;
                    item.lastOkAt = object.optLong("ok", 0);
                    item.deadStreak = object.optInt("d", 0);
                    if (validProxy(item.host, item.port, item.secret)) {
                        proxies.add(item);
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
