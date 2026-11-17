package org.telegram.messenger;

import org.telegram.messenger.regular.BuildConfig;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public void onResume() {
        try {
            org.telegram.ghostgram.update.GhostUpdateManager.onAppResume();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
