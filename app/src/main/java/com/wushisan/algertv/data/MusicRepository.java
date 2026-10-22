package com.wushisan.algertv.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wushisan.algertv.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Data layer.
 *
 * Primary (and only built-in) source: GDStudio API
 * (https://music-api.gdstudio.xyz/api.php, source=netease) — search / playlist /
 * play URL (320k) / lyric / cover pic, all verified end to end.
 *
 * The former primary source mc.alger.fun is DEAD: its public NeteaseCloudMusicApi
 * server was banned by NetEase and the upstream author has discontinued the
 * mobile backend (AlgerMusicPlayer issues #698/#748/#764). It is therefore no
 * longer called by default. A user may still enter their OWN self-hosted
 * NeteaseCloudMusicApi base URL under 设置 → 自定义 API 地址; only then is that
 * custom source used (with GDStudio as fallback).
 *
 * Home is filled directly from GDStudio: curated official NetEase chart
 * playlists (IDs verified live on 2026-10-22, see CURATED_* below).
 *
 * All callbacks are delivered on the main thread.
 */
public class MusicRepository {

    public interface Result<T> {
        void onSuccess(T value);

        void onError(String message);
    }

    public static class HomeData {
        public List<Playlist> recommend = new ArrayList<>();
        public List<Playlist> toplists = new ArrayList<>();
        /** True only when every metadata request failed (names-only built-ins). */
        public boolean fromFallback = false;
        /**
         * True for the instant first delivery (disk cache or built-in seed):
         * the UI should paint it immediately but keep quiet (no error toast);
         * a final delivery with preliminary=false follows once the network
         * refresh settles.
         */
        public boolean preliminary = false;
        /** True when the preliminary delivery came from the disk cache. */
        public boolean fromCache = false;
    }

    private static final String GDS_BASE = "https://music-api.gdstudio.xyz/api.php";
    private static final String UA = "Mozilla/5.0 (Linux; Android 5.1.1) AlgerTV/1.0";

    private static final String PREFS = "algertv_prefs";
    private static final String KEY_CUSTOM_API = "custom_api_base";

    /**
     * Curated evergreen official NetEase playlists for the home rows.
     * Verified live via GDStudio on 2026-10-22 (name / trackCount / playCount):
     *   热歌榜 3778678 (200 tracks, 142亿 plays), 飙升榜 19723756 (100, 65亿),
     *   新歌榜 3779629 (100, 32亿), 原创榜 2884035 (100, 6.2亿),
     *   网易云中文说唱榜 991319590 (50, 5.5亿),
     *   美国Billboard榜 60198 (93, 6.5亿), 网易云欧美热歌榜 2809513713 (200, 2.5亿),
     *   网易云电音榜 1978921795 (50, 4.1亿), 网易云韩语榜 745956260 (100, 1.3亿),
     *   UK排行榜周榜 180106 (88, 1.6亿), 美国热门歌曲 11641012 (89, 1.1亿),
     *   网易云古典榜 71384707 (100, 0.76亿), 日本Oricon榜 60131 (23, 0.69亿).
     * All are NetEase-official chart playlists (userId=1), so they stay alive
     * and keep updating; names/covers/playCounts are refreshed from the server
     * on every home load, the literals here are only offline fallbacks.
     */
    private static final String[][] CURATED_TOPLISTS = {
            {"3778678", "热歌榜"},
            {"19723756", "飙升榜"},
            {"3779629", "新歌榜"},
            {"2884035", "原创榜"},
            {"991319590", "网易云中文说唱榜"},
    };
    private static final String[][] CURATED_RECOMMEND = {
            {"60198", "美国Billboard榜"},
            {"2809513713", "网易云欧美热歌榜"},
            {"1978921795", "网易云电音榜"},
            {"745956260", "网易云韩语榜"},
            {"180106", "UK排行榜周榜"},
            {"11641012", "美国热门歌曲"},
            {"71384707", "网易云古典榜"},
            {"60131", "日本Oricon榜"},
    };

    private static MusicRepository sInstance;

    private final OkHttpClient clientGds;
    private final OkHttpClient clientCustom;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> picCache = new HashMap<>();
    private Context appContext;

    public static synchronized void init(Context ctx) {
        if (sInstance == null) sInstance = new MusicRepository();
        if (ctx != null) sInstance.appContext = ctx.getApplicationContext();
    }

    public static MusicRepository get() {
        return sInstance;
    }

    private MusicRepository() {
        clientGds = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build();
        // Custom source is user-hosted: fail fast so GDStudio fallback is snappy.
        clientCustom = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }

    // ------------------------------------------------------- Custom API pref

    public static String getCustomApiBase(Context ctx) {
        if (ctx == null) return null;
        String v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_API, null);
        return (v == null || v.trim().length() == 0) ? null : v.trim();
    }

    public static void setCustomApiBase(Context ctx, String base) {
        if (ctx == null) return;
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (base == null || base.trim().length() == 0) {
            e.remove(KEY_CUSTOM_API);
        } else {
            String b = base.trim();
            while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
            e.putString(KEY_CUSTOM_API, b);
        }
        e.apply();
    }

    /** Configured custom NeteaseCloudMusicApi base URL, or null when off. */
    private String customBase() {
        return appContext == null ? null : getCustomApiBase(appContext);
    }

    public static List<Playlist> builtInToplists() {
        return toPlaylists(CURATED_TOPLISTS);
    }

    public static List<Playlist> builtInRecommend() {
        return toPlaylists(CURATED_RECOMMEND);
    }

    private static List<Playlist> toPlaylists(String[][] defs) {
        List<Playlist> list = new ArrayList<>();
        for (String[] d : defs) list.add(new Playlist(d[0], d[1], null, 0));
        return list;
    }

    // ------------------------------------------------------------------ HTTP

    private void getJson(OkHttpClient client, String url, final Result<JSONObject> cb) {
        Request req = new Request.Builder().url(url).header("User-Agent", UA).build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(cb, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        deliverError(cb, "HTTP " + response.code());
                        return;
                    }
                    String body = response.body().string();
                    final JSONObject json = new JSONObject(body);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onSuccess(json);
                        }
                    });
                } catch (Exception e) {
                    deliverError(cb, "parse: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private <T> void deliverError(final Result<T> cb, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ------------------------------------------------------------------ Home

    private static final String HOME_CACHE_FILE = "home_cache.json";
    private static final long HOME_META_RETRY_DELAY_MS = 800;

    /** Built-in seed home (names only, no covers) — always available instantly. */
    public static HomeData builtInHomeData() {
        HomeData d = new HomeData();
        d.recommend = builtInRecommend();
        d.toplists = builtInToplists();
        d.preliminary = true;
        return d;
    }

    /**
     * Home loading, two deliveries (both via onSuccess, on the main thread):
     *  1. Instant preliminary delivery — the disk cache when present, else the
     *     built-in seed — so the first frame after install is never an empty
     *     screen (the pre-1.0.3 code waited for all 13 full-playlist JSONs
     *     before painting anything, which on a cold network looked like
     *     "first launch has no list").
     *  2. Final delivery once every metadata request has settled (each failed
     *     request is retried once); fresh metadata is written to the disk
     *     cache for the next launch.
     */
    public void loadHome(final Result<HomeData> cb) {
        final long t0 = System.currentTimeMillis();
        Log.i(App.TAG, "HOME_LOAD_START");
        HomeData cached = readHomeCache();
        final HomeData instant = cached != null ? cached : builtInHomeData();
        Log.i(App.TAG, cached != null ? "HOME_CACHE_HIT" : "HOME_SEED");
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onSuccess(instant);
            }
        });

        final List<Playlist> rec = builtInRecommend();
        final List<Playlist> top = builtInToplists();
        final List<Playlist> all = new ArrayList<>();
        all.addAll(rec);
        all.addAll(top);
        final AtomicInteger pending = new AtomicInteger(all.size());
        final AtomicInteger okCount = new AtomicInteger(0);
        for (final Playlist pl : all) {
            fetchHomeMeta(pl, 0, new Runnable() {
                @Override
                public void run() {
                    if (pending.decrementAndGet() == 0) {
                        HomeData data = new HomeData();
                        data.recommend = rec;
                        data.toplists = top;
                        data.preliminary = false;
                        data.fromFallback = okCount.get() == 0;
                        Log.i(App.TAG, "HOME_DONE ms=" + (System.currentTimeMillis() - t0)
                                + " meta=" + okCount.get() + "/" + all.size()
                                + " fallback=" + data.fromFallback);
                        if (okCount.get() > 0) writeHomeCache(rec, top);
                        cb.onSuccess(data);
                    }
                }
            }, okCount, t0);
        }
    }

    /** One playlist's home metadata; retries exactly once on failure. */
    private void fetchHomeMeta(final Playlist pl, final int attempt,
                               final Runnable done, final AtomicInteger okCount,
                               final long t0) {
        getJson(clientGds, GDS_BASE + "?types=playlist&source=netease&id=" + pl.id,
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        JSONObject meta = json.optJSONObject("playlist");
                        if (meta != null) {
                            String name = optStr(meta, "name");
                            if (name != null) pl.name = name;
                            pl.coverUrl = optStr(meta, "coverImgUrl");
                            pl.playCount = meta.optLong("playCount");
                            pl.trackCount = meta.optInt("trackCount");
                            okCount.incrementAndGet();
                            Log.i(App.TAG, "HOME_META_OK id=" + pl.id
                                    + " ms=" + (System.currentTimeMillis() - t0));
                        } else {
                            Log.w(App.TAG, "HOME_META_EMPTY id=" + pl.id);
                        }
                        done.run();
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(App.TAG, "HOME_META_FAIL id=" + pl.id
                                + " attempt=" + attempt + ": " + message);
                        if (attempt == 0) {
                            mainHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    fetchHomeMeta(pl, 1, done, okCount, t0);
                                }
                            }, HOME_META_RETRY_DELAY_MS);
                        } else {
                            done.run();
                        }
                    }
                });
    }

    // ------------------------------------------------------- Home disk cache

    private HomeData readHomeCache() {
        if (appContext == null) return null;
        try {
            java.io.File f = new java.io.File(appContext.getFilesDir(), HOME_CACHE_FILE);
            if (!f.exists() || f.length() == 0) return null;
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            JSONObject root = new JSONObject(new String(buf, 0, off, "UTF-8"));
            HomeData d = new HomeData();
            d.recommend = parseCachedPlaylists(root.optJSONArray("rec"));
            d.toplists = parseCachedPlaylists(root.optJSONArray("top"));
            if (d.recommend.isEmpty() || d.toplists.isEmpty()) return null;
            d.preliminary = true;
            d.fromCache = true;
            return d;
        } catch (Exception e) {
            Log.w(App.TAG, "HOME_CACHE_READ_FAIL: " + e.getMessage());
            return null;
        }
    }

    private List<Playlist> parseCachedPlaylists(JSONArray arr) {
        List<Playlist> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Playlist p = new Playlist();
            p.id = o.optString("id");
            p.name = o.optString("name");
            p.coverUrl = optStr(o, "cover");
            p.playCount = o.optLong("play");
            p.trackCount = o.optInt("tracks");
            if (p.id != null && p.id.length() > 0) out.add(p);
        }
        return out;
    }

    private void writeHomeCache(final List<Playlist> rec, final List<Playlist> top) {
        if (appContext == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("ts", System.currentTimeMillis());
            root.put("rec", cacheArray(rec));
            root.put("top", cacheArray(top));
            final String text = root.toString();
            final java.io.File f = new java.io.File(appContext.getFilesDir(), HOME_CACHE_FILE);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                        out.write(text.getBytes("UTF-8"));
                        out.close();
                        Log.i(App.TAG, "HOME_CACHE_SAVED bytes=" + text.length());
                    } catch (Exception e) {
                        Log.w(App.TAG, "HOME_CACHE_WRITE_FAIL: " + e.getMessage());
                    }
                }
            }, "home-cache-writer").start();
        } catch (Exception e) {
            Log.w(App.TAG, "HOME_CACHE_SERIALIZE_FAIL: " + e.getMessage());
        }
    }

    private JSONArray cacheArray(List<Playlist> list) throws Exception {
        JSONArray arr = new JSONArray();
        for (Playlist p : list) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("name", p.name == null ? "" : p.name);
            if (p.coverUrl != null) o.put("cover", p.coverUrl);
            o.put("play", p.playCount);
            o.put("tracks", p.trackCount);
            arr.put(o);
        }
        return arr;
    }

    // -------------------------------------------------------------- Playlist

    public void loadPlaylistTracks(final String playlistId, final Result<List<Song>> cb) {
        String base = customBase();
        if (base != null) {
            loadPlaylistTracksCustom(base, playlistId, new Result<List<Song>>() {
                @Override
                public void onSuccess(List<Song> songs) {
                    cb.onSuccess(songs);
                }

                @Override
                public void onError(String message) {
                    Log.w(App.TAG, "PLAYLIST_CUSTOM_FAIL id=" + playlistId + " -> GDS: " + message);
                    loadPlaylistTracksGds(playlistId, cb);
                }
            });
        } else {
            loadPlaylistTracksGds(playlistId, cb);
        }
    }

    /** GDStudio playlist returns the full NetEase playlist JSON (tracks included). */
    private void loadPlaylistTracksGds(final String playlistId, final Result<List<Song>> cb) {
        getJson(clientGds, GDS_BASE + "?types=playlist&source=netease&id=" + playlistId,
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        List<Song> songs = new ArrayList<>();
                        try {
                            JSONObject pl = json.optJSONObject("playlist");
                            if (pl != null) {
                                JSONArray tracks = pl.optJSONArray("tracks");
                                if (tracks != null) {
                                    for (int i = 0; i < tracks.length(); i++) {
                                        Song s = parseNetEaseTrack(tracks.optJSONObject(i));
                                        if (s != null) songs.add(s);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        Log.i(App.TAG, "PLAYLIST_GDS_OK id=" + playlistId + " tracks=" + songs.size());
                        if (songs.isEmpty()) cb.onError("歌单为空或加载失败");
                        else cb.onSuccess(songs);
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(App.TAG, "PLAYLIST_GDS_FAIL id=" + playlistId + ": " + message);
                        cb.onError(message);
                    }
                });
    }

    /** Self-hosted NeteaseCloudMusicApi: /playlist/detail (+ /playlist/track/all). */
    private void loadPlaylistTracksCustom(final String base, final String playlistId,
                                          final Result<List<Song>> cb) {
        getJson(clientCustom, base + "/playlist/detail?id=" + playlistId, new Result<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                final List<Song> songs = new ArrayList<>();
                int trackCount = 0;
                try {
                    JSONObject pl = json.optJSONObject("playlist");
                    if (pl != null) {
                        trackCount = pl.optInt("trackCount");
                        JSONArray tracks = pl.optJSONArray("tracks");
                        if (tracks != null) {
                            for (int i = 0; i < tracks.length(); i++) {
                                Song s = parseNetEaseTrack(tracks.optJSONObject(i));
                                if (s != null) songs.add(s);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                if (!songs.isEmpty() && (trackCount == 0 || songs.size() >= trackCount)) {
                    Log.i(App.TAG, "PLAYLIST_CUSTOM_OK id=" + playlistId + " tracks=" + songs.size());
                    cb.onSuccess(songs);
                } else if (!songs.isEmpty()) {
                    final List<Song> partial = songs;
                    getJson(clientCustom, base + "/playlist/track/all?id=" + playlistId + "&limit=500",
                            new Result<JSONObject>() {
                                @Override
                                public void onSuccess(JSONObject j2) {
                                    List<Song> allSongs = new ArrayList<>();
                                    try {
                                        JSONArray arr = j2.optJSONArray("songs");
                                        if (arr != null) {
                                            for (int i = 0; i < arr.length(); i++) {
                                                Song s = parseNetEaseTrack(arr.optJSONObject(i));
                                                if (s != null) allSongs.add(s);
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                    cb.onSuccess(allSongs.size() > partial.size() ? allSongs : partial);
                                }

                                @Override
                                public void onError(String message) {
                                    cb.onSuccess(partial);
                                }
                            });
                } else {
                    cb.onError("custom playlist empty");
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    // --------------------------------------------------------------- Search

    public void search(final String keyword, final Result<List<Song>> cb) {
        String base = customBase();
        if (base != null) {
            searchCustom(base, keyword, new Result<List<Song>>() {
                @Override
                public void onSuccess(List<Song> songs) {
                    cb.onSuccess(songs);
                }

                @Override
                public void onError(String message) {
                    Log.w(App.TAG, "SEARCH_CUSTOM_FAIL kw=" + keyword + " -> GDS: " + message);
                    searchGds(keyword, cb);
                }
            });
        } else {
            searchGds(keyword, cb);
        }
    }

    private void searchGds(final String keyword, final Result<List<Song>> cb) {
        getArrayGds(GDS_BASE + "?types=search&source=netease&name=" + enc(keyword) + "&count=30&page=1",
                new Result<JSONArray>() {
                    @Override
                    public void onSuccess(JSONArray arr) {
                        List<Song> songs = parseSongsGds(arr);
                        Log.i(App.TAG, "SEARCH_GDS_OK kw=" + keyword + " n=" + songs.size());
                        cb.onSuccess(songs);
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(App.TAG, "SEARCH_GDS_FAIL kw=" + keyword + ": " + message);
                        cb.onError(message);
                    }
                });
    }

    private void searchCustom(String base, final String keyword, final Result<List<Song>> cb) {
        getJson(clientCustom, base + "/search?keywords=" + enc(keyword) + "&limit=30",
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        List<Song> songs = new ArrayList<>();
                        try {
                            JSONObject result = json.optJSONObject("result");
                            JSONArray arr = result == null ? null : result.optJSONArray("songs");
                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
                                    Song s = parseSearchSongCustom(arr.optJSONObject(i));
                                    if (s != null) songs.add(s);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        Log.i(App.TAG, "SEARCH_CUSTOM_OK kw=" + keyword + " n=" + songs.size());
                        cb.onSuccess(songs);
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    // ------------------------------------------------------------ Play / LRC

    public void resolvePlayUrl(final Song song, final Result<String> cb) {
        String base = customBase();
        if (base != null) {
            resolvePlayUrlCustom(base, song, new Result<String>() {
                @Override
                public void onSuccess(String url) {
                    cb.onSuccess(url);
                }

                @Override
                public void onError(String message) {
                    Log.w(App.TAG, "URL_CUSTOM_FAIL id=" + song.id + " -> GDS: " + message);
                    resolvePlayUrlGds(song, cb);
                }
            });
        } else {
            resolvePlayUrlGds(song, cb);
        }
    }

    private void resolvePlayUrlGds(final Song song, final Result<String> cb) {
        getJson(clientGds, GDS_BASE + "?types=url&source=netease&id=" + song.id + "&br=320",
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        String url = optStr(json, "url");
                        if (url != null && url.length() > 0) {
                            Log.i(App.TAG, "URL_GDS_OK id=" + song.id);
                            cb.onSuccess(url);
                        } else {
                            cb.onError("无播放地址（可能无版权）");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    private void resolvePlayUrlCustom(String base, final Song song, final Result<String> cb) {
        getJson(clientCustom, base + "/song/url/v1?id=" + song.id + "&level=exhigh",
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        String url = null;
                        try {
                            JSONArray data = json.optJSONArray("data");
                            if (data != null && data.length() > 0) {
                                JSONObject first = data.optJSONObject(0);
                                if (first != null && !first.isNull("url")) {
                                    url = first.optString("url");
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        if (url != null && url.length() > 0) {
                            Log.i(App.TAG, "URL_CUSTOM_OK id=" + song.id);
                            cb.onSuccess(url);
                        } else {
                            cb.onError("custom url null");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    public void loadLyric(final Song song, final Result<String> cb) {
        String base = customBase();
        if (base != null) {
            loadLyricCustom(base, song, new Result<String>() {
                @Override
                public void onSuccess(String lyric) {
                    cb.onSuccess(lyric);
                }

                @Override
                public void onError(String message) {
                    loadLyricGds(song, cb);
                }
            });
        } else {
            loadLyricGds(song, cb);
        }
    }

    private void loadLyricGds(final Song song, final Result<String> cb) {
        getJson(clientGds, GDS_BASE + "?types=lyric&source=netease&id=" + song.id,
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        String lyric = optStr(json, "lyric");
                        if (lyric != null && lyric.trim().length() > 0) {
                            Log.i(App.TAG, "LYRIC_GDS_OK id=" + song.id);
                            cb.onSuccess(lyric);
                        } else {
                            cb.onError("无歌词");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    private void loadLyricCustom(String base, final Song song, final Result<String> cb) {
        getJson(clientCustom, base + "/lyric?id=" + song.id, new Result<JSONObject>() {
            @Override
            public void onSuccess(JSONObject json) {
                String lyric = null;
                try {
                    JSONObject lrc = json.optJSONObject("lrc");
                    if (lrc != null) lyric = optStr(lrc, "lyric");
                } catch (Exception ignored) {
                }
                if (lyric != null && lyric.trim().length() > 0) {
                    Log.i(App.TAG, "LYRIC_CUSTOM_OK id=" + song.id);
                    cb.onSuccess(lyric);
                } else {
                    cb.onError("custom lyric empty");
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** Resolve a GDStudio song's cover URL via the pic API (cached). */
    public void resolvePic(final Song song, final Result<String> cb) {
        if (song.picUrl != null && song.picUrl.length() > 0) {
            cb.onSuccess(song.picUrl);
            return;
        }
        if (song.picId == null) {
            cb.onError("no pic_id");
            return;
        }
        String cached = picCache.get(song.picId);
        if (cached != null) {
            song.picUrl = cached;
            cb.onSuccess(cached);
            return;
        }
        getJson(clientGds, GDS_BASE + "?types=pic&source=netease&id=" + song.picId + "&size=300",
                new Result<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        String url = optStr(json, "url");
                        if (url != null) {
                            picCache.put(song.picId, url);
                            song.picUrl = url;
                            cb.onSuccess(url);
                        } else {
                            cb.onError("pic empty");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    // ------------------------------------------------------------ GDS helpers

    /** GDStudio search returns a bare JSON array (some endpoints wrap in data). */
    private void getArrayGds(String url, final Result<JSONArray> cb) {
        Request req = new Request.Builder().url(url).header("User-Agent", UA).build();
        clientGds.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliverError(cb, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        deliverError(cb, "HTTP " + response.code());
                        return;
                    }
                    String body = response.body().string().trim();
                    final JSONArray arr;
                    if (body.startsWith("[")) {
                        arr = new JSONArray(body);
                    } else {
                        JSONObject obj = new JSONObject(body);
                        JSONArray found = obj.optJSONArray("data");
                        arr = found == null ? new JSONArray() : found;
                    }
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onSuccess(arr);
                        }
                    });
                } catch (Exception e) {
                    deliverError(cb, "parse: " + e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }

    private List<Song> parseSongsGds(JSONArray arr) {
        List<Song> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Song s = new Song();
            s.id = o.optString("id");
            s.name = o.optString("name");
            JSONArray artists = o.optJSONArray("artist");
            if (artists != null) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < artists.length(); j++) {
                    if (j > 0) sb.append("/");
                    sb.append(artists.optString(j));
                }
                s.artistsText = sb.toString();
            }
            s.albumName = o.optString("album");
            s.picId = optStr(o, "pic_id");
            s.durationMs = 0;
            out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------- NetEase parsing

    /** NetEase track object (ar[] / al.picUrl / dt), used by GDStudio playlist
     *  JSON and by the custom source alike. */
    private Song parseNetEaseTrack(JSONObject o) {
        if (o == null) return null;
        Song s = new Song();
        s.id = String.valueOf(o.optLong("id"));
        s.name = o.optString("name");
        s.artistsText = joinArtists(o.optJSONArray("ar"));
        JSONObject al = o.optJSONObject("al");
        if (al != null) {
            s.albumName = al.optString("name");
            s.picUrl = optStr(al, "picUrl");
        }
        s.durationMs = o.optLong("dt");
        return s;
    }

    private Song parseSearchSongCustom(JSONObject o) {
        if (o == null) return null;
        Song s = new Song();
        s.id = String.valueOf(o.optLong("id"));
        s.name = o.optString("name");
        s.artistsText = joinArtists(o.optJSONArray("artists"));
        JSONObject al = o.optJSONObject("album");
        if (al != null) {
            s.albumName = al.optString("name");
            s.picUrl = optStr(al, "picUrl");
        }
        s.durationMs = o.optLong("duration");
        return s;
    }

    private static String joinArtists(JSONArray ar) {
        if (ar == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ar.length(); i++) {
            JSONObject a = ar.optJSONObject(i);
            if (a == null) continue;
            if (sb.length() > 0) sb.append("/");
            sb.append(a.optString("name"));
        }
        return sb.toString();
    }

    private static String optStr(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return null;
        String v = o.optString(key, null);
        return (v == null || v.length() == 0) ? null : v;
    }
}
