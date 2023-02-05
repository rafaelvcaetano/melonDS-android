package me.magnum.melonds.ui.romdetails.ui

import android.net.Uri
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Rom
import me.magnum.melonds.domain.model.RomConfig
import me.magnum.melonds.domain.model.RomIconFiltering
import me.magnum.melonds.ui.common.MelonPreviewSet
import me.magnum.melonds.ui.romdetails.model.RomDetailsTab
import me.magnum.melonds.ui.romlist.RomIcon
import me.magnum.melonds.ui.theme.MelonTheme
import java.util.*

@OptIn(ExperimentalPagerApi::class)
@Composable
fun RomHeaderUi(
    modifier: Modifier,
    rom: Rom,
    pagerState: PagerState,
    loadRomIcon: suspend () -> RomIcon,
    onLaunchRom: () -> Unit,
    onNavigateBack: () -> Unit,
    onTabClicked: (RomDetailsTab) -> Unit,
) {
    CompositionLocalProvider(LocalElevationOverlay provides null) {
        Surface(
            modifier = modifier,
            elevation = 4.dp,
            color = MaterialTheme.colors.surface,
        ) {
            Column {
                TopAppBar(
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 0.dp,
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    }
                )
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    var romIcon by remember {
                        mutableStateOf<RomIcon?>(null)
                    }
                    LaunchedEffect(rom) {
                        romIcon = loadRomIcon()
                    }

                    if (romIcon == null) {
                        val infiniteAnimation = rememberInfiniteTransition()

                        val animatedColor = infiniteAnimation.animateColor(
                            initialValue = Color.DarkGray,
                            targetValue = Color.LightGray,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse,
                            )
                        )
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(animatedColor.value))

                        Spacer(Modifier.width(12.dp))
                    } else {
                        val currentRomIcon = romIcon
                        currentRomIcon?.bitmap?.let {
                            Image(
                                modifier = Modifier.size(64.dp),
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                filterQuality = when (currentRomIcon.filtering) {
                                    RomIconFiltering.NONE -> FilterQuality.None
                                    RomIconFiltering.LINEAR -> DrawScope.DefaultFilterQuality
                                },
                            )

                            Spacer(Modifier.width(12.dp))
                        }
                    }

                    Column {
                        Text(
                            text = rom.name,
                            style = MaterialTheme.typography.h5,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Column(Modifier.padding(top = 4.dp)) {
                            Text(
                                text = "Producer",
                                style = MaterialTheme.typography.body1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(50),
                    onClick = onLaunchRom,
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary, contentColor = MaterialTheme.colors.onSecondary),
                ) {
                    Text(text = stringResource(R.string.play).uppercase())
                }

                Spacer(Modifier.height(4.dp))

                TabRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedTabIndex = pagerState.currentPage,
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.primary,
                    indicator = {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.pagerTabIndicatorOffset(pagerState, it),
                            color = MaterialTheme.colors.secondary,
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == RomDetailsTab.CONFIG.tabIndex,
                        onClick = { onTabClicked(RomDetailsTab.CONFIG) },
                        text = {
                            Text(
                                text = stringResource(id = R.string.rom_details_configuration_tab).uppercase(),
                                color = MaterialTheme.colors.onBackground,
                            )
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == RomDetailsTab.RETRO_ACHIEVEMENTS.tabIndex,
                        onClick = { onTabClicked(RomDetailsTab.RETRO_ACHIEVEMENTS) },
                        text = {
                            Text(
                                text = stringResource(id = R.string.retro_achievements_tab).uppercase(),
                                color = MaterialTheme.colors.onBackground
                            )
                        }
                    )
                }
            }
        }
    }
}

@ExperimentalPagerApi
@MelonPreviewSet
@Composable
private fun PreviewRomHeaderUi() {
    val bitmap = createBitmap(1, 1).apply { this[0, 0] = 0xFF777777.toInt() }

    MelonTheme {
        val pagerState = rememberPagerState(RomDetailsTab.CONFIG.tabIndex)
        val coroutineScope = rememberCoroutineScope()

        RomHeaderUi(
            modifier = Modifier.fillMaxWidth(),
            rom = Rom(
                name = "Professor Layton and the Lost Future",
                fileName = "layton.nds",
                uri = Uri.EMPTY,
                parentTreeUri = Uri.EMPTY,
                config = RomConfig(),
                lastPlayed = Date(),
                isDsiWareTitle = false,
            ),
            loadRomIcon = {
                RomIcon(
                    bitmap,
                    RomIconFiltering.NONE,
                )
            },
            pagerState = pagerState,
            onLaunchRom = { },
            onNavigateBack = { },
            onTabClicked = {
                coroutineScope.launch {
                    pagerState.scrollToPage(it.tabIndex)
                }
            }
        )
    }
}