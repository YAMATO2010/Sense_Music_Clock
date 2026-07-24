/*package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.graphics.Bitmap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment.Companion.CenterHorizontally
import androidx.glance.layout.Alignment.Companion.CenterVertically
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.google.firebase.annotations.PreviewApi
import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.ui.MainActivity



@Composable
fun ControlWidget_G(
    title: String,
    artwork: Bitmap?,
    background: Bitmap?,
    playing: Boolean,


    ) {

    val size = LocalSize.current
    val isTall = size.height.value > size.width.value * 0.8
    if (isTall) {
        ControlWidget_G_tall(title, artwork, background, playing)
    } else {
        ControlWidget_G_land(title, artwork, background, playing)
    }


}


@Composable
fun ControlWidget_G_tall(title: String = "unknown", artwork: Bitmap? = null, background: Bitmap? = null, playing: Boolean =false) {

    val artPercent = 0.5f


    val height = LocalSize.current.height
    val tmpArtHeight = height * artPercent

    val artSize = if (tmpArtHeight < LocalSize.current.width) {
        tmpArtHeight
    } else {
        LocalSize.current.width
    }


    val etcHeight = height - artSize





    Image(
        provider = if (background == null) {
            ImageProvider(R.drawable.gradient3)
        } else {
            ImageProvider(background)
        },
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )

    Column(
        modifier = GlanceModifier.then(
            if (background == null) {
                GlanceModifier.background(ImageProvider(R.drawable.gradient3))
            } else {
                GlanceModifier
            }.fillMaxSize().clickable(
                onClick = actionStartActivity<MainActivity>()
            )
        )
    ) {
        Image(
            provider = if (artwork == null) {
                ImageProvider(R.drawable.default_album_art)
            } else {
                ImageProvider(artwork)
            },
            contentDescription = null,
            modifier = GlanceModifier.size(artSize).padding(vertical = 1.dp),
            contentScale = ContentScale.Fit,
        )
        Box(modifier = GlanceModifier.fillMaxWidth().height(etcHeight)) {
            TextAndButtons(title = title, playing = playing)
        }


    }
}

@Composable
fun ControlWidget_G_land(title: String, artwork: Bitmap?, background: Bitmap?, playing: Boolean) {

    val width = LocalSize.current.width
    val height = LocalSize.current.height
    val artPercent = 0.5f
    val tmpArtWidth = width * artPercent
    val artSize = if (tmpArtWidth < LocalSize.current.height) {
        tmpArtWidth
    } else {
        height
    }

    val etcWidth = width - artSize



    Image(
        provider = if (background == null) {
            ImageProvider(R.drawable.gradient3)
        } else {
            ImageProvider(background)
        },
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )
    Row(
        modifier = GlanceModifier.then(
            if (background == null) {
                GlanceModifier.background(ImageProvider(R.drawable.gradient3))
            } else {
                GlanceModifier
            }.fillMaxSize()
                .clickable(
                    onClick = actionStartActivity<MainActivity>()
                )
        )
    )
    {


        Image(
            provider = if (artwork == null) {
                ImageProvider(R.drawable.default_album_art)
            } else {
                ImageProvider(artwork)
            },
            contentDescription = null,
            modifier = GlanceModifier.size(artSize).padding(1.dp),
            contentScale = ContentScale.Fit,
        )
        Box(modifier = GlanceModifier.fillMaxHeight().width(etcWidth)) {

            TextAndButtons(title = title, playing = playing)
        }


    }


}

@Composable
fun TextAndButtons(title: String, playing: Boolean) {

    val height = LocalSize.current.height
    val titlePercent = 0.4f
    val ButtonsPercent = 0.6f
    val titleHeight = height * titlePercent
    val buttonsHeight = height * ButtonsPercent

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = CenterVertically,
        horizontalAlignment = CenterHorizontally
    ) {

        Text(
            text = title,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(titleHeight)
                .padding(1.dp)
        )
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(buttonsHeight)
        ) {

            ActionButtons(playing = playing)
        }
    }
}

@Composable
fun ActionButtons(playing: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(1.dp),
        verticalAlignment = CenterVertically,
        horizontalAlignment = CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_previous),
            contentDescription = null,
            modifier = GlanceModifier
                .clickable(
                    onClick = actionRunCallback<ActionPrev>()
                )
        )
        Image(
            provider = if (playing) {
                ImageProvider(androidx.media3.session.R.drawable.media3_icon_pause)
            } else {
                ImageProvider(androidx.media3.session.R.drawable.media3_icon_play)
            },
            contentDescription = null,
            modifier = GlanceModifier

                .clickable(
                    onClick = actionRunCallback<ActionPlayPause>()
                )
        )
        Image(
            provider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_next),
            contentDescription = null,
            modifier = GlanceModifier
                .clickable(
                    onClick = actionRunCallback<ActionNext>()
                )
        )
    }
}

 */

