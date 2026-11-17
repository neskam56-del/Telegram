package org.telegram.ghostgram.ui;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.ghostgram.GhostConfig;
import org.telegram.ghostgram.update.GhostUpdate;
import org.telegram.ghostgram.update.GhostUpdateManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

/**
 * Ghostgram: UI OTA-обновлений (полный APK). Диалоги намеренно простые:
 *  - описание обновления с кнопками "Обновить"/"Позже" (при mandatory "Позже" нет);
 *  - прогресс скачивания с кнопкой "Отмена";
 *  - ошибка с кнопкой "Открыть страницу скачивания".
 */
public final class GhostUpdateDialog {

    private static boolean showing;

    private GhostUpdateDialog() {
    }

    public static boolean isShowing() {
        return showing;
    }

    public static void showAvailable(final Activity activity, final GhostUpdate update) {
        if (activity == null || activity.isFinishing() || update == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("Версия ").append(update.versionName).append(" (").append(update.versionCode).append(")");
        if (!TextUtils.isEmpty(update.changelog)) {
            message.append("\n\n").append(update.changelog);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление")
                .setMessage(message.toString())
                .setPositiveButton("Обновить", (dialog, which) -> {
                    dialog.dismiss();
                    startDownloadFlow(activity, false);
                });
        if (!update.mandatory) {
            builder.setNegativeButton("Позже", (dialog, which) -> {
                GhostConfig.ignoredUpdateVersionCode = update.versionCode;
                GhostConfig.saveConfig();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!update.mandatory);
        dialog.setOnDismissListener(d -> showing = false);
        showing = true;
        dialog.show();
    }

    public static void startDownloadFlow(final Activity activity, final boolean ignoreWifi) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (GhostConfig.updateWifiOnly && !GhostUpdateManager.isWifiConnected() && !ignoreWifi) {
            new AlertDialog.Builder(activity)
                    .setTitle("Обновление")
                    .setMessage("Включено скачивание только по Wi-Fi. Загрузить обновление по мобильной сети?")
                    .setPositiveButton("Скачать", (dialog, which) -> {
                        dialog.dismiss();
                        startDownloadFlow(activity, true);
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }
        GhostUpdateManager.download(activity);
        showProgress(activity);
    }

    private static void showProgress(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        final RadialProgressView progressView = new RadialProgressView(activity);
        container.addView(progressView, LayoutHelper.createLinear(40, 40, Gravity.CENTER_HORIZONTAL));

        final TextView statusView = new TextView(activity);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextSize(14);
        statusView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        statusView.setText("Скачивание… 0%");
        container.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Обновление")
                .setView(container)
                .setNegativeButton("Отмена", null)
                .create();

        final Runnable[] poller = new Runnable[1];
        poller[0] = () -> {
            if (!dialog.isShowing()) {
                return;
            }
            if (GhostUpdateManager.isDownloading()) {
                float progress = GhostUpdateManager.getProgress();
                progressView.setProgress(progress);
                statusView.setText("Скачивание… " + Math.round(progress * 100f) + "%");
                AndroidUtilities.runOnUIThread(poller[0], 250);
            } else if (GhostUpdateManager.isReady()) {
                dialog.dismiss();
                if (!GhostUpdateManager.install(activity)) {
                    showError(activity, "Не удалось открыть установщик");
                }
            } else if (GhostUpdateManager.hasError()) {
                String error = GhostUpdateManager.getError();
                dialog.dismiss();
                showError(activity, error);
            }
        };
        dialog.setOnDismissListener(d -> {
            AndroidUtilities.cancelRunOnUIThread(poller[0]);
            if (GhostUpdateManager.isDownloading()) {
                GhostUpdateManager.cancelDownload();
            }
        });
        dialog.show();
        AndroidUtilities.runOnUIThread(poller[0], 250);
    }

    public static void showError(final Activity activity, String message) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Ошибка обновления")
                .setMessage(TextUtils.isEmpty(message) ? "Не удалось скачать обновление" : message)
                .setPositiveButton("Закрыть", (d, which) -> {
                    d.dismiss();
                    GhostUpdateManager.deleteDownloadedFile();
                })
                .setNeutralButton("Открыть страницу скачивания", (d, which) -> {
                    d.dismiss();
                    GhostUpdateManager.deleteDownloadedFile();
                    Browser.openUrl(activity, GhostUpdateManager.getApkUrl());
                })
                .create();
        dialog.setOnDismissListener(d -> {
            if (GhostUpdateManager.hasError()) {
                GhostUpdateManager.deleteDownloadedFile();
            }
        });
        dialog.show();
    }
}
