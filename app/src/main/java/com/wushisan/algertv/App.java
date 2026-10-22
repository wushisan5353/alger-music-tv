package com.wushisan.algertv;

import android.app.Application;
import android.util.Log;

import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.playback.PlayerController;

public class App extends Application {
    public static final String TAG = "AlgerTV";

    @Override
    public void onCreate() {
        super.onCreate();
        MusicRepository.init(this);
        PlayerController.init(this);
        Log.i(TAG, "App onCreate");
    }
}
