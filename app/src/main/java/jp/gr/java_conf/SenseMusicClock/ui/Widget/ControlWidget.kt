package jp.gr.java_conf.SenseMusicClock.ui.Widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider

import androidx.glance.layout.Alignment
import androidx.glance.layout.Alignment.Companion.CenterHorizontally
import androidx.glance.layout.Alignment.Companion.CenterVertically
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import jp.gr.java_conf.SenseMusicClock.PrefsManager


import jp.gr.java_conf.SenseMusicClock.R
import jp.gr.java_conf.SenseMusicClock.ui.MainActivity

class ControlWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // In this method, load data needed to render the AppWidget.
        // Use `withContext` to switch to another thread for long running
        // operations.

        Log.d("ControlWidget", "[WidgetTrace] provideGlance called for widget id: $id")

        val initialState = WidgetStateManager.getWidgetState(context)


        provideContent {
            val state by WidgetStateManager.getWidgetStateFlow(context).collectAsState(
                initial = initialState
            )
            val isBackgroundEnabled by PrefsManager.getWidgetBackgroundFlow(context).collectAsState(initial = false)
            val isBackgroundRandom by PrefsManager.getRandomBackgroundFlow(context).collectAsState(initial = false)

            Log.d("ControlWidget", "[WidgetTrace] Rendering widget with state: $state")

            val currentSize = LocalSize.current
            val isTall =
                ControlWidgetUpdater.isTall(currentSize.height.value, currentSize.width.value)
            val artwork by produceState<Bitmap?>(initialValue = null, state.artwork) {
                value = ControlWidgetUpdater.resolveArtworkBitmap(context, state.artwork)
            }

            val background by produceState<Bitmap?>(initialValue = null, isTall,isBackgroundEnabled,isBackgroundRandom,state.title) {

                value = ControlWidgetUpdater.resolveBackground(context, isTall)?.drawable?.toBitmap()
            }
            Box(modifier = GlanceModifier.fillMaxSize()) {

                ControlWidget_G(
                    title = state.title,
                    artwork = artwork,
                    background = background,
                    playing = state.playing
                )
            }

        }
    }

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
    fun ControlWidget_G_tall(
        title: String = "unknown",
        artwork: Bitmap? = null,
        background: Bitmap? = null,
        playing: Boolean = false
    ) {

        val artPercent = 0.5f


        val height = LocalSize.current.height
        val tmpArtHeight = height * artPercent

        val artSize = if (tmpArtHeight < LocalSize.current.width) {
            tmpArtHeight
        } else {
            LocalSize.current.width
        }


        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(
                onClick = actionStartActivity<MainActivity>()
            )
        ) {

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
                modifier = GlanceModifier.background(ImageProvider(R.drawable.gradient3))
                    .fillMaxSize(),
                verticalAlignment = CenterVertically,
                horizontalAlignment = CenterHorizontally
            ) {
                Image(
                    provider = if (artwork == null) {
                        ImageProvider(R.drawable.default_album_art)
                    } else {
                        ImageProvider(artwork)
                    },
                    contentDescription = null,
                    modifier = GlanceModifier.size(artSize),
                    contentScale = ContentScale.Fit,
                )

                Box(modifier = GlanceModifier.fillMaxWidth().height(height - artSize)) {
                    TextAndButtons(
                        title = title,
                        playing = playing,
                        availableWidth = LocalSize.current.width,
                        availableHeight = height - artSize
                    )
                }
            }
        }
    }

    @Composable
    fun ControlWidget_G_land(
        title: String,
        artwork: Bitmap?,
        background: Bitmap?,
        playing: Boolean
    ) {

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


        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(
                onClick = actionStartActivity<MainActivity>()
            )
        ) {


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
                modifier = GlanceModifier.background(ImageProvider(R.drawable.gradient3))
                    .fillMaxSize()
                    .clickable(
                        onClick = actionStartActivity<MainActivity>()
                    ),
                verticalAlignment = CenterVertically,
                horizontalAlignment = CenterHorizontally
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
                    TextAndButtons(
                        title = title,
                        playing = playing,
                        availableWidth = etcWidth,
                        availableHeight = height
                    )
                }


            }
        }


    }

    @Composable
    fun TextAndButtons(
        title: String,
        playing: Boolean,
        availableWidth: Dp,
        availableHeight: Dp
    ) {
        val oneHeightBlock = availableHeight / 8f
        val buttonHeight = oneHeightBlock * 3

        Column(
            modifier = GlanceModifier.width(availableWidth).height(availableHeight),
            verticalAlignment = CenterVertically,
            horizontalAlignment = CenterHorizontally
        ) {

            Spacer(
                modifier = GlanceModifier.height(oneHeightBlock)
            )



            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(
                        day = Color.White,
                        night = Color.White
                    ),
                    textAlign = TextAlign.Center
                ),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(oneHeightBlock * 2)
                    .padding(1.dp),

                )
            Spacer(
                modifier = GlanceModifier.height(oneHeightBlock)
            )
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(buttonHeight)
            ) {

                ActionButtons(
                    playing = playing,
                    availableWidth = availableWidth,
                    availableHeight = buttonHeight
                )
            }
            Spacer(
                modifier = GlanceModifier.height(oneHeightBlock)
            )
        }
    }


    @Composable
    fun ActionButtons(
        playing: Boolean,
        availableWidth: Dp,
        availableHeight: Dp
    ) {
        val oneWeight = availableWidth / 7f
        val iconSize = if (oneWeight < availableHeight) oneWeight else availableHeight

        Row(
            modifier = GlanceModifier.fillMaxSize().padding(1.dp),
            verticalAlignment = CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(
                modifier = GlanceModifier.width(oneWeight)
            )
            Image(
                provider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_previous),
                contentDescription = null,
                modifier = GlanceModifier
                    .clickable(
                        onClick = actionRunCallback<ActionPrev>()
                    )
                    .size(iconSize)
            )
            Spacer(
                modifier = GlanceModifier.width(oneWeight)
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
                    ).size(iconSize)
            )
            Spacer(
                modifier = GlanceModifier.width(oneWeight)
            )
            Image(
                provider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_next),
                contentDescription = null,
                modifier = GlanceModifier
                    .clickable(
                        onClick = actionRunCallback<ActionNext>()
                    ).size(iconSize)
            )
            Spacer(
                modifier = GlanceModifier.width(oneWeight)
            )
        }
    }


}
