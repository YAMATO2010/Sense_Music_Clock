package jp.gr.java_conf.SenseMusicClock

import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView

 interface Track {

     val title: String  //トラックタイトル
     val album: String  //アルバムタイトル
     val artist: String  //アーティスト名
     var albumArt : Bitmap?
 }



/*
data class YoutubeContent (
    override val title: String , //トラックタイトル
    override val album: String , //アルバムタイトル
    override val artist: String  ,//アーティスト名
    override var albumArt : Bitmap?,


) : Track


 */

/*
data class SpotifyTrack (

    override val title: String , //トラックタイトル
    override val album: String , //アルバムタイトル
    override val artist: String  ,//アーティスト名
    override var albumArt : Bitmap?,
    val trackId: String?,
    val albumArtUri: String?



) : Track


 */
data class localTrack(
    override val title: String , //トラックタイトル
    override val album: String , //アルバムタイトル
    override val artist: String  ,//アーティスト名
    override var albumArt : Bitmap?,
    val id: Long , //コンテントプロバイダに登録されたID
    val albumId: Long, //同じくトラックのアルバムのID
    val artistId: Long,//同じくトラックのアーティストのID
    val path: String, //実データのPATH
    val uri: Uri, // URI
    val trackNo: Int, // アルバムのトラックナンバ


) : Track


