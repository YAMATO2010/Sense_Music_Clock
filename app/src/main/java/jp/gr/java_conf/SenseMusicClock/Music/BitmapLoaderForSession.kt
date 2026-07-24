package jp.gr.java_conf.SenseMusicClock.Music


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import jp.gr.java_conf.SenseMusicClock.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class BitmapLoaderForSession(private val context: Context) : BitmapLoader {
    private val imageLoader = ImageLoader(context)
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
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .fallback(R.drawable.default_album_art)
                .build()

            withContext(Dispatchers.Main) {

                val result = loader.execute(request)

                if (result.drawable == null) {
                    loadDefaultBitmap()
                } else {
                    future.set(result.drawable?.toBitmap())
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
                .build()


            withContext(Dispatchers.Main) {

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    future.set((result.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
                } else {
                    future.setException(RuntimeException("Failed to load default bitmap"))
                }
            }
        }

        return future
    }
}