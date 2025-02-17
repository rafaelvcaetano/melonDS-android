package me.magnum.melonds.ui.cheats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.serialization.ExperimentalSerializationApi
import me.magnum.melonds.R
import me.magnum.melonds.ui.cheats.CheatsNavigation
import me.magnum.melonds.ui.cheats.CheatsViewModel
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun CheatsScreen(viewModel: CheatsViewModel) {
    val enabledCheatsTitle = stringResource(R.string.enabled_cheats)
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val showEnabledCheatsButton by remember(currentBackStackEntry) {
        derivedStateOf {
            val currentRoute = currentBackStackEntry?.destination?.route?.substringBefore("/")
            currentRoute == CheatsNavigation.GameFolders.serializer().descriptor.serialName || currentRoute == CheatsNavigation.FolderCheats.serializer().descriptor.serialName
        }
    }
    val appBarTitle by remember(currentBackStackEntry) {
        derivedStateOf {
            val currentRoute = currentBackStackEntry?.destination?.route?.substringBefore("/")
            when (currentRoute) {
                CheatsNavigation.GameFolders.serializer().descriptor.serialName -> currentBackStackEntry?.toRoute<CheatsNavigation.GameFolders>()?.gameName
                CheatsNavigation.FolderCheats.serializer().descriptor.serialName -> currentBackStackEntry?.toRoute<CheatsNavigation.FolderCheats>()?.folderName
                CheatsNavigation.EnabledCheats.serializer().descriptor.serialName -> enabledCheatsTitle
                else -> null
            }
        }
    }
    val systemUiController = rememberSystemUiController()
    val navigateBack = {
        if (navController.previousBackStackEntry == null) {
            // The first screen (the loading screen) is not considered as an entry. So, previousBackStackEntry will be null
            viewModel.commitCheatChanges()
        } else {
            navController.navigateUp()
        }
    }

    systemUiController.setStatusBarColor(MaterialTheme.colors.primaryVariant)

    BackHandler {
        navigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appBarTitle ?: stringResource(R.string.cheats),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                backgroundColor = MaterialTheme.colors.primary,
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(
                            painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
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
                },
                windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets(bottom = Int.MAX_VALUE)),
            )
        },
        backgroundColor = MaterialTheme.colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LaunchedEffect(Unit) {
            viewModel.openGamesEvent.collect {
                navController.navigate(CheatsNavigation.GameList) {
                    popUpTo<CheatsNavigation.Loading> {
                        inclusive = true
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            viewModel.openFoldersEvent.collect {
                navController.navigate(CheatsNavigation.GameFolders(it.newTitle)) {
                    popUpTo<CheatsNavigation.Loading> {
                        inclusive = true
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            viewModel.openCheatsEvent.collect {
                navController.navigate(CheatsNavigation.FolderCheats(it.newTitle)) {
                    popUpTo<CheatsNavigation.Loading> {
                        inclusive = true
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            viewModel.openEnabledCheatsEvent.collect {
                navController.navigate(CheatsNavigation.EnabledCheats) {
                    popUpTo<CheatsNavigation.Loading> {
                        inclusive = true
                    }
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = CheatsNavigation.Loading,
            enterTransition = { slideInHorizontally { it } },
            exitTransition = { slideOutHorizontally { -it } },
            popEnterTransition = { slideInHorizontally { -it } },
            popExitTransition = { slideOutHorizontally { it } },
        ) {
            composable<CheatsNavigation.Loading>(
                exitTransition = { ExitTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                LoadingScreen(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
            }
            composable<CheatsNavigation.GameList> {
                val games by viewModel.games.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                GameListScreen(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    contentPadding = padding,
                    games = games,
                    onGameClick = { viewModel.setSelectedGame(it) },
                )
            }
            composable<CheatsNavigation.GameFolders> {
                val folders by viewModel.folders.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                FolderListScreen(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    contentPadding = padding,
                    folders = folders,
                    onFolderClick = { viewModel.setSelectedFolder(it) },
                )
            }
            composable<CheatsNavigation.FolderCheats> {
                val cheats by viewModel.folderCheats.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                CheatListScreen(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    contentPadding = padding,
                    cheats = cheats,
                    onCheatClick = { viewModel.toggleCheat(it) },
                )
            }
            composable<CheatsNavigation.EnabledCheats> {
                val cheats by viewModel.selectedGameCheats.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())

                EnabledCheatsListScreen(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    contentPadding = padding,
                    cheats = cheats,
                    onCheatClick = { viewModel.toggleCheat(it.cheat) }
                )
            }
        }
    }
}