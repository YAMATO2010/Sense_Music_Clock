package jp.gr.java_conf.SenseMusicClock.Music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 更新待ちがある間は、新しいページ取得を開始しない。 */
internal class MediaItemsAccess {
    private val mutex = Mutex()
    private val pendingUpdates = MutableStateFlow(0)
    private var version = 0L

    suspend fun <T> update(block: suspend () -> T): T {
        pendingUpdates.update { it + 1 }
        try {
            return mutex.withLock {
                try {
                    block()
                } finally {
                    version++
                }
            }
        } finally {
            pendingUpdates.update { it - 1 }
        }
    }

    suspend fun <T> read(block: (version: Long) -> T): T {
        while (true) {
            pendingUpdates.first { it == 0 }
            mutex.lock()
            try {
                // 待機中に登録された更新も、ページ取得より先に処理する。
                if (pendingUpdates.value == 0) return block(version)
            } finally {
                mutex.unlock()
            }
        }
    }
}
