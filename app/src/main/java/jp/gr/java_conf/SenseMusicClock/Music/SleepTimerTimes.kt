package jp.gr.java_conf.SenseMusicClock.Music

enum class SleepTimerTimes {
    OFF,
    MIN_5,
    MIN_15,
    MIN_30,
    HOUR_1,
    HOUR_2,
    HOUR_5,


}

val sleepTimerTimesToMinutes = mapOf(
    SleepTimerTimes.OFF to 0,
    SleepTimerTimes.MIN_5 to 5,
    SleepTimerTimes.MIN_15 to 15,
    SleepTimerTimes.MIN_30 to 30,
    SleepTimerTimes.HOUR_1 to 60,
    SleepTimerTimes.HOUR_2 to 120,
    SleepTimerTimes.HOUR_5 to 300
)

val sleepTimerTimesToText = mapOf(
    SleepTimerTimes.OFF to "OFF",
    SleepTimerTimes.MIN_5 to "5分",
    SleepTimerTimes.MIN_15 to "15分",
    SleepTimerTimes.MIN_30 to "30分",
    SleepTimerTimes.HOUR_1 to "1時間",
    SleepTimerTimes.HOUR_2 to "2時間",
    SleepTimerTimes.HOUR_5 to "5時間"
)