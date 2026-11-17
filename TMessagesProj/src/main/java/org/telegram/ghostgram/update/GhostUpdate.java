package org.telegram.ghostgram.update;

/**
 * Ghostgram: данные одного OTA-обновления, разобранные из update.json.
 * Поля соответствуют манифесту: versionCode, versionName, apkUrl, sha256, changelog, mandatory.
 */
public class GhostUpdate {

    public int versionCode;
    public String versionName = "";
    public String apkUrl = "";
    public String sha256 = "";
    public String changelog = "";
    public boolean mandatory;

}
