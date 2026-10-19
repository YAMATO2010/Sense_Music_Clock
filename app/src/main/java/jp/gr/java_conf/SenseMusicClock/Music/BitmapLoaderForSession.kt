package jp.gr.java_conf.SenseMusicClock.Music


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.placeholder
import coil3.request.error
import coil3.request.fallback
import coil3.request.crossfade
import coil3.request.allowHardware
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import jp.gr.java_conf.SenseMusicClock.App
import jp.gr.java_conf.SenseMusicClock.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class BitmapLoaderForSession(private val context: Context) : BitmapLoader {
    private val imageLoader = ImageLoader.Builder(context)
        .memoryCache((context.applicationContext as App).sharedMemoryCache)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {

        val future = SettableFuture.create<Bitmap>()

        return future
    }


    override fun supportsMimeType(mimeType: String): Boolean {

        return mimeType.startsWith("image/")
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {

        val albumArtUri = metadata.artworkUri

        return loadBitmapFromUri(albumArtUri)

    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = loadBitmapFromUri(uri)

        return future

    }


    private fun loadBitmapFromUri(uri: Uri?): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()


        scope.launch {
            val loader = imageLoader

            val request = ImageRequest.Builder(context)
                .allowHardware(false)
                .crossfade(false)
                .data(uri)
                .size(512, 512) // Resize to 512x512 pixels
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .fallback(R.drawable.default_album_art)
                .build()

            withContext(Dispatchers.Main) {

                val result = loader.execute(request)

                if (result.image == null) {
                    loadDefaultBitmap()
                } else {
                    future.set(result.image?.asDrawable(context.resources)?.toBitmap())
                }
            }


        }


        return future
    }


    private fun loadDefaultBitmap(): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        scope.launch {

            val request = ImageRequest.Builder(context)
                .data(R.drawable.default_album_art)
                .allowHardware(false)
                .size(512, 512) // Resize to 512x512 pixels
                .build()


            withContext(Dispatchers.Main) {

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    future.set(result.image.asDrawable(context.resources).toBitmap())
                } else {
                    future.setException(RuntimeException("Failed to load default bitmap"))
                }
            }
        }

        return future
    }
}
