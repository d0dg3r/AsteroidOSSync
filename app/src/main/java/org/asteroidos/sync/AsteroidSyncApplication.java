package org.asteroidos.sync;

import android.app.Application;
import android.content.Context;

public class AsteroidSyncApplication extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    public static Context getAppContext() {
        return context;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
} 