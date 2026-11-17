package org.telegram.ghostgram.update;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.ghostgram.GhostConfig;
import org.telegram.ghostgram.ui.GhostUpdateDialog;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Ghostgram: простые OTA-обновления полным APK (без дельт/патчей).
 *
 * Проверка манифеста идёт по HTTPS-JSON из GhostConfig.updateManifestUrl, не чаще
 * одного раза в 12 часов (кроме ручной проверки из настроек), в фоновом потоке.
 * Скачанный APK проверяется по SHA-256 и устанавливается штатным способом проекта
 * (AndroidUtilities.openForView -> системный установщик + запрос "неизвестных источников").
 */
public final class GhostUpdateManager {

    public static final long CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final String APK_FILE_NAME = "ghostgram-update.apk";
    private static final int TIMEOUT_MS = 20000;
    private static final long INSTALL_CLEANUP_DELAY_MS = 5000L;

    private static final int STATE_IDLE = 0;
    private static final int STATE_DOWNLOADING = 1;
    private static final int STATE_READY = 2;
    private static final int STATE_ERROR = 3;

    private static volatile GhostUpdate pending;
    private static volatile int state = STATE_IDLE;
    private static volatile float progress;
    private static volatile String error;
    private static volatile boolean cancelRequested;
    private static volatile boolean checking;
    private static volatile long installLaunchedAt;

    private GhostUpdateManager() {
    }

    public static GhostUpdate getPending() {
        return pending;
    }

    public static boolean isDownloading() {
        return state == STATE_DOWNLOADING;
    }

    public static boolean isReady() {
        return state == STATE_READY;
    }

    public static boolean hasError() {
        return state == STATE_ERROR;
    }

    public static float getProgress() {
        return progress;
    }

    public static String getError() {
        return error;
    }

    public static String getApkUrl() {
        return pending != null ? pending.apkUrl : "";
    }

