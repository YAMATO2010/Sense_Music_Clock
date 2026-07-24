package jp.gr.java_conf.SenseMusicClock.Music

import android.content.ContentResolver
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalArtistFetcher {

    suspend fun loadArtist(
        resolver: ContentResolver,
        artistId: Long,
        cancellationSignal: CancellationSignal? = null,
    ): MediaStoreArtistSummary? {
        val (selection, selectionArgs) = artistId_selection(artistId)
        val queryArgs = createQueryArgs(selection, selectionArgs)
        val artist = loadArtistsFromAppDir(resolver, queryArgs, cancellationSignal).firstOrNull()
        return artist
    }

    fun artist_selection(artist: String): Pair<String, Array<String>> {
        val selection = "${MediaStore.Audio.Artists.ARTIST} LIKE ?"
        val args = arrayOf("%$artist%")
        return (selection to args)


    }


    fun artistId_selection(artistId: Long): Pair<String, Array<String>> {
        val selection = "${MediaStore.Audio.Artists._ID} = ?"
        val args = arrayOf(artistId.toString())
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


    suspend fun loadArtistsFromAppDir(
        resolver: ContentResolver,
        queryArgs: Bundle,
        cancellationSignal: CancellationSignal? = null,
    ): List<MediaStoreArtistSummary> {
        val artists = mutableListOf<MediaStoreArtistSummary>()

        try {


            withContext(Dispatchers.IO) {
                val queryUri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Artists._ID,
                    MediaStore.Audio.Artists.ARTIST,
                )
                val cursor = resolver.query(
                    queryUri,
                    projection,
                    queryArgs,
                    cancellationSignal
                )

                cursor?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
                    val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)


                    while (c.moveToNext()) {
                        val albumId = c.getLong(idIdx)
                        val artistName = c.getString(artistIdx) ?: ""



                        artists.add(
                            MediaStoreArtistSummary(
                                artistId = albumId,
                                artistName = artistName,
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LocalArtistFetcher", "Failed to load artists", e)
        }

        return artists
    }

    data class MediaStoreArtistSummary(
        val artistId: Long,
        val artistName: String?,
    )

}
