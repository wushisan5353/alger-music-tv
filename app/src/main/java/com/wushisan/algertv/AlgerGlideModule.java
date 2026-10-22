package com.wushisan.algertv;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

/**
 * TV has only ~1.1GB free storage and 2GB RAM: cap Glide's disk cache at
 * 30MB and shrink the memory cache well below the default.
 */
@GlideModule
public final class AlgerGlideModule extends AppGlideModule {

    private static final long DISK_CACHE_BYTES = 30L * 1024 * 1024;
    private static final long MEMORY_CACHE_BYTES = 8L * 1024 * 1024;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_BYTES));
        builder.setMemoryCache(new LruResourceCache(MEMORY_CACHE_BYTES));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
