package com.wushisan.algertv.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/** Pauses playback when audio becomes noisy (e.g. output device unplugged). */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            PlayerController pc = PlayerController.get();
            if (pc != null) pc.pause();
        }
    }
}
