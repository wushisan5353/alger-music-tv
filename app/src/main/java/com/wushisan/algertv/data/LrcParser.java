package com.wushisan.algertv.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {

    public static class Line {
        public final long timeMs;
        public final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private static final Pattern TS = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");

    public static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) return out;
        String[] rawLines = lrc.split("\n");
        for (String raw : rawLines) {
            if (raw == null) continue;
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (m.find()) {
                try {
                    long min = Long.parseLong(m.group(1));
                    long sec = Long.parseLong(m.group(2));
                    long frac = 0;
                    if (m.group(3) != null) {
                        String f = m.group(3);
                        frac = Long.parseLong(f);
                        if (f.length() == 1) frac *= 100;
                        else if (f.length() == 2) frac *= 10;
                    }
                    times.add(min * 60000 + sec * 1000 + frac);
                } catch (Exception ignored) {
                }
                lastEnd = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(lastEnd).trim();
            for (Long t : times) out.add(new Line(t, text));
        }
        Collections.sort(out, new Comparator<Line>() {
            @Override
            public int compare(Line a, Line b) {
                return Long.compare(a.timeMs, b.timeMs);
            }
        });
        return out;
    }

    /** Index of the line active at positionMs, or -1. */
    public static int indexFor(List<Line> lines, long positionMs) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= positionMs) idx = i;
            else break;
        }
        return idx;
    }
}
