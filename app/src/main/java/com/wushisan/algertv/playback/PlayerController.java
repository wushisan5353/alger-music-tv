package com.wushisan.algertv.playback;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.wushisan.algertv.App;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the MediaPlayer and the play queue (single process, app-wide singleton).
 * PlaybackService mirrors this state into a foreground notification + media session.
 */
public class PlayerController {

    public interface Listener {
        void onSongChanged(Song song, int index);

        void onPlaybackStateChanged(boolean playing);

        void onPlaybackError(String message);
    }

    private static PlayerController sInstance;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AudioManager audioManager;

    private MediaPlayer mediaPlayer;
    private List<Song> queue = new ArrayList<>();
    private int index = -1;
    private boolean playing = false;
    private boolean preparing = false;
    /**
     * True only between onPrepared and the next load/reset. MediaPlayer
     * position/duration/seek calls are valid solely inside this window;
     * calling them earlier (a UI ticker polling while prepareAsync is
     * still in flight, or PlaybackService.refresh on song change) makes
     * the player post error (1, -38) to the error listener — which used
     * to kill the first track of every queue on real devices.
     */
    private boolean prepared = false;
    private long loadToken = 0;

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS && playing) {
                        pause();
                    }
                }
            };

    public static synchronized void init(Context ctx) {
        if (sInstance == null) sInstance = new PlayerController(ctx.getApplicationContext());
    }

    public static PlayerController get() {
        return sInstance;
    }

    private PlayerController(Context ctx) {
        this.appContext = ctx;
        this.audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------- state

    public boolean hasQueue() {
        return index >= 0 && index < queue.size();
    }

    public Song currentSong() {
        return hasQueue() ? queue.get(index) : null;
    }

    public List<Song> getQueue() {
        return queue;
    }

    public int getIndex() {
        return index;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPreparing() {
        return preparing;
    }

    public int getPosition() {
        // Never touch the player outside the prepared window: in Idle /
        // Initialized / Preparing states getCurrentPosition() posts an
        // error (1, -38) to the error listener even when called inside
        // try/catch — the exception is not the delivery mechanism.
        if (!prepared || mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getCurrentPosition();
        } catch (Exception ignored) {
            return 0;
        }
    }

    public int getDuration() {
        // Same guard as getPosition(): fall back to playlist metadata
        // until the player is actually prepared.
        if (prepared && mediaPlayer != null) {
            try {
                int d = mediaPlayer.getDuration();
                if (d > 0) return d;
            } catch (Exception ignored) {
            }
        }
        Song s = currentSong();
        return s == null ? 0 : (int) s.durationMs;
    }

    // ------------------------------------------------------------- control

    public void playQueue(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        queue = new ArrayList<>(songs);
        index = Math.max(0, Math.min(startIndex, queue.size() - 1));
        ensureService();
        startCurrent();
    }

    public void togglePlayPause() {
        if (!hasQueue()) return;
        if (playing) pause();
        else resume();
    }

    public void resume() {
        if (preparing) return; // onPrepared starts playback when ready
        if (mediaPlayer == null || !prepared) {
            startCurrent();
            return;
        }
        requestFocus();
        try {
            mediaPlayer.start();
            setPlaying(true);
        } catch (Exception e) {
            startCurrent();
        }
    }

    public void pause() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        } catch (Exception ignored) {
        }
        setPlaying(false);
    }

    public void playNext() {
        if (!hasQueue()) return;
        if (index + 1 < queue.size()) {
            index++;
            startCurrent();
        } else {
            stopPlayback();
        }
    }

    public void playPrev() {
        if (!hasQueue()) return;
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        if (index > 0) {
            index--;
            startCurrent();
        } else {
            seekTo(0);
        }
    }

    public void seekBy(int deltaMs) {
        if (mediaPlayer == null || !prepared) return;
        try {
            int pos = mediaPlayer.getCurrentPosition() + deltaMs;
            int dur = mediaPlayer.getDuration();
            if (pos < 0) pos = 0;
            if (dur > 0 && pos > dur) pos = dur - 500;
            mediaPlayer.seekTo(pos);
        } catch (Exception ignored) {
        }
    }

    public void seekTo(int posMs) {
        if (mediaPlayer == null || !prepared) return;
        try {
            mediaPlayer.seekTo(posMs);
        } catch (Exception ignored) {
        }
    }

    public void stopPlayback() {
        loadToken++;
        try {
            if (mediaPlayer != null) mediaPlayer.reset();
        } catch (Exception ignored) {
        }
        preparing = false;
        prepared = false;
        setPlaying(false);
    }

    // ------------------------------------------------------------- internals

    private void ensureService() {
        try {
            Intent i = new Intent(appContext, PlaybackService.class);
            // The playback service is a foreground service: on API 26+ it
            // must be started via startForegroundService (plain startService
            // throws IllegalStateException once the app is not in the
            // foreground on Android 12+/targetSdk 31+, which the phone
            // flavor hits — playback entry points like notification
            // actions and auto-advance reach here from the background).
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                androidx.core.content.ContextCompat.startForegroundService(appContext, i);
            } else {
                appContext.startService(i);
            }
        } catch (Exception e) {
            Log.w(App.TAG, "startService failed: " + e.getMessage());
        }
    }

    private void ensurePlayer() {
        if (mediaPlayer != null) return;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                preparing = false;
                Song s = currentSong();
                Log.i(App.TAG, "PLAYER_PREPARED id=" + (s == null ? "?" : s.id)
                        + " dur=" + mp.getDuration());
                requestFocus();
                mp.start();
                setPlaying(true);
                Log.i(App.TAG, "PLAYER_STARTED id=" + (s == null ? "?" : s.id));
                notifySongChanged();
            }
        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.i(App.TAG, "PLAYER_COMPLETED idx=" + index);
                if (index + 1 < queue.size()) {
                    index++;
                    startCurrent();
                } else {
                    setPlaying(false);
                }
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                // -38 (INVALID_OPERATION) is a client-side state error,
                // not a stream failure: something polled or sought the
                // player outside the prepared window. It must never kill
                // a track — the pre-fix handler treated it as fatal and
                // skipped the first song of every queue.
                if (extra == -38) {
                    Log.w(App.TAG, "PLAYER_ERROR_IGNORED -38 idx=" + index
                            + " prepared=" + prepared);
                    return true;
                }
                Log.e(App.TAG, "PLAYER_ERROR what=" + what + " extra=" + extra + " idx=" + index);
                prepared = false;
                preparing = false;
                setPlaying(false);
                notifyError("播放失败，自动跳到下一首");
                if (index + 1 < queue.size()) {
                    index++;
                    startCurrent();
                }
                return true;
            }
        });
    }

    private void startCurrent() {
        final Song song = currentSong();
        if (song == null) return;
        ensurePlayer();
        final long token = ++loadToken;
        prepared = false;
        preparing = true;
        setPlaying(false);
        notifySongChanged();
        Log.i(App.TAG, "PLAYER_LOAD id=" + song.id + " name=" + song.name);
        MusicRepository.get().resolvePlayUrl(song, new MusicRepository.Result<String>() {
            @Override
            public void onSuccess(final String url) {
                if (token != loadToken) return; // superseded
                try {
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(url);
                    mediaPlayer.prepareAsync();
                    Log.i(App.TAG, "PLAYER_PREPARING id=" + song.id);
                } catch (Exception e) {
                    preparing = false;
                    Log.e(App.TAG, "PLAYER_SETDATA_FAIL: " + e.getMessage());
                    notifyError("无法播放该歌曲");
                    if (index + 1 < queue.size()) {
                        index++;
                        startCurrent();
                    }
                }
            }

            @Override
            public void onError(String message) {
                if (token != loadToken) return;
                preparing = false;
                Log.e(App.TAG, "PLAYER_URL_FAIL id=" + song.id + ": " + message);
                notifyError("无播放源：" + song.name);
                if (index + 1 < queue.size()) {
                    index++;
                    startCurrent();
                }
            }
        });
    }

    private void requestFocus() {
        try {
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        } catch (Exception ignored) {
        }
    }

    private void setPlaying(boolean value) {
        if (playing == value) {
            notifyState();
            return;
        }
        playing = value;
        notifyState();
    }

    private void notifyState() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onPlaybackStateChanged(playing);
            }
        });
    }

    private void notifySongChanged() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Song s = currentSong();
                for (Listener l : listeners) l.onSongChanged(s, index);
            }
        });
    }

    private void notifyError(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) l.onPlaybackError(msg);
            }
        });
    }
}
