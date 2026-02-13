package jp.gr.java_conf.SenseMusicClock

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import java.util.concurrent.ThreadLocalRandom
import java.util.UUID


interface Track {

     val title: String  //トラックタイトル
     val album: String  //アルバムタイトル
     val artist: String  //アーティスト名
     var albumArtUri : Uri?

     var uuid : UUID
 }



/*
data class YoutubeContent (
    override val title: String , //トラックタイトル
    override val album: String , //アルバムタイトル
    override val artist: String  ,//アーティスト名
    override var albumArt : Bitmap?,


) : Track


 */


data class SpotifyTrack (

    override val title: String , //トラックタイトル
    override val album: String , //アルバムタイトル
    override val artist: String  ,//アーティスト名
    override var albumArtUri : Uri?,
    override var uuid: UUID = fastRandomUUID(),
    val trackId: String?,



) : Track




/*
data class LocalTrack(
    override val title: String , //トラックタイトル
    override val album: String , //アルバムタイトル
    override val artist: String  ,//アーティスト名
    override var albumArtUri : Uri?,
    override var uuid: UUID = fastRandomUUID(),
    val id: Long , //コンテントプロバイダに登録されたID
    val albumId: Long, //同じくトラックのアルバムのID
    val artistId: Long,//同じくトラックのアーティストのID
    val path: String, //実データのPATH
    val uri: Uri, // URI
    val trackNo: Int, // アルバムのトラックナンバ


) : Track



 */

// kotlin


fun fastRandomUUID(): UUID {
    val rnd = ThreadLocalRandom.current()
    var msb = rnd.nextLong()
    var lsb = rnd.nextLong()

    // version (bits 12-15) を 4 にセット
    val versionClearMask = (0xFL shl 12).toLong().inv() // 0xF000 の反転マスク
    msb = (msb and versionClearMask) or (0x4L shl 12)

    // variant (bits 62-63) を 10 にセット
    val lower62Mask = (1L shl 62) - 1                 // 下位62ビットを保持するマスク
    lsb = (lsb and lower62Mask) or (1L shl 63)        // bit63 = 1, bit62 = 0

    return UUID(msb, lsb)
}