    public static int installedVersionCode() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return 0;
            }
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Throwable e) {
            return 0;
        }
    }

    public static String installedVersionName() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return "";
            }
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable e) {
            return "";
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isWifiConnected() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) {
                return false;
            }
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Вызывается из ApplicationLoaderImpl.onResume(): проверка не чаще раза в 12 часов. */
    public static void onAppResume() {
        try {
            onAppResume(org.telegram.ui.LaunchActivity.instance);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void onAppResume(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (installLaunchedAt != 0 && System.currentTimeMillis() - installLaunchedAt > INSTALL_CLEANUP_DELAY_MS) {
            installLaunchedAt = 0;
            deleteDownloadedFile();
        }
        if (isDownloading()) {
            return;
        }
        if (pending != null && pending.versionCode > installedVersionCode()
                && (pending.mandatory || pending.versionCode != GhostConfig.ignoredUpdateVersionCode)) {
            if (!GhostUpdateDialog.isShowing()) {
                GhostUpdateDialog.showAvailable(activity, pending);
            }
            return;
        }
        check(false, activity, false);
    }

    /**
     * Проверка манифеста.
     *
     * @param force           игнорировать 12-часовой лимит (ручная проверка)
     * @param activity        активность для показа диалога (может быть null)
     * @param showLatestBulletin показать "у вас последняя версия", если обновления нет
     */
    public static void check(final boolean force, final Activity activity, final boolean showLatestBulletin) {
        if (checking) {
            return;
        }
        if (!force && System.currentTimeMillis() - GhostConfig.lastUpdateCheckTime < CHECK_INTERVAL_MS) {
            if (showLatestBulletin) {
                showLatest(activity);
            }
            return;
        }
        final String url = GhostConfig.updateManifestUrl;
        if (TextUtils.isEmpty(url)) {
            if (showLatestBulletin) {
                showLatest(activity);
            }
            return;
        }
        checking = true;
        new Thread(() -> {
            GhostUpdate found = null;
            boolean fetched = false;
            try {
                JSONObject json = new JSONObject(httpGet(url));
                fetched = true;
                GhostUpdate update = new GhostUpdate();
                update.versionCode = json.optInt("versionCode", 0);
                update.versionName = json.optString("versionName", "");
                update.apkUrl = json.optString("apkUrl", "");
                update.sha256 = json.optString("sha256", "");
                update.changelog = json.optString("changelog", "");
                update.mandatory = json.optBoolean("mandatory", false);
                if (update.versionCode > installedVersionCode() && !TextUtils.isEmpty(update.apkUrl)) {
                    found = update;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final GhostUpdate result = found;
            if (fetched) {
                GhostConfig.lastUpdateCheckTime = System.currentTimeMillis();
                GhostConfig.saveConfig();
            }
            checking = false;
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null) {
                    pending = result;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                    if (activity != null && !activity.isFinishing()
                            && (result.mandatory || result.versionCode != GhostConfig.ignoredUpdateVersionCode)
                            && !GhostUpdateDialog.isShowing()) {
                        GhostUpdateDialog.showAvailable(activity, result);
                    }
                } else {
                    pending = null;
                    if (showLatestBulletin) {
                        showLatest(activity);
                    }
                }
            });
        }, "ghostgram-update-check").start();
    }

    /** Запускает скачивание (можно вызывать с UI-потока). */
    public static void download(final Activity activity) {
        final GhostUpdate update = pending;
        final File target = getTargetFile();
        if (update == null || target == null || TextUtils.isEmpty(update.apkUrl) || isDownloading()) {
            return;
        }
        cancelRequested = false;
        error = null;
        progress = 0f;
        state = STATE_DOWNLOADING;
        new Thread(() -> {
            final File tmp = new File(target.getAbsolutePath() + ".part");
            try {
                if (tmp.exists()) {
                    tmp.delete();
                }
                if (target.exists()) {
                    target.delete();
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(update.apkUrl).openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Ghostgram-Android-Update");
                try {
                    int code = conn.getResponseCode();
                    if (code != 200) {
                        throw new IOException("HTTP " + code);
                    }
                    int total = conn.getContentLength();
                    long read = 0;
                    try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = in.read(buffer)) > 0) {
                            if (cancelRequested) {
                                throw new IOException("cancelled");
                            }
                            out.write(buffer, 0, count);
                            read += count;
                            if (total > 0) {
                                progress = Math.min(0.99f, read / (float) total);
                            }
                        }
                        out.flush();
                    }
                } finally {
                    conn.disconnect();
                }
                if (cancelRequested) {
                    throw new IOException("cancelled");
                }
                if (TextUtils.isEmpty(update.sha256)) {
                    throw new IOException("В манифесте не указан sha256");
                }
                String actual = sha256(tmp);
                if (actual == null || !actual.equalsIgnoreCase(update.sha256.trim())) {
                    throw new IOException("SHA-256 не совпадает");
                }
                if (!tmp.renameTo(target)) {
                    throw new IOException("Не удалось сохранить APK");
                }
                progress = 1f;
                state = STATE_READY;
            } catch (Throwable e) {
                if (tmp.exists()) {
                    tmp.delete();
                }
                FileLog.e(e);
                if (cancelRequested) {
                    state = STATE_IDLE;
                    error = null;
                } else {
                    error = e.getMessage() == null ? "Не удалось скачать обновление" : e.getMessage();
                    state = STATE_ERROR;
                }
            }
        }, "ghostgram-update-download").start();
    }

    public static void cancelDownload() {
        if (isDownloading()) {
            cancelRequested = true;
        }
    }

    public static File getDownloadedFile() {
        File file = getTargetFile();
        return file != null && file.exists() ? file : null;
    }

    /**
     * Открывает системный установщик через штатный хелпер проекта
     * (он же показывает запрос "устанавливать из этого источника" на Android 8+).
     */
    public static boolean install(Activity activity) {
        File file = getDownloadedFile();
        if (activity == null || file == null) {
            return false;
        }
        boolean opened = AndroidUtilities.openForView(file, APK_FILE_NAME, "application/vnd.android.package-archive", activity, null, false);
        if (opened) {
            installLaunchedAt = System.currentTimeMillis();
            state = STATE_IDLE;
            progress = 0f;
        }
        return opened;
    }

    public static void deleteDownloadedFile() {
        try {
            File file = getTargetFile();
            if (file != null && file.exists()) {
                file.delete();
            }
            File tmp = file == null ? null : new File(file.getAbsolutePath() + ".part");
            if (tmp != null && tmp.exists()) {
                tmp.delete();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (state != STATE_DOWNLOADING) {
            state = STATE_IDLE;
            error = null;
            progress = 0f;
        }
    }

    private static void showLatest(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            BulletinFactory.global()
                    .createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.YourVersionIsLatest))
                    .show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static File getTargetFile() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) {
            dir = new File(ctx.getFilesDir(), "cache");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, APK_FILE_NAME);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Ghostgram-Android-Update");
        conn.setRequestProperty("Cache-Control", "no-cache");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) > 0) {
                    out.write(buffer, 0, count);
                }
                return out.toString("UTF-8");
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String sha256(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) {
                digest.update(buffer, 0, count);
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
