package com.wushisan.algertv.ui;

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
import androidx.leanback.app.VerticalGridSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;

import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private static final String[] HOT = {
            "周杰伦", "林俊杰", "李荣浩", "薛之谦", "陈奕迅", "邓紫棋", "五月天", "蔡依林"};

    private EditText input;
    private ArrayObjectAdapter adapter;
    private List<Song> results = new ArrayList<>();
    private VerticalGridSupportFragment gridFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        input = findViewById(R.id.search_input);
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

        LinearLayout hotRow = findViewById(R.id.hot_row);
        for (final String kw : HOT) {
            TextView chip = new TextView(this);
            chip.setText(kw);
            chip.setTextSize(17);
            chip.setTextColor(getResources().getColor(R.color.text_primary));
            chip.setBackgroundResource(R.drawable.chip_bg);
            chip.setFocusable(true);
            chip.setFocusableInTouchMode(true);
            chip.setPadding(26, 10, 26, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 14, 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(kw);
                    doSearch(kw);
                }
            });
            hotRow.addView(chip);
        }

        adapter = new ArrayObjectAdapter(new TrackRowPresenter());
        gridFragment = new VerticalGridSupportFragment();
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
        gridPresenter.setNumberOfColumns(1);
        gridFragment.setGridPresenter(gridPresenter);
        gridFragment.setAdapter(adapter);
        gridFragment.setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Song) {
                    Song s = (Song) item;
                    int idx = Math.max(0, s.rowIndex);
                    PlayerController.get().playQueue(results, idx);
                    startActivity(new Intent(SearchActivity.this, PlayerActivity.class));
                }
            }
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.results_container, gridFragment)
                    .commit();
        }

        input.requestFocus();
    }

    private void doSearch(String keyword) {
        adapter.clear();
        adapter.add(new TrackRowPresenter.Message(getString(R.string.loading)));
        MusicRepository.get().search(keyword, new MusicRepository.Result<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                results = songs;
                for (int i = 0; i < results.size(); i++) results.get(i).rowIndex = i;
                adapter.clear();
                if (results.isEmpty()) {
                    adapter.add(new TrackRowPresenter.Message("没有找到相关歌曲"));
                } else {
                    for (Song s : results) adapter.add(s);
                    focusResults();
                }
            }

            @Override
            public void onError(String message) {
                adapter.clear();
                adapter.add(new TrackRowPresenter.Message(getString(R.string.load_failed)));
                Toast.makeText(SearchActivity.this, "搜索失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void focusResults() {
        // VerticalGridSupportFragment (leanback 1.0.0) has no getGridView();
        // find the VerticalGridView inside the fragment's view tree instead.
        View root = gridFragment.getView();
        View grid = findGridView(root);
        if (grid != null) {
            grid.requestFocus();
        } else if (root != null) {
            root.requestFocus();
        }
    }

    private static View findGridView(View v) {
        if (v == null) return null;
        if (v instanceof androidx.leanback.widget.VerticalGridView) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findGridView(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }
}
