package com.wushisan.algertv.ui.phone;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Playlist;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.ArrayList;
import java.util.List;

public class PhonePlaylistActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "playlist_id";
    public static final String EXTRA_NAME = "playlist_name";
    public static final String EXTRA_COVER = "playlist_cover";

    public static void start(Context ctx, Playlist pl) {
        Intent i = new Intent(ctx, PhonePlaylistActivity.class);
        i.putExtra(EXTRA_ID, pl.id);
        i.putExtra(EXTRA_NAME, pl.name);
        i.putExtra(EXTRA_COVER, pl.coverUrl);
        ctx.startActivity(i);
    }

    private String playlistId;
    private ImageView coverView;
    private TextView nameView;
    private TextView metaView;
    private TextView statusView;
    private PhoneTracksAdapter adapter;
    private MiniPlayerBar miniBar;
    private List<Song> tracks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_playlist);

        playlistId = getIntent().getStringExtra(EXTRA_ID);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String cover = getIntent().getStringExtra(EXTRA_COVER);

        coverView = findViewById(R.id.pl_cover);
        nameView = findViewById(R.id.pl_name);
        metaView = findViewById(R.id.pl_meta);
        statusView = findViewById(R.id.pl_status);
        nameView.setText(name == null ? "" : name);
        if (cover != null) {
            int radius = getResources().getDimensionPixelSize(R.dimen.ph_radius_card);
            Glide.with(this).load(cover)
                    .placeholder(R.drawable.cover_placeholder)
                    .error(R.drawable.cover_placeholder)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                            new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                    .into(coverView);
        }
        findViewById(R.id.btn_play_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playFrom(0);
            }
        });
        statusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tracks.isEmpty()) return;
                loadTracks();
            }
        });

        RecyclerView rv = findViewById(R.id.rv_tracks);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PhoneTracksAdapter(new PhoneTracksAdapter.OnTrackClick() {
            @Override
            public void onTrackClick(Song song, int position) {
                playFrom(position);
            }
        });
        rv.setAdapter(adapter);

        miniBar = new MiniPlayerBar(this);
        loadTracks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        miniBar.attach();
        syncHighlight();
    }

    @Override
    protected void onPause() {
        super.onPause();
        miniBar.detach();
    }

    private void syncHighlight() {
        PlayerController pc = PlayerController.get();
        int hl = -1;
        if (pc != null && pc.hasQueue() && pc.getQueue() == tracks) {
            hl = pc.getIndex();
        } else if (pc != null && pc.hasQueue()) {
            // The queue may be a copy of this list (playQueue copies); match by song id.
            Song cur = pc.currentSong();
            if (cur != null) {
                for (int i = 0; i < tracks.size(); i++) {
                    if (tracks.get(i).id != null && tracks.get(i).id.equals(cur.id)) {
                        hl = i;
                        break;
                    }
                }
            }
        }
        adapter.setHighlightIndex(hl);
    }

    private void loadTracks() {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(R.string.loading);
        MusicRepository.get().loadPlaylistTracks(playlistId,
                new MusicRepository.Result<List<Song>>() {
                    @Override
                    public void onSuccess(List<Song> songs) {
                        tracks = songs;
                        adapter.setItems(tracks);
                        statusView.setVisibility(View.GONE);
                        metaView.setText(getString(R.string.phone_tracks_count, tracks.size()));
                        syncHighlight();
                        maybeSmokePlay();
                    }

                    @Override
                    public void onError(String message) {
                        statusView.setVisibility(View.VISIBLE);
                        statusView.setText(R.string.load_failed);
                        metaView.setText("");
                    }
                });
    }

    private boolean smokePlayed = false;

    /** Smoke hook: with extra alger_smoke_play=1, tap the first track —
     *  the exact user path that crashed phone 1.0 on modern Android. */
    private void maybeSmokePlay() {
        if (smokePlayed) return;
        if (!"1".equals(getIntent().getStringExtra("alger_smoke_play"))) return;
        if (tracks.isEmpty()) return;
        smokePlayed = true;
        android.util.Log.i("AlgerTV", "SMOKE_PHONE_PLAY first track");
        playFrom(0);
    }

    private void playFrom(int index) {
        if (tracks.isEmpty()) {
            Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
            return;
        }
        PlayerController.get().playQueue(tracks, index);
        startActivity(new Intent(this, PhonePlayerActivity.class));
    }
}
