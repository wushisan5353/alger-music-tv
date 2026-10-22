package com.wushisan.algertv.ui.phone;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
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
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.LrcParser;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.ArrayList;
import java.util.List;

/** Full-screen touch player: cover, palette gradient, synced lyrics,
 *  draggable progress, prev/play/next + queue entry. */
public class PhonePlayerActivity extends AppCompatActivity {

    private View root;
    private ImageView coverView;
    private TextView titleView;
    private TextView artistView;
    private TextView timeCurrent;
    private TextView timeTotal;
    private SeekBar seekBar;
    private ImageButton playBtn;
    private RecyclerView lyricsView;
    private LinearLayoutManager lyricsLayoutManager;
    private LyricAdapter lyricAdapter;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private List<LrcParser.Line> lyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;
    private Song boundSong;
    private CustomTarget<Bitmap> coverTarget;
    private boolean userDragging = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressAndLyrics();
            ticker.postDelayed(this, 400);
        }
    };

    private final PlayerController.Listener controllerListener = new PlayerController.Listener() {
        @Override
        public void onSongChanged(Song song, int index) {
            bindSong(song);
        }

        @Override
        public void onPlaybackStateChanged(boolean playing) {
            updatePlayIcon();
        }

        @Override
        public void onPlaybackError(String message) {
            Toast.makeText(PhonePlayerActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_player);

        // Notifications are optional; ask once here (first playback lands on
        // this screen) and never block on the answer.
        PhonePerms.requestNotificationsIfNeeded(this);

        root = findViewById(R.id.player_root);
        coverView = findViewById(R.id.player_cover);
        titleView = findViewById(R.id.player_title);
        artistView = findViewById(R.id.player_artist);
        timeCurrent = findViewById(R.id.time_current);
        timeTotal = findViewById(R.id.time_total);
        seekBar = findViewById(R.id.player_seek);
        playBtn = findViewById(R.id.btn_play);
        lyricsView = findViewById(R.id.player_lyrics);

        lyricsLayoutManager = new LinearLayoutManager(this);
        lyricsView.setLayoutManager(lyricsLayoutManager);
        int pad = (int) (90 * getResources().getDisplayMetrics().density);
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
        findViewById(R.id.btn_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.playPrev();
            }
        });
        findViewById(R.id.btn_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.playNext();
            }
        });
        findViewById(R.id.btn_queue).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PhonePlayerActivity.this, PhoneQueueActivity.class));
            }
        });
        coverView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pc.togglePlayPause();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    timeCurrent.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userDragging = false;
                PlayerController.get().seekTo(bar.getProgress());
            }
        });

        Song current = pc.currentSong();
        if (current == null) {
            Toast.makeText(this, "暂无播放队列", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bindSong(current);
        updatePlayIcon();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerController.get().addListener(controllerListener);
        updatePlayIcon();
        ticker.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerController.get().removeListener(controllerListener);
        ticker.removeCallbacks(tickRunnable);
    }

    private void updatePlayIcon() {
        boolean playing = PlayerController.get().isPlaying();
        playBtn.setImageResource(playing
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    // ------------------------------------------------------------ binding

    private void bindSong(final Song song) {
        if (song == null) return;
        boundSong = song;
        titleView.setText(song.name == null ? "" : song.name);
        artistView.setText(song.subtitle());
        currentLyricIndex = -1;
        lyricLines = new ArrayList<>();
        lyricAdapter.notifyDataSetChanged();
        seekBar.setProgress(0);
        loadCover(song);
        loadLyrics(song);
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
            if (seekBar.getMax() != dur) seekBar.setMax(dur);
            if (!userDragging) seekBar.setProgress(pos);
            timeTotal.setText(formatTime(dur));
        }
        if (!userDragging) timeCurrent.setText(formatTime(pos));
        if (!lyricLines.isEmpty()) {
            int idx = LrcParser.indexFor(lyricLines, pos);
            if (idx != currentLyricIndex && idx >= 0) {
                int old = currentLyricIndex;
                currentLyricIndex = idx;
                if (old >= 0) lyricAdapter.notifyItemChanged(old);
                lyricAdapter.notifyItemChanged(idx);
                int offset = lyricsView.getHeight() / 2
                        - (int) (20 * getResources().getDisplayMetrics().density);
                lyricsLayoutManager.scrollToPositionWithOffset(idx, offset);
            }
        }
    }

    static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
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
                    .inflate(R.layout.item_phone_lyric, parent, false);
            return new LVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LVH holder, int position) {
            LrcParser.Line line = lyricLines.get(position);
            holder.text.setText(line.text);
            if (position == currentLyricIndex) {
                holder.text.setTextColor(0xFFFFFFFF);
                holder.text.setTextSize(19);
                holder.text.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                holder.text.setTextColor(0xFF8CA3B8);
                holder.text.setTextSize(16);
                holder.text.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }

        @Override
        public int getItemCount() {
            return lyricLines.size();
        }
    }
}
