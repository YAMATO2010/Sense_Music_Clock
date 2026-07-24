package jp.gr.java_conf.SenseMusicClock

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class AppViewModel(application: Application) : AndroidViewModel(application) {
    /*
    private val _MusicList : MutableLiveData<MutableList<Track>>  = MutableLiveData<MutableList<Tra>>(mutableListOf<Music>())
    val MusicList :LiveData<MutableList<Track>> get() = _MusicList

    private val _timerTime : MutableLiveData<Long>  = MutableLiveData<Long>()
    val timerTime :LiveData<Long> get() = _timerTime
    private val _isTimer : MutableLiveData<Boolean>  = MutableLiveData<Boolean>(true)
    val isTimer :LiveData<Boolean> get() = _isTimer

   private var job : Job?  =null







    fun addMusicList(music : Track){

        _MusicList.value?.add(music)

    }

    fun timerStart(seconds: Int){
        _isTimer.value = true
        job?.cancel()
        job = viewModelScope.launch {
            for (i in seconds downTo 0) {
               _timerTime.postValue(i * 10L)
                delay(100)
            }
        }


    }
    fun timerStop(){

        job?.cancel()
    }
    fun alarmStart(){
        _isTimer.value = false


    }
    fun alarmStop(){

    }
    */


}