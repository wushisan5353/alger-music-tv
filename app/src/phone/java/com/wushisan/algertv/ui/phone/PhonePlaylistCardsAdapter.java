package com.wushisan.algertv.ui.phone;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.Playlist;

import java.util.ArrayList;
import java.util.List;

/** Horizontal playlist cover cards for the phone home screen. */
public class PhonePlaylistCardsAdapter
        extends RecyclerView.Adapter<PhonePlaylistCardsAdapter.VH> {

    public interface OnPlaylistClick {
        void onPlaylistClick(Playlist playlist);
    }

    private final List<Playlist> items = new ArrayList<>();
    private final OnPlaylistClick listener;

    public PhonePlaylistCardsAdapter(OnPlaylistClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Playlist> playlists) {
        items.clear();
        if (playlists != null) items.addAll(playlists);
        notifyDataSetChanged();
    }

    class VH extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView name;
        final TextView meta;

        VH(View v) {
            super(v);
            cover = v.findViewById(R.id.card_cover);
            name = v.findViewById(R.id.card_name);
            meta = v.findViewById(R.id.card_meta);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_phone_playlist_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Playlist pl = items.get(position);
        holder.name.setText(pl.name == null ? "" : pl.name);
        String plays = Playlist.formatPlayCount(pl.playCount);
        holder.meta.setText(plays.length() > 0 ? plays + " 播放" : "");
        if (pl.coverUrl != null) {
            Glide.with(holder.itemView.getContext()).load(pl.coverUrl)
                    .placeholder(R.drawable.cover_placeholder)
                    .error(R.drawable.cover_placeholder)
                    .centerCrop().into(holder.cover);
        } else {
            holder.cover.setImageResource(R.drawable.cover_placeholder);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onPlaylistClick(pl);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
