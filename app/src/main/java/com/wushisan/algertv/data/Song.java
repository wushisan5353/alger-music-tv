package com.wushisan.algertv.data;

import java.io.Serializable;

public class Song implements Serializable {
    private static final long serialVersionUID = 1L;

    /** NetEase numeric id, as string. Works as url_id/lyric_id on source B too. */
    public String id;
    public String name;
    public String artistsText;
    public String albumName;
    /** Cover URL; may be null for source-B songs until resolved via pic API. */
    public String picUrl;
    /** Source-B picture id (pic_id); used to resolve picUrl lazily. */
    public String picId;
    public long durationMs;
    /** Display-only row number within its list (set by the UI before binding). */
    public transient int rowIndex = -1;

    public Song() {
    }

    public Song(String id, String name, String artistsText, String albumName,
                String picUrl, String picId, long durationMs) {
        this.id = id;
        this.name = name;
        this.artistsText = artistsText;
        this.albumName = albumName;
        this.picUrl = picUrl;
        this.picId = picId;
        this.durationMs = durationMs;
    }

    public String subtitle() {
        if (artistsText != null && albumName != null) return artistsText + " · " + albumName;
        if (artistsText != null) return artistsText;
        return albumName == null ? "" : albumName;
    }
}
