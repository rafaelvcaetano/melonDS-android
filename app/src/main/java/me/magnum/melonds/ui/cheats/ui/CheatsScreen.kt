package me.magnum.melonds.ui.cheats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.magnum.melonds.R
import me.magnum.melonds.ui.cheats.CheatsNavigation
import me.magnum.melonds.ui.cheats.CheatsViewModel
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState

@Composable
fun CheatsScreen(
    viewModel: CheatsViewModel,
    initialScreen: CheatsNavigation,
) {
    val enabledCheatsTitle = stringResource(R.string.enabled_cheats)
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route?.substringBefore("/")
    val isSearchRoute = currentRoute == CheatsNavigation.Search.serializer().descriptor.serialName
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val isSearchVisible = isSearchActive || isSearchRoute
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            searchFocusRequester.requestFocus()
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val showEnabledCheatsButton = remember(currentRoute) {
        currentRoute == CheatsNavigation.GameFolders.serializer().descriptor.serialName ||
            currentRoute == CheatsNavigation.FolderCheats.serializer().descriptor.serialName ||
                currentRoute == CheatsNavigation.Search.serializer().descriptor.serialName
    }
    val appBarTitle = remember(currentRoute) {
        when (currentRoute) {
            CheatsNavigation.GameFolders.serializer().descriptor.serialName -> currentBackStackEntry?.toRoute<CheatsNavigation.GameFolders>()?.gameName
            CheatsNavigation.FolderCheats.serializer().descriptor.serialName -> currentBackStackEntry?.toRoute<CheatsNavigation.FolderCheats>()?.folderName
            CheatsNavigation.EnabledCheats.serializer().descriptor.serialName -> enabledCheatsTitle
            else -> null
        }
    }
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberScaffoldState(snackbarHostState = snackbarHostState)
    val coroutineScope = rememberCoroutineScope()
    val navigateBack = {
        if (isSearchActive) {
            isSearchActive = false
            viewModel.clearSearchQuery()
        } else if (navController.previousBackStackEntry == null) {
            // The first screen (the loading screen) is not considered as an entry. So, previousBackStackEntry will be null
            viewModel.commitCheatChanges()
        } else {
            navController.navigateUp()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openGamesEvent.collect {
            navController.navigate(CheatsNavigation.GameList)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.openFoldersEvent.collect {
            navController.navigate(CheatsNavigation.GameFolders(it.newTitle))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.openCheatsEvent.collect {
            isSearchActive = false
            navController.navigate(CheatsNavigation.FolderCheats(it.newTitle))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.openEnabledCheatsEvent.collect {
            isSearchActive = false
            navController.navigate(CheatsNavigation.EnabledCheats)
        }
    }

    BackHandler {
        navigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        topBar = {
            Box(Modifier.background(MaterialTheme.colors.primaryVariant).statusBarsPadding()) {
                TopAppBar(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSearchVisible) {
                            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

                            TextField(
                                modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                value = searchQuery,
                                onValueChange = { value ->
                                    viewModel.setSearchQuery(value)
                                    if (value.isNotEmpty() && !isSearchRoute) {
                                        val isInsideFolder = currentRoute == CheatsNavigation.FolderCheats.serializer().descriptor.serialName
                                        navController.navigate(CheatsNavigation.Search(searchOnlyInCurrentFolder = isInsideFolder)) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.hint_search),
                                        color = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f),
                                    )
                                },
                                leadingIcon = {
                                    IconButton(onClick = {
                                        if (isSearchRoute) {
                                            navController.navigateUp()
                                        }
                                        isSearchActive = false
                                        viewModel.clearSearchQuery()
                                    }) {
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
                        } else {
                            Row(Modifier.fillMaxHeight().width(68.dp), verticalAlignment = Alignment.CenterVertically) {
                                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                                    IconButton(onClick = { navigateBack() }) {
                                        Icon(
                                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                            Row(Modifier.fillMaxHeight().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                ProvideTextStyle(value = MaterialTheme.typography.h6) {
                                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                                        Text(
                                            text = appBarTitle ?: stringResource(R.string.cheats),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (currentRoute == CheatsNavigation.GameFolders.serializer().descriptor.serialName ||
                                currentRoute == CheatsNavigation.FolderCheats.serializer().descriptor.serialName
                            ) {
                                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                                    IconButton(onClick = {
                                        viewModel.clearSearchQuery()
                                        isSearchActive = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.hint_search),
                                        )
                                    }
                                }
                            }
                        }
                        if (showEnabledCheatsButton) {
                            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                                IconButton(onClick = { viewModel.openEnabledCheats() }) {
                                    Icon(
                                        painter = rememberVectorPainter(Icons.Outlined.CheckBox),
                                        contentDescription = stringResource(R.string.enabled_cheats),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = initialScreen,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { -it } },
            popEnterTransition = { slideInHorizontally { -it } },
            popExitTransition = { slideOutHorizontally { it } },
        ) {
            composable<CheatsNavigation.GameList> {
                val games by viewModel.games.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                GameListScreen(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    games = games,
                    onGameClick = { viewModel.setSelectedGame(it) },
                )
            }
            composable<CheatsNavigation.GameFolders> {
                val folders by viewModel.folders.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                FolderListScreen(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    folders = folders,
                    onFolderClick = { viewModel.setSelectedFolder(it) },
                    onAddFolder = { viewModel.addFolder(it) },
                )
            }
            composable<CheatsNavigation.FolderCheats> {
                val cheats by viewModel.folderCheats.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                CheatListScreen(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    cheats = cheats,
                    onCheatClick = viewModel::toggleCheat,
                    onAddNewCheat = viewModel::addNewCheat,
                    onUpdateCheat = viewModel::updateCheat,
                    onDeleteCheatClick = {
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.cheat_deleted, it.name),
                                actionLabel = resources.getString(R.string.undo)
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoCheatDeletion(it)
                            }
                        }
                        viewModel.deleteCheat(it)
                    },
                )
            }
            composable<CheatsNavigation.Search> {
                val searchRoute = it.toRoute<CheatsNavigation.Search>()
                val searchResultsFlow = remember(searchRoute.searchOnlyInCurrentFolder) {
                    viewModel.getFilteredCheatsFlow(searchRoute.searchOnlyInCurrentFolder).map { CheatsScreenUiState.Ready(it) }
                }
                val searchResults by searchResultsFlow.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

                CheatSearchScreen(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    searchResults = searchResults,
                    query = searchQuery,
                    onCheatClick = viewModel::toggleCheat,
                    onUpdateCheat = viewModel::updateCheat,
                    onDeleteCheatClick = { folder, cheat ->
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.cheat_deleted, cheat.name),
                                actionLabel = resources.getString(R.string.undo),
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoCheatDeletion(cheat)
                            }
                        }
                        viewModel.deleteCheat(cheat, folder)
                    },
                )

                BackHandler {
                    if (searchQuery.isNotEmpty()) {
                        // 1st back: just clear the field, stay on the search screen
                        viewModel.clearSearchQuery()
                    } else {
                        // 2nd back: dismiss the search screen
                        navController.navigateUp()
                        isSearchActive = false
                    }
                }
            }
            composable<CheatsNavigation.EnabledCheats> {
                val cheats by viewModel.selectedGameCheats.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                EnabledCheatsListScreen(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    cheats = cheats,
                    onCheatClick = { viewModel.toggleCheat(it.cheat) }
                )
            }
        }
    }
}
