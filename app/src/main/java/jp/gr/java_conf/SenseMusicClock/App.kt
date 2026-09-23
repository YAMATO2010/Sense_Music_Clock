package jp.gr.java_conf.SenseMusicClock

import android.app.Application
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade


class App : Application() {
    lateinit var backgroundImageLoader: ImageLoader
        private set

    override fun onCreate() {
        super.onCreate()

        backgroundImageLoader = ImageLoader.Builder(this.applicationContext)

            .components {
                add(AnimatedImageDecoder.Factory())
                add(GifDecoder.Factory())

            }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this.applicationContext, 0.01)
                    .strongReferencesEnabled(false)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .build()



    }


}
