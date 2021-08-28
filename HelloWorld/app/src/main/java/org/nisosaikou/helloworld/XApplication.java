package org.nisosaikou.helloworld;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class XApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        Log.i("xiabo", "XApplication attachBaseContext base " + base);
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        Log.i("xiabo", "XApplication onCreate");
        super.onCreate();
    }
}
