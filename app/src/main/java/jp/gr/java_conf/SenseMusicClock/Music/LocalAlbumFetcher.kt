package jp.gr.java_conf.SenseMusicClock.Music

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalAlbumFetcher {

    fun albumTitle_selection(title: String): Pair<String, Array<String>> {
        val selection = "${MediaStore.Audio.Albums.ALBUM} LIKE ?"
        val args = arrayOf("%$title%")
        return (selection to args)


    }

    fun albumId_selection(albumId: Long): Pair<String, Array<String>> {
        val selection = "${MediaStore.Audio.Albums._ID} = ?"
        val args = arrayOf(albumId.toString())
        return (selection to args)
    }

    fun createQueryArgs(
        selection: String?,
        selectionArgs: Array<String>?,
    ): Bundle {
        val args = Bundle()

        args.run {

            if (selection != null && !selectionArgs.isNullOrEmpty()) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)

            }

            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
            )
            putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.TITLE)
        }
        return args
    }


    suspend fun loadAlbumsFromAppDir(
        resolver: ContentResolver,
        queryArgs: Bundle,
        cancellationSignal: CancellationSignal? = null,
    ): List<MediaStoreAlbumSummary> {
        val albums = mutableListOf<MediaStoreAlbumSummary>()

        try {


            withContext(Dispatchers.IO) {
                val queryUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Albums._ID,
                    MediaStore.Audio.Albums.ALBUM,
                    MediaStore.Audio.Albums.ALBUM_ID,
                    MediaStore.Audio.Albums.ARTIST
                )
                val cursor = resolver.query(
                    queryUri,
                    projection,
                    queryArgs,
                    cancellationSignal
                )

                cursor?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                    val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                    c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ID)
                    val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)


                    while (c.moveToNext()) {
                        val albumId = c.getLong(idIdx)
                        val albumName = c.getString(albumIdx) ?: ""


                        val albumArtUri: Uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            albumId
                        )
                        val artistName = c.getString(artistIdx) ?: ""

                        albums.add(
                            MediaStoreAlbumSummary(
                                albumId = albumId,
                                albumName = albumName,
                                albumArtUri = albumArtUri,
                                artist = artistName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return albums
    }

    data class MediaStoreAlbumSummary(
        val albumId: Long,
        val albumName: String?,
        val albumArtUri: Uri?,
        val artist: String?
    )


}