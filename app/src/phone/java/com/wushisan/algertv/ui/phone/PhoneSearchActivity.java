package com.wushisan.algertv.ui.phone;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.List;

public class PhoneSearchActivity extends AppCompatActivity {

    /** Same hot keywords as the TV search screen. */
    private static final String[] HOT = {
            "周杰伦", "林俊杰", "李荣浩", "薛之谦", "陈奕迅", "邓紫棋", "五月天", "蔡依林"};

    private EditText input;
    private TextView statusView;
    private PhoneTracksAdapter adapter;
    private MiniPlayerBar miniBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_search);

        input = findViewById(R.id.search_input);
        statusView = findViewById(R.id.search_status);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String kw = v.getText().toString().trim();
                    if (!kw.isEmpty()) doSearch(kw);
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.btn_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String kw = input.getText().toString().trim();
                if (!kw.isEmpty()) doSearch(kw);
            }
        });

        buildHotChips();

        RecyclerView rv = findViewById(R.id.rv_results);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PhoneTracksAdapter(new PhoneTracksAdapter.OnTrackClick() {
            @Override
            public void onTrackClick(Song song, int position) {
                // Adapter items mirror the last result list; rebuild queue from it.
                List<Song> q = lastResults;
                if (q == null || q.isEmpty()) return;
                PlayerController.get().playQueue(q, position);
                startActivity(new Intent(PhoneSearchActivity.this, PhonePlayerActivity.class));
            }
        });
        rv.setAdapter(adapter);

        miniBar = new MiniPlayerBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        miniBar.attach();
    }

    @Override
    protected void onPause() {
        super.onPause();
        miniBar.detach();
    }

    private List<Song> lastResults;

    private void buildHotChips() {
        LinearLayout container = findViewById(R.id.hot_chips);
        LinearLayout row = null;
        for (int i = 0; i < HOT.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.setMargins(0, 10, 0, 0);
                row.setLayoutParams(rlp);
                container.addView(row);
            }
            final String kw = HOT[i];
            TextView chip = new TextView(this);
            chip.setText(kw);
            chip.setTextSize(15);
            chip.setTextColor(getResources().getColor(R.color.text_primary));
            chip.setBackgroundResource(R.drawable.chip_bg);
            chip.setPadding(22, 9, 22, 9);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 10, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(kw);
                    doSearch(kw);
                }
            });
            row.addView(chip);
        }
    }

    private void doSearch(String keyword) {
        statusView.setVisibility(View.VISIBLE);
        statusView.setText(R.string.loading);
        adapter.setItems(null);
        MusicRepository.get().search(keyword, new MusicRepository.Result<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                lastResults = songs;
                adapter.setItems(songs);
                if (songs.isEmpty()) {
                    statusView.setVisibility(View.VISIBLE);
                    statusView.setText(R.string.phone_no_results);
                } else {
                    statusView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String message) {
                statusView.setVisibility(View.VISIBLE);
                statusView.setText(R.string.load_failed);
                Toast.makeText(PhoneSearchActivity.this,
                        R.string.phone_search_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
