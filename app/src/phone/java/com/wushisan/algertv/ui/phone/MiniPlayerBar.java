package com.wushisan.algertv.ui.phone;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

/**
 * Bottom mini player bar shared by the phone screens. Include
 * {@code @layout/view_mini_player} in a layout, construct with the activity,
 * then {@link #attach()} in onResume and {@link #detach()} in onPause.
 */
public class MiniPlayerBar implements PlayerController.Listener {

    private final Activity activity;
    private final View root;
    private final ImageView cover;
    private final TextView title;
    private final TextView artist;
    private final ImageButton playBtn;
    private Song boundSong;

    public MiniPlayerBar(final Activity activity) {
        this.activity = activity;
        root = activity.findViewById(R.id.mini_root);
        cover = activity.findViewById(R.id.mini_cover);
        title = activity.findViewById(R.id.mini_title);
        artist = activity.findViewById(R.id.mini_artist);
        playBtn = activity.findViewById(R.id.btn_mini_play);
        ImageButton nextBtn = activity.findViewById(R.id.btn_mini_next);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startActivity(new Intent(activity, PhonePlayerActivity.class));
            }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerController.get().togglePlayPause();
            }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerController.get().playNext();
            }
        });
    }

    public void attach() {
        PlayerController pc = PlayerController.get();
        if (pc != null) pc.addListener(this);
        refresh();
    }

    public void detach() {
        PlayerController pc = PlayerController.get();
        if (pc != null) pc.removeListener(this);
    }

    @Override
    public void onSongChanged(Song song, int index) {
        refresh();
    }

    @Override
    public void onPlaybackStateChanged(boolean playing) {
        refreshIcon();
    }

    @Override
    public void onPlaybackError(String message) {
        // Mini bar stays silent; the player screen surfaces errors.
    }

    private void refresh() {
        PlayerController pc = PlayerController.get();
        Song song = pc == null ? null : pc.currentSong();
        if (song == null) {
            root.setVisibility(View.GONE);
            boundSong = null;
            return;
        }
        root.setVisibility(View.VISIBLE);
        title.setText(song.name == null ? "" : song.name);
        artist.setText(song.artistsText == null ? "" : song.artistsText);
        refreshIcon();
        if (boundSong != song) {
            boundSong = song;
            loadCover(song);
        }
    }

    private void refreshIcon() {
        PlayerController pc = PlayerController.get();
        boolean playing = pc != null && pc.isPlaying();
        playBtn.setImageResource(playing
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    private void loadCover(final Song song) {
        if (song.picUrl != null) {
            Glide.with(activity).load(song.picUrl)
                    .placeholder(R.drawable.cover_placeholder)
                    .error(R.drawable.cover_placeholder)
                    .centerCrop().into(cover);
        } else {
            cover.setImageResource(R.drawable.cover_placeholder);
            MusicRepository.get().resolvePic(song, new MusicRepository.Result<String>() {
                @Override
                public void onSuccess(String url) {
                    if (boundSong == song && !activity.isFinishing()) {
                        Glide.with(activity).load(url)
                                .placeholder(R.drawable.cover_placeholder)
                                .centerCrop().into(cover);
                    }
                }

                @Override
                public void onError(String message) {
                }
            });
        }
    }
}
