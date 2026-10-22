package com.wushisan.algertv.ui;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.wushisan.algertv.App;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.LrcParser;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private View root;
    private ImageView coverView;
    private TextView titleView;
    private TextView artistView;
    private TextView timeView;
    private ProgressBar progressBar;
    private Button playBtn;
    private RecyclerView lyricsView;
    private LinearLayoutManager lyricsLayoutManager;
    private LyricAdapter lyricAdapter;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private List<LrcParser.Line> lyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;
    private Song boundSong;
    private CustomTarget<Bitmap> coverTarget;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressAndLyrics();
            ticker.postDelayed(this, 500);
        }
    };

    private final PlayerController.Listener controllerListener = new PlayerController.Listener() {
        @Override
        public void onSongChanged(Song song, int index) {
            bindSong(song);
        }

        @Override
        public void onPlaybackStateChanged(boolean playing) {
            playBtn.setText(playing ? R.string.pause : R.string.play);
        }

        @Override
        public void onPlaybackError(String message) {
            Toast.makeText(PlayerActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.player_root);
        coverView = findViewById(R.id.player_cover);
        titleView = findViewById(R.id.player_title);
        artistView = findViewById(R.id.player_artist);
        timeView = findViewById(R.id.player_time);
        progressBar = findViewById(R.id.player_progress);
        playBtn = findViewById(R.id.btn_play);
        Button prevBtn = findViewById(R.id.btn_prev);
        Button nextBtn = findViewById(R.id.btn_next);
        lyricsView = findViewById(R.id.player_lyrics);

        lyricsLayoutManager = new LinearLayoutManager(this);
        lyricsView.setLayoutManager(lyricsLayoutManager);
        int pad = (int) (180 * getResources().getDisplayMetrics().density);
        lyricsView.setPadding(0, pad, 0, pad);
        lyricsView.setClipToPadding(false);
        lyricAdapter = new LyricAdapter();
        lyricsView.setAdapter(lyricAdapter);

        final PlayerController pc = PlayerController.get();
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.togglePlayPause();
            }
        });
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.playPrev();
            }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.playNext();
            }
        });
        coverView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.togglePlayPause();
            }
        });

        Song current = pc.currentSong();
        if (current == null) {
            Toast.makeText(this, "暂无播放队列", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bindSong(current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerController.get().addListener(controllerListener);
        playBtn.setText(PlayerController.get().isPlaying() ? R.string.pause : R.string.play);
        ticker.post(tickRunnable);
        coverView.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerController.get().removeListener(controllerListener);
        ticker.removeCallbacks(tickRunnable);
    }

    // ------------------------------------------------------------ binding

    private void bindSong(final Song song) {
        if (song == null) return;
        if (boundSong == song) {
            // same instance: still refresh texts
        }
        boundSong = song;
        titleView.setText(song.name == null ? "" : song.name);
        artistView.setText(song.subtitle());
        currentLyricIndex = -1;
        lyricLines = new ArrayList<>();
        lyricAdapter.notifyDataSetChanged();
        loadCover(song);
        loadLyrics(song);
        Log.i(App.TAG, "PLAYER_UI_BIND id=" + song.id);
    }

    private void loadCover(final Song song) {
        if (song.picUrl != null) {
            loadCoverBitmap(song.picUrl);
        } else {
            coverView.setImageResource(R.drawable.cover_placeholder);
            MusicRepository.get().resolvePic(song, new MusicRepository.Result<String>() {
                @Override
                public void onSuccess(String url) {
                    if (boundSong == song && !isFinishing()) loadCoverBitmap(url);
                }

                @Override
                public void onError(String message) {
                }
            });
        }
    }

    private void loadCoverBitmap(String url) {
        if (coverTarget != null) {
            Glide.with(this).clear(coverTarget);
        }
        coverTarget = new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource,
                                        @Nullable Transition<? super Bitmap> transition) {
                coverView.setImageBitmap(resource);
                applyPalette(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                coverView.setImageResource(R.drawable.cover_placeholder);
            }
        };
        Glide.with(this).asBitmap().load(url)
                .placeholder(R.drawable.cover_placeholder)
                .into(coverTarget);
    }

    private void applyPalette(Bitmap bitmap) {
        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(@Nullable Palette palette) {
                if (palette == null) return;
                int dark = palette.getDarkMutedColor(0xFF0B0F14);
                int muted = palette.getMutedColor(0xFF16202B);
                GradientDrawable gd = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{muted, dark, 0xFF0B0F14});
                root.setBackground(gd);
            }
        });
    }

    private void loadLyrics(final Song song) {
        MusicRepository.get().loadLyric(song, new MusicRepository.Result<String>() {
            @Override
            public void onSuccess(String lyric) {
                if (boundSong != song || isFinishing()) return;
                lyricLines = LrcParser.parse(lyric);
                currentLyricIndex = -1;
                lyricAdapter.notifyDataSetChanged();
                Log.i(App.TAG, "LYRIC_OK id=" + song.id + " lines=" + lyricLines.size());
            }

            @Override
            public void onError(String message) {
                if (boundSong != song || isFinishing()) return;
                lyricLines = new ArrayList<>();
                lyricLines.add(new LrcParser.Line(0, getString(R.string.no_lyric)));
                lyricAdapter.notifyDataSetChanged();
            }
        });
    }

    // ------------------------------------------------------------ progress

    private void updateProgressAndLyrics() {
        PlayerController pc = PlayerController.get();
        int dur = pc.getDuration();
        int pos = pc.getPosition();
        if (dur > 0) {
            progressBar.setProgress((int) (1000L * pos / dur));
        } else {
            progressBar.setProgress(0);
        }
        timeView.setText(formatTime(pos) + " / " + formatTime(dur));
        if (!lyricLines.isEmpty()) {
            int idx = LrcParser.indexFor(lyricLines, pos);
            if (idx != currentLyricIndex && idx >= 0) {
                int old = currentLyricIndex;
                currentLyricIndex = idx;
                lyricAdapter.notifyItemChanged(old);
                lyricAdapter.notifyItemChanged(idx);
                int offset = lyricsView.getHeight() / 2 - (int) (28 * getResources().getDisplayMetrics().density);
                lyricsLayoutManager.scrollToPositionWithOffset(idx, offset);
            }
        }
    }

    private static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    // ------------------------------------------------------------ keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        PlayerController pc = PlayerController.get();
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                pc.togglePlayPause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                pc.resume();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pc.pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                pc.playNext();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                pc.playPrev();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (getCurrentFocus() == coverView) {
                    pc.seekBy(-10000);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (getCurrentFocus() == coverView) {
                    pc.seekBy(10000);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------ lyrics list

    private class LyricAdapter extends RecyclerView.Adapter<LyricAdapter.LVH> {

        class LVH extends RecyclerView.ViewHolder {
            final TextView text;

            LVH(View v) {
                super(v);
                text = v.findViewById(R.id.lyric_text);
            }
        }

        @NonNull
        @Override
        public LVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lyric, parent, false);
            return new LVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LVH holder, int position) {
            LrcParser.Line line = lyricLines.get(position);
            holder.text.setText(line.text);
            if (position == currentLyricIndex) {
                holder.text.setTextColor(0xFFFFFFFF);
                holder.text.setTextSize(27);
                holder.text.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                holder.text.setTextColor(0xFF8CA3B8);
                holder.text.setTextSize(21);
                holder.text.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }

        @Override
        public int getItemCount() {
            return lyricLines.size();
        }
    }
}
