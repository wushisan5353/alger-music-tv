package com.wushisan.algertv.ui.phone;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wushisan.algertv.R;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.ArrayList;
import java.util.List;

public class PhoneQueueActivity extends AppCompatActivity {

    private PhoneTracksAdapter adapter;
    private MiniPlayerBar miniBar;

    private final PlayerController.Listener listener = new PlayerController.Listener() {
        @Override
        public void onSongChanged(Song song, int index) {
            adapter.setHighlightIndex(PlayerController.get().getIndex());
            adapter.setItems(new ArrayList<>(PlayerController.get().getQueue()));
            adapter.setHighlightIndex(PlayerController.get().getIndex());
        }

        @Override
        public void onPlaybackStateChanged(boolean playing) {
        }

        @Override
        public void onPlaybackError(String message) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_queue);

        RecyclerView rv = findViewById(R.id.rv_queue);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PhoneTracksAdapter(new PhoneTracksAdapter.OnTrackClick() {
            @Override
            public void onTrackClick(Song song, int position) {
                PlayerController pc = PlayerController.get();
                List<Song> q = new ArrayList<>(pc.getQueue());
                if (!q.isEmpty()) {
                    pc.playQueue(q, position);
                }
                finish();
            }
        });
        rv.setAdapter(adapter);

        PlayerController pc = PlayerController.get();
        adapter.setItems(new ArrayList<>(pc.getQueue()));
        adapter.setHighlightIndex(pc.getIndex());
        int idx = pc.getIndex();
        if (idx > 0) rv.scrollToPosition(idx);

        miniBar = new MiniPlayerBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        miniBar.attach();
        PlayerController.get().addListener(listener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        miniBar.detach();
        PlayerController.get().removeListener(listener);
    }
}
