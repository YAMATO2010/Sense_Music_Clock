package jp.gr.java_conf.SenseMusicClock

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LocalMusicRepositoryCancellationTest {
    @After
    fun resetTracks() {
        LocalMusicRepository.setTracks(emptyList())
    }

    @Test
    fun newerLoadCancelsQueryAndRejectsItsLateResult() = runBlocking {
        val provider = BlockingMusicProvider()
        val context = contextFor(provider)
        val oldLoad = async(Dispatchers.IO) {
            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_Id(context, 1)
        }
        try {
            assertTrue(provider.firstQueryStarted.await(10, TimeUnit.SECONDS))
            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_Id(context, 2)
            assertTrue(provider.firstSignal.get().isCanceled)
        } finally {
            provider.releaseFirstQuery.countDown()
            oldLoad.await()
        }
        assertEquals(listOf("2"), LocalMusicRepository.getTracks().map { it.mediaId })
        assertEquals(0, LocalMusicRepository.getIndexById("2"))
        assertEquals(null, LocalMusicRepository.getIndexById("1"))
    }

    @Test
    fun emptyPlaylistCancelsPreviousQueryWithoutPublishingItsResult() = runBlocking {
        val provider = BlockingMusicProvider()
        val context = contextFor(provider)
        val oldLoad = async(Dispatchers.IO) {
            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_Id(context, 1)
        }
        try {
            assertTrue(provider.firstQueryStarted.await(10, TimeUnit.SECONDS))
            LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_playlist(context, emptyList())
            assertTrue(provider.firstSignal.get().isCanceled)
        } finally {
            provider.releaseFirstQuery.countDown()
            oldLoad.await()
        }
        assertTrue(LocalMusicRepository.getTracks().isEmpty())
        assertEquals(null, LocalMusicRepository.getIndexById("1"))
    }

    @Test
    fun cancelledRequestKeepsCurrentTracksAndDoesNotQuery() = runBlocking {
        val provider = BlockingMusicProvider()
        val context = contextFor(provider)
        LocalMusicRepository.setTracks(listOf(MediaItem.Builder().setMediaId("current").build()))
        val signal = CancellationSignal().apply { cancel() }

        LocalMusicRepository.loadMusicAndSetTracksAndCreateMap_Id(
            context, 1, cancellationSignal = signal
        )

        assertEquals(0, provider.queryCount.get())
        assertEquals(listOf("current"), LocalMusicRepository.getTracks().map { it.mediaId })
    }

    @Test
    fun directUpdateCancelsPreviousSignalButReadOnlyLoadDoesNot() = runBlocking {
        val provider = BlockingMusicProvider()
        val context = contextFor(provider)
        val signal = CancellationSignal()
        LocalMusicRepository.setTracks(emptyList(), cancellationSignal = signal)

        LocalMusicRepository.loadPlaylistMusic(context, emptyList())
        assertFalse(signal.isCanceled)

        LocalMusicRepository.setTracksAndCreateMap(emptyList(), isBlock = false)
        assertTrue(signal.isCanceled)
    }

    private fun contextFor(provider: ContentProvider): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        provider.attachInfo(base, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        val resolver = ContentResolver.wrap(provider)
        return object : ContextWrapper(base) {
            override fun getContentResolver(): ContentResolver = resolver
        }
    }

    private class BlockingMusicProvider : ContentProvider() {
        val firstQueryStarted = CountDownLatch(1)
        val releaseFirstQuery = CountDownLatch(1)
        val firstSignal = AtomicReference<CancellationSignal>()
        val queryCount = AtomicInteger()

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            queryArgs: Bundle?,
            cancellationSignal: CancellationSignal?
        ): Cursor {
            val id = queryCount.incrementAndGet().toLong()
            if (id == 1L) {
                firstSignal.set(checkNotNull(cancellationSignal))
                firstQueryStarted.countDown()
                check(releaseFirstQuery.await(10, TimeUnit.SECONDS))
                // Simulate a provider that still returns a result after cancellation.
            }
            val columns = checkNotNull(projection)
            return MatrixCursor(columns).apply {
                addRow(columns.map { column ->
                    when (column) {
                        MediaStore.Audio.Media._ID -> id
                        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ARTIST_ID -> 1L
                        MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED -> 1
                        MediaStore.Audio.Media.RELATIVE_PATH -> "Music/"
                        MediaStore.Audio.Media.DISPLAY_NAME -> "test.mp3"
                        else -> "test"
                    }
                }.toTypedArray<Any>())
            }
        }

        override fun onCreate() = true
        override fun getType(uri: Uri) = "audio/mpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("Not used")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(
            uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
        ) = 0

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?
        ): Cursor = error("Expected the cancellation-aware query overload")
    }
}
