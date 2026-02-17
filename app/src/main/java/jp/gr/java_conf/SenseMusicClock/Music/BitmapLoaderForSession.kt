package jp.gr.java_conf.SenseMusicClock.Music


import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
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

@UnstableApi
class BitmapLoaderForSession(private val context: Context) : BitmapLoader {
    private val imageLoader = ImageLoader(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        // バイト配列からのデコードが必要な場合（通常はあまり使われません）
        val future = SettableFuture.create<Bitmap>()
        // ここにデコードロジックを記述
        return future
    }

    // 新規追加が必要なメソッド
    override fun supportsMimeType(mimeType: String): Boolean {
        // 画像形式（image/jpeg, image/png など）をサポートするかどうか
        return mimeType.startsWith("image/")
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {

        val albumArtUri = metadata.artworkUri

        return loadBitmapFromUri(albumArtUri)

    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = loadBitmapFromUri(uri)

        return future

    }


    private fun loadBitmapFromUri(uri: Uri?): ListenableFuture<Bitmap> {
        var future = SettableFuture.create<Bitmap>()


        scope.launch {
            val loader = ImageLoader(context)

            val request = ImageRequest.Builder(context)
                .data(uri)
                .placeholder(R.drawable.default_album_art)
                .error(R.drawable.default_album_art)
                .fallback(R.drawable.default_album_art)
                .build()

            val result = loader.execute(request)

            if (result.drawable == null) {
                val request = ImageRequest.Builder(context)
                    .data(R.drawable.default_album_art)
                    .placeholder(R.drawable.default_album_art)
                    .error(R.drawable.default_album_art)
                    .fallback(R.drawable.default_album_art)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    future.set(result.drawable.toBitmap())
                } else {
                    future.setException(RuntimeException("Failed to load default bitmap"))
                }

            } else {
                future.set(result.drawable?.toBitmap())
            }


        }


        return future
    }


    private fun loadDefaultBitmap(): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        scope.launch {
            val request = ImageRequest.Builder(context)
                .data(R.drawable.default_album_art)
                .allowHardware(false) // 通知用BitmapはソフトウェアBitmapである必要がある
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                future.set((result.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
            } else {
                future.setException(RuntimeException("Failed to load default bitmap"))
            }
        }

        return future
    }
}