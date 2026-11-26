package com.kjota.ritmo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.kjota.ritmo.data.Library
import com.kjota.ritmo.engine.Engine

class RitmoApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        com.kjota.ritmo.engine.YouTube.init(this)
        com.kjota.ritmo.data.Taste.init(this)
        Engine.start(this)
    }

    /** Las carátulas quedan en caché para verse también sin internet. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder().directory(cacheDir.resolve("covers")).maxSizeBytes(200L * 1024 * 1024).build()
            }
            .build()
}
