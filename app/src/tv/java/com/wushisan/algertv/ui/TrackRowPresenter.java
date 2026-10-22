package com.wushisan.algertv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.leanback.widget.Presenter;

import com.wushisan.algertv.R;
import com.wushisan.algertv.data.Song;

public class TrackRowPresenter extends Presenter {

    /** Marker item rendered as the "播放全部" row. */
    public static class PlayAll {
        public final int count;

        public PlayAll(int count) {
            this.count = count;
        }
    }

    /** Marker item rendered as a full-width message row. */
    public static class Message {
        public final String text;

        public Message(String text) {
            this.text = text;
        }
    }

    static class VH extends ViewHolder {
        final TextView index;
        final TextView name;
        final TextView artist;
        final TextView duration;

        VH(View v) {
            super(v);
            index = v.findViewById(R.id.track_index);
            name = v.findViewById(R.id.track_name);
            artist = v.findViewById(R.id.track_artist);
            duration = v.findViewById(R.id.track_duration);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        VH vh = (VH) viewHolder;
        if (item instanceof PlayAll) {
            vh.index.setText("▶");
            vh.name.setText(R.string.play_all);
            vh.artist.setText("共 " + ((PlayAll) item).count + " 首");
            vh.duration.setText("");
        } else if (item instanceof Message) {
            vh.index.setText("!");
            vh.name.setText(((Message) item).text);
            vh.artist.setText("");
            vh.duration.setText("");
        } else if (item instanceof Song) {
            Song s = (Song) item;
            vh.index.setText(s.rowIndex >= 0 ? String.valueOf(s.rowIndex + 1) : "");
            vh.name.setText(s.name == null ? "" : s.name);
            vh.artist.setText(s.subtitle());
            vh.duration.setText(formatDuration(s.durationMs));
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "";
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }
}
