package com.wushisan.algertv.ui.phone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;
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
        final ImageView cover;
        final TextView index;
        final TextView name;
        final TextView sub;
        final TextView duration;

        VH(View v) {
            super(v);
            cover = v.findViewById(R.id.track_cover);
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
        holder.index.setText(current ? "▶" : "");
        holder.name.setText(s.name == null ? "" : s.name);
        holder.name.setTextColor(holder.itemView.getResources().getColor(
                current ? R.color.ph_accent : R.color.ph_text));
        holder.sub.setText(s.subtitle());
        holder.duration.setText(formatDuration(s.durationMs));
        bindCover(holder, s);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onTrackClick(s, pos);
            }
        });
    }

    /** Rounded cover; search results only carry a pic_id, resolved lazily
     *  with a tag guard so recycled rows never show a stale cover.
     *  All Glide calls here use the application context on purpose: the
     *  resolvePic callback can land after the host activity is destroyed
     *  (user backs out of search while pic requests are in flight), and
     *  Glide.with(activity) then throws "You cannot start a load for a
     *  destroyed activity" — the playing-then-navigate crash of 1.2. */
    private void bindCover(final VH holder, final Song s) {
        holder.cover.setTag(s);
        int radius = holder.itemView.getResources()
                .getDimensionPixelSize(R.dimen.ph_radius_cover);
        if (s.picUrl != null && s.picUrl.length() > 0) {
            Glide.with(holder.itemView.getContext().getApplicationContext()).load(s.picUrl)
                    .placeholder(R.drawable.cover_placeholder)
                    .error(R.drawable.cover_placeholder)
                    .transform(new CenterCrop(), new RoundedCorners(radius))
                    .into(holder.cover);
        } else if (s.picId != null) {
            Glide.with(holder.itemView.getContext().getApplicationContext()).clear(holder.cover);
            holder.cover.setImageResource(R.drawable.cover_placeholder);
            MusicRepository.get().resolvePic(s, new MusicRepository.Result<String>() {
                @Override
                public void onSuccess(String url) {
                    if (holder.cover.getTag() == s) {
                        int r = holder.itemView.getResources()
                                .getDimensionPixelSize(R.dimen.ph_radius_cover);
                        Glide.with(holder.itemView.getContext().getApplicationContext()).load(url)
                                .placeholder(R.drawable.cover_placeholder)
                                .transform(new CenterCrop(), new RoundedCorners(r))
                                .into(holder.cover);
                    }
                }

                @Override
                public void onError(String message) {
                }
            });
        } else {
            Glide.with(holder.itemView.getContext().getApplicationContext()).clear(holder.cover);
            holder.cover.setImageResource(R.drawable.cover_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
