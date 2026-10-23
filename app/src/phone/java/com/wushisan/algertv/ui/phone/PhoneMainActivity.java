package com.wushisan.algertv.ui.phone;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import java.util.List;

public class PhoneMainActivity extends AppCompatActivity {

    private MiniPlayerBar miniBar;
    private PhonePlaylistCardsAdapter toplistAdapter;
    private PhonePlaylistCardsAdapter recommendAdapter;
    private LinearLayout toplistRows;

    private final PhonePlaylistCardsAdapter.OnPlaylistClick playlistClick =
            new PhonePlaylistCardsAdapter.OnPlaylistClick() {
                @Override
                public void onPlaylistClick(Playlist playlist) {
                    PhonePlaylistActivity.start(PhoneMainActivity.this, playlist);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_main);

        findViewById(R.id.search_entry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(
                        PhoneMainActivity.this, PhoneSearchActivity.class));
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(
                        PhoneMainActivity.this, PhoneSettingsActivity.class));
            }
        });

        RecyclerView rvTop = findViewById(R.id.rv_toplist);
        rvTop.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        toplistAdapter = new PhonePlaylistCardsAdapter(playlistClick);
        rvTop.setAdapter(toplistAdapter);

        RecyclerView rvRec = findViewById(R.id.rv_recommend);
        rvRec.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        recommendAdapter = new PhonePlaylistCardsAdapter(playlistClick);
        rvRec.setAdapter(recommendAdapter);

        toplistRows = findViewById(R.id.toplist_rows);
        miniBar = new MiniPlayerBar(this);

        // loadHome delivers twice: an instant preliminary paint (disk
        // cache, else the built-in seed) and the refreshed final data.
        loadHome();
        maybeSmokeDirectPlay();
    }

    /** Smoke hook: with --es alger_smoke_direct 1, play a direct-URL track
     *  through the full playback pipeline (data source bypassed — the CI
     *  egress IP is rate-limited there) and open the player screen, whose
     *  400 ms ticker is exactly the poller that used to trigger error -38
     *  during prepare. */
    private void maybeSmokeDirectPlay() {
        if (!"1".equals(getIntent().getStringExtra("alger_smoke_direct"))) return;
        Song s = new Song(
                "direct:https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                "Smoke Direct", "SoundHelix", "", null, null, 0);
        android.util.Log.i("AlgerTV", "SMOKE_DIRECT_PLAY phone");
        java.util.List<Song> q = new java.util.ArrayList<>();
        q.add(s);
        PlayerController.get().playQueue(q, 0);
        startActivity(new android.content.Intent(this, PhonePlayerActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        miniBar.attach();
        PlayerController pc = PlayerController.get();
        if (pc != null && pc.hasQueue()) {
            PhonePerms.requestNotificationsIfNeeded(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        miniBar.detach();
    }

    private boolean smokeOpenedPlaylist = false;

    private void loadHome() {
        MusicRepository.get().loadHome(new MusicRepository.Result<MusicRepository.HomeData>() {
            @Override
            public void onSuccess(MusicRepository.HomeData data) {
                // loadHome delivers up to twice and a delivery can land
                // after this instance was destroyed (rotation recreate in
                // the smoke rig did exactly that): never touch views or
                // Glide with a dead activity.
                if (isFinishing() || isDestroyed()) return;
                bindHome(data);
                // Only the final delivery may complain about the network;
                // the preliminary cache/seed paint must stay silent.
                if (!data.preliminary && data.fromFallback) {
                    Toast.makeText(PhoneMainActivity.this,
                            R.string.phone_net_fallback, Toast.LENGTH_SHORT).show();
                }
                maybeSmokeOpenPlaylist(data);
            }

            @Override
            public void onError(String message) {
                // Data is already on screen (preliminary delivery); the
                // user must never see an error for a populated home.
                android.util.Log.w("AlgerTV", "HOME_LOAD_ERROR: " + message);
            }
        });
    }

    /** Smoke hook (modern-Android playback repro): with --es alger_smoke 1,
     *  open the first chart playlist and let it autoplay its first track,
     *  mirroring a user's tap path Playlist -> track -> Player. */
    private void maybeSmokeOpenPlaylist(MusicRepository.HomeData data) {
        if (smokeOpenedPlaylist) return;
        if (!"1".equals(getIntent().getStringExtra("alger_smoke"))) return;
        if (data.toplists == null || data.toplists.isEmpty()) return;
        smokeOpenedPlaylist = true;
        Playlist first = data.toplists.get(0);
        android.util.Log.i("AlgerTV", "SMOKE_PHONE_OPEN_PLAYLIST id=" + first.id);
        android.content.Intent i = new android.content.Intent(this, PhonePlaylistActivity.class);
        i.putExtra(PhonePlaylistActivity.EXTRA_ID, first.id);
        i.putExtra(PhonePlaylistActivity.EXTRA_NAME, first.name);
        i.putExtra(PhonePlaylistActivity.EXTRA_COVER, first.coverUrl);
        i.putExtra("alger_smoke_play", "1");
        startActivity(i);
    }

    private void bindHome(MusicRepository.HomeData data) {
        toplistAdapter.setItems(data.toplists);
        recommendAdapter.setItems(data.recommend);
        buildToplistRows(data.toplists);
    }

    private void buildToplistRows(List<Playlist> toplists) {
        toplistRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < toplists.size(); i++) {
            final Playlist pl = toplists.get(i);
            View row = inflater.inflate(R.layout.item_phone_toplist_row, toplistRows, false);
            TextView rank = row.findViewById(R.id.row_rank);
            rank.setText(String.valueOf(i + 1));
            // Alger chart language: only the podium wears the brand green.
            rank.setTextColor(getResources().getColor(
                    i < 3 ? R.color.ph_accent : R.color.ph_text_54));
            ((TextView) row.findViewById(R.id.row_name))
                    .setText(pl.name == null ? "" : pl.name);
            StringBuilder meta = new StringBuilder();
            if (pl.trackCount > 0) meta.append(pl.trackCount).append(" 首");
            String plays = Playlist.formatPlayCount(pl.playCount);
            if (plays.length() > 0) {
                if (meta.length() > 0) meta.append(" · ");
                meta.append(plays).append(" 播放");
            }
            ((TextView) row.findViewById(R.id.row_meta)).setText(meta.toString());
            ImageView cover = row.findViewById(R.id.row_cover);
            if (pl.coverUrl != null) {
                int radius = getResources().getDimensionPixelSize(R.dimen.ph_radius_cover);
                Glide.with(getApplicationContext()).load(pl.coverUrl)
                        .placeholder(R.drawable.cover_placeholder)
                        .error(R.drawable.cover_placeholder)
                        .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                                new com.bumptech.glide.load.resource.bitmap.RoundedCorners(radius))
                        .into(cover);
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PhonePlaylistActivity.start(PhoneMainActivity.this, pl);
                }
            });
            toplistRows.addView(row);
        }
    }
}
