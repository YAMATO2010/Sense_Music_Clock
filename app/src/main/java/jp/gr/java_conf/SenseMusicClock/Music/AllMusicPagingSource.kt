package jp.gr.java_conf.SenseMusicClock.Music

import android.content.ContentResolver
import android.util.Log
import androidx.paging.LOG_TAG
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState

class AllMusicPagingSource(
    private val fetcher: LocalMusicFetcher,
    private val resolver: ContentResolver,
    private val limit : Int = 1000
) : PagingSource<Int, LocalMusicFetcher.MediaStoreAudioSummary>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LocalMusicFetcher.MediaStoreAudioSummary> {
        return try {

            // 次にロードするページのキー
            val page = params.key ?: 1
            // 1ページあたりのアイテム数


            Log.d("LIST_/AllMusicPagingSource", "Load page")
            val response = fetcher.FetchAllMusicPage(resolver, page = page, limit = limit)
            Log.d("LIST_/AllMusicPagingSource", "Loaded page $page with ${response.size} items")

            return LoadResult.Page(
                data = response,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.isEmpty()) null else page + 1
            )


        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, LocalMusicFetcher.MediaStoreAudioSummary>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }


}