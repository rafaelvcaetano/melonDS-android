package me.magnum.melonds.ui.cheats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import me.magnum.melonds.common.cheats.WildEncounterCheat
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.ui.cheats.model.CheatsScreenUiState
import me.magnum.melonds.ui.cheats.ui.FolderListScreen
import me.magnum.melonds.ui.theme.MelonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w600dp-h800dp", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CheatSearchScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun plain(id: Long, name: String) = Cheat(id, 1, name, null, "not an AR code $id", false)
    private val folders = listOf(
        CheatFolder(1, "Inventory", listOf(plain(1, "Max money"))),
        CheatFolder(2, "Movement", listOf(plain(2, "Walk through walls"))),
    )

    private fun show(data: List<CheatFolder>) {
        compose.setContent { MelonTheme {
            FolderListScreen(Modifier.fillMaxSize(), PaddingValues(), CheatsScreenUiState.Ready(data), {}, {})
        } }
    }

    @Test fun filtersCheatNamesCaseInsensitivelyAndClearRestoresFolders() {
        show(folders)
        compose.onNodeWithText("Search cheats and folders").performTextInput("  MoNeY  ")
        compose.onNodeWithText("Max money").assertIsDisplayed()
        compose.onNodeWithText("Movement").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear").performClick()
        compose.onNodeWithText("Movement").assertIsDisplayed()
        compose.onNodeWithText("Max money").assertDoesNotExist()
    }

    @Test fun aSingleLineSearchResultHasAFullHeightTouchTarget() {
        show(folders)
        compose.onNodeWithText("Search cheats and folders").performTextInput("money")
        compose.onNode(hasText("Max money") and hasClickAction()).assertHeightIsAtLeast(48.dp)
    }

    @Test fun findsGeneratedSpeciesAbsentFromThePhysicalCheatName() {
        show(listOf(CheatFolder(9, "Wild", listOf(Cheat(90, 1, "Choose", null, WildEncounterCheat.code(1, 5), false)))))
        compose.onNodeWithText("Search cheats and folders").performTextInput("Pikachu")
        compose.onNodeWithText("#025 Pikachu").assertIsDisplayed()
    }

    @Test fun largeFolderComposesOnlyVisibleResultsAndCanScrollToTheLastMatch() {
        show(listOf(CheatFolder(1, "Many", (0..1199).map { plain(it.toLong(), "Match %04d".format(it)) })))
        compose.onNodeWithText("Search cheats and folders").performTextInput("Match")
        compose.onNodeWithText("Match 0000").assertIsDisplayed()
        compose.onNodeWithText("Match 1199").assertDoesNotExist()
        compose.onNodeWithTag("cheat-search-results").performScrollToNode(hasText("Match 1199"))
        compose.onNodeWithText("Match 1199").assertIsDisplayed()
    }

    @Test fun queryAndResultsSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MelonTheme {
            FolderListScreen(Modifier.fillMaxSize(), PaddingValues(), CheatsScreenUiState.Ready(folders), {}, {})
        } }
        compose.onNodeWithText("Search cheats and folders").performTextInput("walls")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Walk through walls").assertIsDisplayed()
        compose.onNodeWithText("Inventory").assertDoesNotExist()
    }

    @Test fun replacingFoldersRebuildsTheIndexAndRemovesStaleMatches() {
        val data = mutableStateOf(folders)
        compose.setContent { MelonTheme {
            FolderListScreen(Modifier.fillMaxSize(), PaddingValues(), CheatsScreenUiState.Ready(data.value), {}, {})
        } }
        compose.onNodeWithText("Search cheats and folders").performTextInput("money")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Max money").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { data.value = listOf(CheatFolder(3, "New folder", listOf(plain(3, "Double money")))) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Double money").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Max money").assertDoesNotExist()
        compose.onNodeWithText("Inventory").assertDoesNotExist()
        compose.onNodeWithText("New folder").assertIsDisplayed()
        compose.onNodeWithText("Search cheats and folders").performTextReplacement("no such cheat")
        compose.onNodeWithText("No matching cheats or folders").assertIsDisplayed()
    }

    @Test fun refreshingFoldersThroughLoadingPreservesTheQuery() {
        val data = mutableStateOf<CheatsScreenUiState<List<CheatFolder>>>(CheatsScreenUiState.Ready(folders))
        compose.setContent { MelonTheme {
            FolderListScreen(Modifier.fillMaxSize(), PaddingValues(), data.value, {}, {})
        } }
        compose.onNodeWithText("Search cheats and folders").performTextInput("money")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Max money").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { data.value = CheatsScreenUiState.Loading() }
        compose.onNodeWithText("Search cheats and folders").assertDoesNotExist()
        compose.runOnIdle { data.value = CheatsScreenUiState.Ready(folders) }
        compose.onNode(hasSetTextAction() and hasText("money")).assertExists()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Max money").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Movement").assertDoesNotExist()
    }
}
