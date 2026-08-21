package me.magnum.melonds.ui.romlist.ui

import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.SortingMode
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.common.component.romlist.ConfigurableRomItem
import me.magnum.melonds.ui.romlist.RomIcon

@Composable
fun RomListScreen(
    roms: List<Rom>?,
    isRefreshing: Boolean,
    hasSearchDirectories: Boolean,
    onSearchQueryChange: (String?) -> Unit,
    onSortChange: (SortingMode) -> Unit,
    onFirmwareBoot: (ConsoleType) -> Unit,
    onRomSelected: (Rom) -> Unit,
    onRomConfigClick: (Rom) -> Unit,
    onRefresh: () -> Unit,
    onDirectorySelected: (Uri) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDsiWareManager: () -> Unit,
    retrieveRomIcon: suspend (Rom) -> RomIcon,
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    BackHandler(isSearchActive) {
        isSearchActive = false
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent {
            if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                when (it.key) {
                    Key.Menu -> {
                        showOverflowMenu = !showOverflowMenu
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        },
        topBar = {
            Box(Modifier.background(MaterialTheme.colors.primaryVariant).statusBarsPadding()) {
                if (isSearchActive) {
                    SearchTopBar(
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                            onSearchQueryChange(query)
                        },
                        onClose = {
                            isSearchActive = false
                            searchQuery = ""
                            onSearchQueryChange(null)
                        },
                    )
                } else {
                    RomListTopBar(
                        showOverflowMenu = showOverflowMenu,
                        onOverflowMenuChange = { showOverflowMenu = it },
                        onSearchClick = { isSearchActive = true },
                        onSortChange = onSortChange,
                        onFirmwareBoot = onFirmwareBoot,
                        onNavigateToDsiWareManager = onNavigateToDsiWareManager,
                        onRefresh = onRefresh,
                        onNavigateToSettings = onNavigateToSettings,
                    )
                }
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        if (hasSearchDirectories) {
            RomList(
                modifier = Modifier.fillMaxSize(),
                roms = roms,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                contentPadding = padding,
            ) { modifier, rom ->
                ConfigurableRomItem(
                    modifier = modifier.fillMaxWidth(),
                    rom = rom,
                    onClick = { onRomSelected(rom) },
                    onConfigClick = { onRomConfigClick(rom) },
                    retrieveTitleIcon = { retrieveRomIcon(rom) },
                )
            }
        } else {
            NoSearchDirectoriesContent(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                onDirectorySelected = onDirectorySelected,
            )
        }
    }
}

@Composable
private fun RomListTopBar(
    showOverflowMenu: Boolean,
    onOverflowMenuChange: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onSortChange: (SortingMode) -> Unit,
    onFirmwareBoot: (ConsoleType) -> Unit,
    onNavigateToDsiWareManager: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFirmwareMenu by remember { mutableStateOf(false) }

    val actionFocusRequesters = remember { List(4) { FocusRequester() } }
    var lastFocusedActionIndex by rememberSaveable { mutableIntStateOf(-1) }

    TopAppBar(
        title = {
            Text(stringResource(R.string.app_name))
        },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        actions = {
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                Row(
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            if (lastFocusedActionIndex != -1) {
                                actionFocusRequesters[lastFocusedActionIndex].requestFocus()
                            } else {
                                actionFocusRequesters.first().requestFocus()
                            }
                        }
                    }.focusable(),
                ) {
                    IconButton(
                        modifier = Modifier.focusRequester(actionFocusRequesters[0]).onFocusChanged {
                            if (it.isFocused) lastFocusedActionIndex = 0
                        },
                        onClick = onSearchClick,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search_roms),
                        )
                    }

                    // Sort menu
                    Box {
                        IconButton(
                            modifier = Modifier.focusRequester(actionFocusRequesters[1]).onFocusChanged {
                                if (it.isFocused) lastFocusedActionIndex = 1
                            },
                            onClick = { showSortMenu = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sort),
                                contentDescription = stringResource(R.string.action_sort_roms),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            DropdownMenuItem(onClick = {
                                showSortMenu = false
                                onSortChange(SortingMode.ALPHABETICALLY)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.SortByAlpha,
                                    contentDescription = null,
                                )
                                Text(
                                    modifier = Modifier.padding(start = 8.dp),
                                    text = stringResource(R.string.action_sort_alphabetically),
                                )
                            }
                            DropdownMenuItem(onClick = {
                                showSortMenu = false
                                onSortChange(SortingMode.RECENTLY_PLAYED)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                )
                                Text(
                                    modifier = Modifier.padding(start = 8.dp),
                                    text = stringResource(R.string.action_sort_recently_played),
                                )
                            }
                        }
                    }

                    // Firmware boot menu
                    Box {
                        IconButton(
                            modifier = Modifier.focusRequester(actionFocusRequesters[2]).onFocusChanged {
                                if (it.isFocused) lastFocusedActionIndex = 2
                            },
                            onClick = { showFirmwareMenu = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_firmware),
                                contentDescription = stringResource(R.string.action_boot_firmware),
                            )
                        }
                        DropdownMenu(
                            expanded = showFirmwareMenu,
                            onDismissRequest = { showFirmwareMenu = false },
                        ) {
                            DropdownMenuItem(
                                onClick = {
                                    showFirmwareMenu = false
                                    onFirmwareBoot(ConsoleType.DS)
                                },
                            ) {
                                Text(stringResource(R.string.console_ds))
                            }
                            DropdownMenuItem(
                                onClick = {
                                    showFirmwareMenu = false
                                    onFirmwareBoot(ConsoleType.DSi)
                                },
                            ) {
                                Text(stringResource(R.string.console_dsi))
                            }
                        }
                    }

                    // Overflow menu
                    Box {
                        IconButton(
                            modifier = Modifier.focusRequester(actionFocusRequesters[3]).onFocusChanged {
                                if (it.isFocused) lastFocusedActionIndex = 3
                            },
                            onClick = { onOverflowMenuChange(true) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { onOverflowMenuChange(false) },
                        ) {
                            DropdownMenuItem(onClick = {
                                onOverflowMenuChange(false)
                                onNavigateToDsiWareManager()
                            }) {
                                Text(stringResource(R.string.dsiware_manager))
                            }
                            DropdownMenuItem(onClick = {
                                onOverflowMenuChange(false)
                                onRefresh()
                            }) {
                                Text(stringResource(R.string.action_refresh_rom_list))
                            }
                            DropdownMenuItem(onClick = {
                                onOverflowMenuChange(false)
                                onNavigateToSettings()
                            }) {
                                Text(stringResource(R.string.settings))
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    ) {
        TextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.hint_search_roms),
                    color = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
                )
            },
            leadingIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colors.onPrimary,
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            colors = TextFieldDefaults.textFieldColors(
                textColor = MaterialTheme.colors.onPrimary,
                backgroundColor = Color.Transparent,
                cursorColor = MaterialTheme.colors.onPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}