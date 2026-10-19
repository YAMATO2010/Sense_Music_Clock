package jp.gr.java_conf.SenseMusicClock

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade


class App : Application(), SingletonImageLoader.Factory {
    val sharedMemoryCache: MemoryCache by lazy {
        MemoryCache.Builder()
            .maxSizePercent(this, 0.05)
            .build()
    }

    lateinit var backgroundImageLoader: ImageLoader
        private set

    override fun onCreate() {
        super.onCreate()
        backgroundImageLoader = ImageLoader.Builder(this)
            .components {
                add(AnimatedImageDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .crossfade(true)
            .memoryCache(sharedMemoryCache)
            .build()
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache(sharedMemoryCache)
            .build()
}
