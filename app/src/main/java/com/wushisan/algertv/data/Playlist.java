package com.wushisan.algertv.data;

import java.io.Serializable;

public class Playlist implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String name;
    public String coverUrl;
    public long playCount;
    public int trackCount;

    public Playlist() {
    }

    public Playlist(String id, String name, String coverUrl, long playCount) {
        this.id = id;
        this.name = name;
        this.coverUrl = coverUrl;
        this.playCount = playCount;
    }

    public static String formatPlayCount(long n) {
        if (n <= 0) return "";
        if (n >= 100000000) return String.format("%.1f亿", n / 100000000.0);
        if (n >= 10000) return String.format("%.1f万", n / 10000.0);
        return String.valueOf(n);
    }
}
