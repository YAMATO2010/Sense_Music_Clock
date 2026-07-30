package jp.gr.java_conf.SenseMusicClock

import android.app.Application
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder


class App : Application() {
    lateinit var backgroundImageLoader: ImageLoader
        private set

    override fun onCreate() {
        super.onCreate()

        backgroundImageLoader = ImageLoader.Builder(this.applicationContext)

            .components {
                add(ImageDecoderDecoder.Factory()) // Android 9以降のWebP/GIF用
                add(GifDecoder.Factory())          // Android 8以前のGIF用

            }
            .crossfade(true)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this.applicationContext)
                    .maxSizePercent(0.01) // メモリの25%までキャッシュを使用
                    .strongReferencesEnabled(false)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .build()



    }


}
