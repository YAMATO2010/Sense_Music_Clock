package jp.gr.java_conf.SenseMusicClock.Music.Data

data class FileItem(
    val relativePath: String,
    val fileName: String
)

fun FileItem.displayName(): String {
    return if (relativePath.endsWith("/")) {
        "$relativePath$fileName"
    } else {
        "$relativePath/$fileName"
    }
}

