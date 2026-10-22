package com.wushisan.algertv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.app.VerticalGridSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;

import com.bumptech.glide.Glide;
import com.wushisan.algertv.App;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.ArrayList;
import java.util.List;

public class PlaylistActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "playlist_id";
    public static final String EXTRA_NAME = "playlist_name";
    public static final String EXTRA_COVER = "playlist_cover";

    private String playlistId;
    private String playlistName;
    private String coverUrl;

    private ImageView coverView;
    private TextView nameView;
    private TextView metaView;
    private ArrayObjectAdapter adapter;
    private List<Song> tracks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        playlistId = getIntent().getStringExtra(EXTRA_ID);
        playlistName = getIntent().getStringExtra(EXTRA_NAME);
        coverUrl = getIntent().getStringExtra(EXTRA_COVER);

        coverView = findViewById(R.id.pl_cover);
        nameView = findViewById(R.id.pl_name);
        metaView = findViewById(R.id.pl_meta);
        nameView.setText(playlistName == null ? "" : playlistName);
        metaView.setText(R.string.loading);
        if (coverUrl != null) {
            Glide.with(this).load(coverUrl)
                    .placeholder(R.drawable.cover_placeholder)
                    .error(R.drawable.cover_placeholder)
                    .centerCrop().into(coverView);
        }

        adapter = new ArrayObjectAdapter(new TrackRowPresenter());
        VerticalGridSupportFragment grid = new VerticalGridSupportFragment();
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
        gridPresenter.setNumberOfColumns(1);
        grid.setGridPresenter(gridPresenter);
        grid.setAdapter(adapter);
        grid.setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof TrackRowPresenter.PlayAll) {
                    playFrom(0);
                } else if (item instanceof TrackRowPresenter.Message) {
                    loadTracks();
                } else if (item instanceof Song) {
                    Song s = (Song) item;
                    playFrom(Math.max(0, s.rowIndex));
                }
            }
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.tracks_container, grid)
                    .commit();
        }

        loadTracks();
    }

    private void loadTracks() {
        metaView.setText(R.string.loading);
        adapter.clear();
        adapter.add(new TrackRowPresenter.Message(getString(R.string.loading)));
        MusicRepository.get().loadPlaylistTracks(playlistId,
                new MusicRepository.Result<List<Song>>() {
                    @Override
                    public void onSuccess(List<Song> songs) {
                        tracks = songs;
                        for (int i = 0; i < tracks.size(); i++) tracks.get(i).rowIndex = i;
                        adapter.clear();
                        adapter.add(new TrackRowPresenter.PlayAll(tracks.size()));
                        for (Song s : tracks) adapter.add(s);
                        metaView.setText("共 " + tracks.size() + " 首 · 选中任意一首开始播放");
                        fillHeaderCoverIfNeeded();
                        Log.i(App.TAG, "PLAYLIST_UI_OK id=" + playlistId + " n=" + tracks.size());
                    }

                    @Override
                    public void onError(String message) {
                        adapter.clear();
                        adapter.add(new TrackRowPresenter.Message(getString(R.string.load_failed)));
                        metaView.setText("加载失败");
                        Log.w(App.TAG, "PLAYLIST_UI_FAIL id=" + playlistId + ": " + message);
                    }
                });
    }

    private void fillHeaderCoverIfNeeded() {
        if (coverUrl != null || tracks.isEmpty()) return;
        final Song first = tracks.get(0);
        if (first.picUrl != null) {
            Glide.with(this).load(first.picUrl).centerCrop().into(coverView);
            return;
        }
        MusicRepository.get().resolvePic(first, new MusicRepository.Result<String>() {
            @Override
            public void onSuccess(String url) {
                if (!isFinishing()) Glide.with(PlaylistActivity.this).load(url)
                        .centerCrop().into(coverView);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void playFrom(int index) {
        if (tracks.isEmpty()) return;
        PlayerController.get().playQueue(tracks, index);
        startActivity(new Intent(this, PlayerActivity.class));
        Toast.makeText(this, "播放：" + tracks.get(index).name, Toast.LENGTH_SHORT).show();
    }
}
