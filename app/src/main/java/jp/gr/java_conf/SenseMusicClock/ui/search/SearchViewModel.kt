package jp.gr.java_conf.SenseMusicClock.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jp.gr.java_conf.SenseMusicClock.Music.Data.SearchHistorys.SearchHistory
import jp.gr.java_conf.SenseMusicClock.Music.LocalAlbumFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalArtistFetcher
import jp.gr.java_conf.SenseMusicClock.Music.LocalMusicFetcher
import kotlinx.coroutines.Job

class SearchViewModel : ViewModel() {


    var searchJob: Job? = null
    private val _currentSearchType: MutableLiveData<SearchActivity.SearchType> =
        MutableLiveData(SearchActivity.SearchType.ARTIST)
    val currentSearchType: LiveData<SearchActivity.SearchType> get() = _currentSearchType

    private val _currentAlbumResult: MutableLiveData<List<LocalAlbumFetcher.MediaStoreAlbumSummary>> =
        MutableLiveData(emptyList())
    val currentAlbumResult: LiveData<List<LocalAlbumFetcher.MediaStoreAlbumSummary>> get() = _currentAlbumResult

    private val _currentMusicResult: MutableLiveData<List<LocalMusicFetcher.MediaStoreAudioSummary>> =
        MutableLiveData(emptyList())
    val currentMusicResult: LiveData<List<LocalMusicFetcher.MediaStoreAudioSummary>> get() = _currentMusicResult

    private val _currentArtistResult: MutableLiveData<List<LocalArtistFetcher.MediaStoreArtistSummary>> =
        MutableLiveData(emptyList())
    val currentArtistResult: LiveData<List<LocalArtistFetcher.MediaStoreArtistSummary>> get() = _currentArtistResult

    private val _searchHistory: MutableLiveData<List<SearchHistory>> = MutableLiveData(emptyList())

    val searchHistory: LiveData<List<SearchHistory>> get() = _searchHistory

    private val _isSearchMode: MutableLiveData<Boolean> = MutableLiveData(false)
    val isSearchMode: LiveData<Boolean> get() = _isSearchMode
    fun setSearchType(type: SearchActivity.SearchType) {
        _currentSearchType.value = type
    }

    fun setAlbumResult(result: List<LocalAlbumFetcher.MediaStoreAlbumSummary>) {
        _currentAlbumResult.value = result
    }

    fun setMusicResult(result: List<LocalMusicFetcher.MediaStoreAudioSummary>) {
        _currentMusicResult.value = result
    }

    fun setArtistResult(result: List<LocalArtistFetcher.MediaStoreArtistSummary>) {
        _currentArtistResult.value = result
    }

    fun setAllResult(
        artistResult: List<LocalArtistFetcher.MediaStoreArtistSummary>,
        albumResult: List<LocalAlbumFetcher.MediaStoreAlbumSummary>,
        musicResult: List<LocalMusicFetcher.MediaStoreAudioSummary>
    ) {
        setArtistResult(artistResult)
        setAlbumResult(albumResult)
        setMusicResult(musicResult)
    }

    fun clearResult() {
        setArtistResult(emptyList())
        setAlbumResult(emptyList())
        setMusicResult(emptyList())
    }

    fun setSearchHistory(history: List<SearchHistory>) {
        _searchHistory.value = history
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }

    fun setSearchMode(isSearchMode: Boolean) {
        _isSearchMode.value = isSearchMode
    }


}