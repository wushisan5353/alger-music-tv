package com.wushisan.algertv.ui.phone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wushisan.algertv.R;
import com.wushisan.algertv.data.Song;

import java.util.ArrayList;
import java.util.List;

/** Simple touch track list used by playlist / search / queue screens. */
public class PhoneTracksAdapter extends RecyclerView.Adapter<PhoneTracksAdapter.VH> {

    public interface OnTrackClick {
        void onTrackClick(Song song, int position);
    }

    private final List<Song> items = new ArrayList<>();
    private final OnTrackClick listener;
    private int highlightIndex = -1;

    public PhoneTracksAdapter(OnTrackClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Song> songs) {
        items.clear();
        if (songs != null) items.addAll(songs);
        notifyDataSetChanged();
    }

    public void setHighlightIndex(int idx) {
        if (idx == highlightIndex) return;
        int old = highlightIndex;
        highlightIndex = idx;
        if (old >= 0 && old < items.size()) notifyItemChanged(old);
        if (idx >= 0 && idx < items.size()) notifyItemChanged(idx);
    }

    static String formatDuration(long ms) {
        if (ms <= 0) return "";
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    class VH extends RecyclerView.ViewHolder {
        final TextView index;
        final TextView name;
        final TextView sub;
        final TextView duration;

        VH(View v) {
            super(v);
            index = v.findViewById(R.id.track_index);
            name = v.findViewById(R.id.track_name);
            sub = v.findViewById(R.id.track_sub);
            duration = v.findViewById(R.id.track_duration);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_phone_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Song s = items.get(position);
        final int pos = position;
        boolean current = position == highlightIndex;
        holder.index.setText(current ? "▶" : String.valueOf(position + 1));
        holder.name.setText(s.name == null ? "" : s.name);
        holder.name.setTextColor(holder.itemView.getResources().getColor(
                current ? R.color.accent : R.color.text_primary));
        holder.sub.setText(s.subtitle());
        holder.duration.setText(formatDuration(s.durationMs));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onTrackClick(s, pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
