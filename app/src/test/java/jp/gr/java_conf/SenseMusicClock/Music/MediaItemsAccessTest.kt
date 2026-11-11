package jp.gr.java_conf.SenseMusicClock.Music

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class MediaItemsAccessTest {
    @Test
    fun queuedUpdateRunsBeforeEarlierQueuedRead() = runBlocking {
        withTimeout(5_000) {
            val access = MediaItemsAccess()
            val release = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val firstUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                access.update {
                    events.add("first update")
                    release.await()
                }
            }
            val read = async(start = CoroutineStart.UNDISPATCHED) {
                access.read { version ->
                    events.add("read")
                    version
                }
            }
            val secondUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                access.update { events.add("second update") }
            }

            assertFalse(read.isCompleted)
            assertFalse(secondUpdate.isCompleted)
            release.complete(Unit)
            firstUpdate.await()
            secondUpdate.await()

            assertEquals(2L, read.await())
            assertEquals(listOf("first update", "second update", "read"), events)
        }
    }

    @Test
    fun cancelledQueuedUpdateDoesNotBlockReads() = runBlocking {
        withTimeout(5_000) {
            val access = MediaItemsAccess()
            val release = CompletableDeferred<Unit>()
            val firstUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                access.update { release.await() }
            }
            val cancelledUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                access.update { fail("Cancelled update must not run") }
            }
            cancelledUpdate.cancelAndJoin()
            release.complete(Unit)
            firstUpdate.await()

            assertEquals(1L, access.read { it })
        }
    }

    @Test
    fun cancelledActiveUpdateReleasesReadersAndInvalidatesOldPages() = runBlocking {
        withTimeout(5_000) {
            val access = MediaItemsAccess()
            assertEquals(0L, access.read { it })
            val update = async(start = CoroutineStart.UNDISPATCHED) {
                access.update { CompletableDeferred<Unit>().await() }
            }
            val read = async(start = CoroutineStart.UNDISPATCHED) { access.read { it } }

            assertFalse(read.isCompleted)
            update.cancelAndJoin()
            assertEquals(1L, read.await())
            access.update { }
            assertEquals(2L, access.read { it })
        }
    }

    @Test
    fun failedUpdateReleasesLockAndInvalidatesOldPages() = runBlocking {
        withTimeout(5_000) {
            val access = MediaItemsAccess()
            try {
                access.update { throw IllegalStateException("test failure") }
                fail("Update must propagate its failure")
            } catch (e: IllegalStateException) {
                assertEquals("test failure", e.message)
            }
            assertEquals(1L, access.read { it })
            access.update { }
            assertEquals(2L, access.read { it })
        }
    }

    @Test
    fun cancelledReadDoesNotBlockLaterUpdates() = runBlocking {
        withTimeout(5_000) {
            val access = MediaItemsAccess()
            val release = CompletableDeferred<Unit>()
            val update = async(start = CoroutineStart.UNDISPATCHED) {
                access.update { release.await() }
            }
            val read = async(start = CoroutineStart.UNDISPATCHED) { access.read { it } }
            read.cancelAndJoin()
            release.complete(Unit)
            update.await()
            access.update { }
            assertEquals(2L, access.read { it })
        }
    }
}
