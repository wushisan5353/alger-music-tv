package com.wushisan.algertv.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.wushisan.algertv.App;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
import com.wushisan.algertv.data.Playlist;
import com.wushisan.algertv.data.Song;
import com.wushisan.algertv.playback.PlayerController;

import java.util.List;

public class MainFragment extends BrowseSupportFragment {

    private static final String NOW_PLAYING_ID = "__now_playing__";
    private static final String SETTINGS_ID = "__settings__";

    private ArrayObjectAdapter rowsAdapter;
    private MusicRepository.HomeData homeData;
    private boolean homeDelivered = false;
    private boolean smokeAutoPlayed = false;
    private String lastNowPlayingSongId = null;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setTitle(getString(R.string.app_name));
        setBrandColor(getResources().getColor(R.color.bg_dark));
        setSearchAffordanceColor(getResources().getColor(R.color.accent));
        setHeadersState(HEADERS_ENABLED);

        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(rowsAdapter);
        showLoadingRow();

        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), SearchActivity.class));
            }
        });
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                      RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Playlist) {
                    Playlist pl = (Playlist) item;
                    if (SETTINGS_ID.equals(pl.id)) {
                        showCustomApiDialog();
                    } else if (NOW_PLAYING_ID.equals(pl.id)) {
                        startActivity(new Intent(getActivity(), PlayerActivity.class));
                    } else {
                        Intent i = new Intent(getActivity(), PlaylistActivity.class);
                        i.putExtra(PlaylistActivity.EXTRA_ID, pl.id);
                        i.putExtra(PlaylistActivity.EXTRA_NAME, pl.name);
                        i.putExtra(PlaylistActivity.EXTRA_COVER, pl.coverUrl);
                        startActivity(i);
                    }
                }
            }
        });

        loadHome();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshNowPlayingRow();
    }

    private void showLoadingRow() {
        rowsAdapter.clear();
        ArrayObjectAdapter loading = new ArrayObjectAdapter(new PlaylistCardPresenter());
        loading.add(new Playlist("loading", getString(R.string.loading), null, 0));
        rowsAdapter.add(new ListRow(new HeaderItem(getString(R.string.app_name)), loading));
    }

    private void loadHome() {
        MusicRepository.get().loadHome(new MusicRepository.Result<MusicRepository.HomeData>() {
            @Override
            public void onSuccess(MusicRepository.HomeData data) {
                homeDelivered = true;
                // loadHome delivers up to twice; the final delivery can
                // land after this fragment detached (cloud smoke caught
                // getString() -> "not attached to a context" FATAL).
                if (!isAdded() || getActivity() == null) return;
                homeData = data;
                buildRowsSafely();
                maybeSmokeAutoPlay(data);
            }

            @Override
            public void onError(String message) {
                homeDelivered = true;
                Log.w(App.TAG, "HOME_LOAD_ERROR: " + message);
                // Never leave an empty home: fall back to the built-in seed
                // rows (names are known offline) and say so.
                if (homeData == null && getActivity() != null) {
                    homeData = MusicRepository.builtInHomeData();
                    homeData.fromFallback = true;
                    buildRowsSafely();
                    Toast.makeText(getActivity(), R.string.load_failed,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        // Watchdog: the loading row must never be permanent. If neither
        // delivery landed within 6s (any platform-specific surprise on
        // the real TV), force the built-in seed rows on screen.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!homeDelivered && isAdded() && homeData == null) {
                            Log.w(App.TAG, "HOME_WATCHDOG_SEED");
                            homeData = MusicRepository.builtInHomeData();
                            homeData.fromFallback = true;
                            buildRowsSafely();
                        }
                    }
                }, 6000);
    }

    /** buildRows with a seatbelt: any failure retries once with seed data. */
    private void buildRowsSafely() {
        try {
            buildRows();
        } catch (Throwable t) {
            Log.e(App.TAG, "HOME_ROWS_BUILD_FAIL: " + t);
            homeData = MusicRepository.builtInHomeData();
            homeData.fromFallback = true;
            try {
                buildRows();
            } catch (Throwable ignored) {
                Log.e(App.TAG, "HOME_ROWS_BUILD_FAIL_SEED: " + ignored);
            }
        }
    }

    private void buildRows() {
        if (homeData == null || !isAdded()) return;
        rowsAdapter.clear();
        lastNowPlayingSongId = null;

        Playlist np = nowPlayingCard();
        if (np != null) {
            ArrayObjectAdapter npAdapter = new ArrayObjectAdapter(new PlaylistCardPresenter());
            npAdapter.add(np);
            rowsAdapter.add(new ListRow(new HeaderItem(getString(R.string.row_now_playing)), npAdapter));
            lastNowPlayingSongId = np.id + "|" + np.name;
        }

        if (!homeData.recommend.isEmpty()) {
            ArrayObjectAdapter recAdapter = new ArrayObjectAdapter(new PlaylistCardPresenter());
            for (Playlist p : homeData.recommend) recAdapter.add(p);
            String title = homeData.fromFallback ? "精选榜单" : getString(R.string.row_recommend);
            rowsAdapter.add(new ListRow(new HeaderItem(title), recAdapter));
        }
        if (!homeData.toplists.isEmpty() && !homeData.fromFallback) {
            ArrayObjectAdapter topAdapter = new ArrayObjectAdapter(new PlaylistCardPresenter());
            for (Playlist p : homeData.toplists) topAdapter.add(p);
            rowsAdapter.add(new ListRow(new HeaderItem(getString(R.string.row_toplist)), topAdapter));
        }

        ArrayObjectAdapter setAdapter = new ArrayObjectAdapter(new PlaylistCardPresenter());
        String apiBase = MusicRepository.getCustomApiBase(getActivity());
        String label = getString(R.string.custom_api)
                + (apiBase == null ? "（" + getString(R.string.custom_api_off) + "）"
                                   : "（" + getString(R.string.custom_api_on) + "）");
        setAdapter.add(new Playlist(SETTINGS_ID, label, null, 0));
        rowsAdapter.add(new ListRow(new HeaderItem(getString(R.string.row_settings)), setAdapter));

        Log.i(App.TAG, "HOME_ROWS_BUILT fallback=" + homeData.fromFallback);
    }

    /** 设置 → 自定义 API 地址: user's own NeteaseCloudMusicApi base URL (optional). */
    private void showCustomApiDialog() {
        if (getActivity() == null) return;
        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("http://192.168.1.10:3000");
        String current = MusicRepository.getCustomApiBase(getActivity());
        if (current != null) input.setText(current);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.custom_api)
                .setMessage(R.string.custom_api_hint)
                .setView(input)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MusicRepository.setCustomApiBase(getActivity(),
                                input.getText().toString());
                        Toast.makeText(getActivity(), R.string.saved, Toast.LENGTH_SHORT).show();
                        buildRows();
                    }
                })
                .setNeutralButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MusicRepository.setCustomApiBase(getActivity(), null);
                        Toast.makeText(getActivity(), R.string.saved, Toast.LENGTH_SHORT).show();
                        buildRows();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private Playlist nowPlayingCard() {
        PlayerController pc = PlayerController.get();
        if (pc == null || !pc.hasQueue()) return null;
        Song s = pc.currentSong();
        if (s == null) return null;
        Playlist pl = new Playlist(NOW_PLAYING_ID,
                (pc.isPlaying() ? "▶ " : "❚❚ ") + s.name,
                s.picUrl, 0);
        return pl;
    }

    private void refreshNowPlayingRow() {
        if (homeData == null) return;
        Playlist np = nowPlayingCard();
        String key = np == null ? null : np.id + "|" + np.name;
        if (key == null ? lastNowPlayingSongId != null : !key.equals(lastNowPlayingSongId)) {
            buildRows();
        }
    }

    /** Smoke-test hook: with --es alger_smoke 1, autoplay the first playlist's first tracks. */
    private void maybeSmokeAutoPlay(MusicRepository.HomeData data) {
        if (smokeAutoPlayed) return;
        Intent intent = getActivity() == null ? null : getActivity().getIntent();
        if (intent == null || !"1".equals(intent.getStringExtra("alger_smoke"))) return;
        if (data.recommend.isEmpty()) return;
        smokeAutoPlayed = true;
        final Playlist first = data.recommend.get(0);
        Log.i(App.TAG, "SMOKE_AUTOPLAY playlist=" + first.id);
        MusicRepository.get().loadPlaylistTracks(first.id,
                new MusicRepository.Result<List<Song>>() {
                    @Override
                    public void onSuccess(List<Song> songs) {
                        if (songs != null && !songs.isEmpty()) {
                            PlayerController.get().playQueue(songs, 0);
                            Log.i(App.TAG, "SMOKE_AUTOPLAY_OK n=" + songs.size());
                        } else {
                            Log.w(App.TAG, "SMOKE_AUTOPLAY_EMPTY");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(App.TAG, "SMOKE_AUTOPLAY_FAIL: " + message);
                    }
                });
    }
}
