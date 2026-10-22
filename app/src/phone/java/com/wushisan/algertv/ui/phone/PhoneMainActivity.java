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

        // Instant paint with the curated built-ins; metadata refreshes async.
        MusicRepository.HomeData seed = new MusicRepository.HomeData();
        seed.recommend = MusicRepository.builtInRecommend();
        seed.toplists = MusicRepository.builtInToplists();
        bindHome(seed);
        loadHome();
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

    private void loadHome() {
        MusicRepository.get().loadHome(new MusicRepository.Result<MusicRepository.HomeData>() {
            @Override
            public void onSuccess(MusicRepository.HomeData data) {
                bindHome(data);
                if (data.fromFallback) {
                    Toast.makeText(PhoneMainActivity.this,
                            R.string.phone_net_fallback, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(PhoneMainActivity.this,
                        R.string.phone_net_fallback, Toast.LENGTH_SHORT).show();
            }
        });
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
            ((TextView) row.findViewById(R.id.row_rank)).setText(String.valueOf(i + 1));
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
                Glide.with(this).load(pl.coverUrl)
                        .placeholder(R.drawable.cover_placeholder)
                        .error(R.drawable.cover_placeholder)
                        .centerCrop().into(cover);
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
