package com.wushisan.algertv.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.wushisan.algertv.R;
import com.wushisan.algertv.data.Playlist;

public class PlaylistCardPresenter extends Presenter {

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        final ImageCardView card = new ImageCardView(parent.getContext());
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        int w = (int) parent.getResources().getDimension(R.dimen.card_width);
        int h = (int) parent.getResources().getDimension(R.dimen.card_height);
        card.setMainImageDimensions(w, h);
        card.setInfoAreaBackgroundColor(0xFF161616);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float target = hasFocus ? 1.08f : 1.0f;
                v.animate().scaleX(target).scaleY(target).setDuration(150).start();
                v.setElevation(hasFocus ? 12f : 0f);
            }
        });
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        Playlist pl = (Playlist) item;
        ImageCardView card = (ImageCardView) viewHolder.view;
        card.setTitleText(pl.name == null ? "" : pl.name);
        if (pl.playCount > 0) {
            card.setContentText("播放 " + Playlist.formatPlayCount(pl.playCount) + " 次");
        } else if (pl.trackCount > 0) {
            card.setContentText(pl.trackCount + " 首");
        } else {
            card.setContentText("网易云音乐");
        }
        if (pl.coverUrl != null && pl.coverUrl.length() > 0) {
            Glide.with(card.getContext())
                    .load(pl.coverUrl)
                    .placeholder(R.drawable.cover_placeholder)
                    .error(R.drawable.cover_placeholder)
                    .centerCrop()
                    .into(card.getMainImageView());
        } else {
            Glide.with(card.getContext()).clear(card.getMainImageView());
            card.setMainImage(card.getResources().getDrawable(R.drawable.cover_placeholder));
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        ImageCardView card = (ImageCardView) viewHolder.view;
        Glide.with(card.getContext()).clear(card.getMainImageView());
        card.setMainImage(null);
        card.setTitleText(null);
        card.setContentText(null);
    }
}
