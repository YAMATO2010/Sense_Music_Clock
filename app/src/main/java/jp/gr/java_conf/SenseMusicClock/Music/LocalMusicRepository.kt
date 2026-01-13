package jp.gr.java_conf.SenseMusicClock



import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlin.div
import kotlin.text.toInt
import kotlin.times
import kotlin.toString

object LocalMusicRepository {
    fun loadLocalMusicFromAppDir(context: Context, relativePaths: List<String> = listOf("Music/SMC")): List<localTrack> {
        if (relativePaths.isEmpty()) return emptyList()
        val list = mutableListOf<localTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.RELATIVE_PATH
        )

        val parts = mutableListOf<String>()
        val args = mutableListOf<String>()
        for (p in relativePaths) {
            // RELATIVE_PATH が "Music/SMC" のとき "Music/SMC%" と DATA 用に "%/Music/SMC/%"
            parts.add("(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)")
            parts.add("(${MediaStore.Audio.Media.DATA} LIKE ?)")
            args.add("${p.removeSuffix("/")}%")
            args.add("%/${p.removePrefix("/")}%")
        }
        val selection = parts.joinToString(" OR ")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args.toTypedArray(),
            sortOrder
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val trackIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val dataIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)



            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val title = c.getString(titleIdx) ?: ""

                if (title.contains(".nomedia")){
                    continue
                }
                val album = c.getString(albumIdx) ?: ""
                val artist = c.getString(artistIdx) ?: ""
                val albumId = c.getLong(albumIdIdx)
                val artistId = c.getLong(artistIdIdx)
                val trackNo = c.getInt(trackIdx)
                val path = c.getString(dataIdx) ?: ""

                if (path.contains(".nomedia")){
                    continue
                }
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

// kotlin
                val albumArtBitmap: Bitmap? = try {

                    val albumArtUri = Uri.parse("content://media/external/audio/albumart")
                        .buildUpon()
                        .appendPath(albumId.toString())
                        .build()

                    val targetSize = 200

                    // 1) bounds を取得して inSampleSize を計算
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, bounds)
                    }
                    val inSample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSize, targetSize)

                    // 2) 実際にデコード（inSampleSize を使う）
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
                    context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOpts)
                    }
                } catch (e: Exception) {
                    null
                }

                // ヘルパー: inSampleSize を計算


                val lm = localTrack(title, album, artist, albumArtBitmap, id, albumId, artistId, path, uri, trackNo)
                list.add(lm)
            }
        }
        Log.i("LocalMusicRepository", " ${list.joinToString("\n") { it.title }}} ")

        return list
    }
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ヘルパー: 中央クロップして targetW x targetH を返す（必要に応じて元ビットマップを recycle）
    private fun scaleCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (src.width == targetW && src.height == targetH) return src

        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        if (scaled !== src) {
            try { src.recycle() } catch (_: Exception) {}
        }

        val x = (scaled.width - targetW) / 2
        val y = (scaled.height - targetH) / 2
        val result = Bitmap.createBitmap(scaled, x.coerceAtLeast(0), y.coerceAtLeast(0), targetW, targetH)
        if (result !== scaled) {
            try { scaled.recycle() } catch (_: Exception) {}
        }
        return result
    }

}
