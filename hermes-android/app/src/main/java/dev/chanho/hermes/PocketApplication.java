package dev.chanho.hermes;

import android.app.Application;
import android.content.Context;

/** Install crash recording before activity or service initialization. */
public final class PocketApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Diagnostics.initialize(this);
    }
    @Override public void onCreate() {
        super.onCreate();
        Thread history=new Thread(() -> Diagnostics.collectPreviousProcessExits(this), "hermes-exit-metadata");
        history.setDaemon(true);
        history.start();
    }
}

