package me.magnum.melonds.ui.cheats

import android.view.WindowManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import me.magnum.melonds.common.cheats.ModifierFamilies
import me.magnum.melonds.common.cheats.ModifierFamily
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.ui.cheats.ui.ModifierFamilyDialog
import me.magnum.melonds.ui.theme.MelonTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w600dp-h800dp", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModifierFamilyDialogTest {
    @get:Rule val compose = createComposeRule()

    private val hgSpeciesAndLevel = "94000130 FCFF0000 6211186C 00000000 B211186C 00000000 0000DCF4 01ED0001 0000DCF8 00640002 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DA000000 0000DCF6 C0000000 00000027 D7000000 00032A48 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DB000000 0000DCFA C0000000 0000000B D8000000 00032A3C D2000000 00000000"
    private val itemLoad: ModifierFamily = ModifierFamilies.recognize("Wild Pokemon Modifier Codes",
        listOf(Cheat(1, 1, "Wild Pokemon and Level Modifier", "(Press L+R): hold L before the encounter.", hgSpeciesAndLevel, false))).single()
    private val hgV1 = "94000130 FCFF0000 6211186C 00000000 B211186C 00000000 0000DCF4 01ED0001 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DA000000 0000DCF6 C0000000 00000027 D7000000 00032A48 D2000000 00000000"
    private fun levelFamily(): ModifierFamily = ModifierFamilies.recognize("49 - Wild · level 1-100",
        (1..10).map { Cheat(100L + it, 1, "Level $it", null, "52246C94 28038800 12247BEC %08X D2000000 00000000".format(0x2000 + it), false) }).single()
    private val names = listOf("Bulbasaur", "Ivysaur", "Venusaur", "Charmander", "Charmeleon")
    private fun enumerated(activeIndex: Int? = null): ModifierFamily = ModifierFamilies.recognize("Wild Pokemon Modifier - Generation 1",
        names.mapIndexed { i, n -> Cheat(10L + i, 1, n, null,
            "94000130 FFFB0000 6214617C 00000000 B214617C 00000000 C0000000 0000002F 00005DD4 %08X DC000000 00000004 D2000000 00000000".format(i + 1), i == activeIndex) }).single()

    @Test fun searchSelectValidateAndConfirm() {
        var result: Pair<Int, Int?>? = null
        compose.setContent { MelonTheme { ModifierFamilyDialog(itemLoad, {}, {}, { v, l -> result = v to l }) } }
        compose.onNodeWithText("Search name or Pokédex number").performTextInput("493")
        compose.onNodeWithText("#493 Arceus").performClick()
        compose.onNodeWithText("Level 1–100").performTextReplacement("101")
        compose.onNodeWithText("Enable").assertIsNotEnabled()
        compose.onNodeWithText("Level 1–100").performTextReplacement("100")
        compose.onNodeWithText("Enable").performClick()
        assertEquals(493 to 100, result)
    }

    @Test fun enumeratedFamilyHasNoLevelFieldAndConfirmsTheValue() {
        var result: Pair<Int, Int?>? = null
        compose.setContent { MelonTheme { ModifierFamilyDialog(enumerated(), {}, {}, { v, l -> result = v to l }) } }
        compose.onNodeWithText("Level 1–100").assertDoesNotExist()
        compose.onNodeWithText("Disable").assertDoesNotExist()
        compose.onNodeWithText("Search name or Pokédex number").performTextInput("charm")
        compose.onNodeWithText("Charmeleon").assertExists()
        compose.onNodeWithText("Charmander").performClick()
        compose.onNodeWithText("Enable").performClick()
        assertEquals(4 to null, result)
    }

    @Test fun optionRowIsOneLabelledRadioActionAndSelectionDoesNotConfirm() {
        var result: Pair<Int, Int?>? = null
        compose.setContent { MelonTheme { ModifierFamilyDialog(enumerated(), {}, {}, { v, l -> result = v to l }) } }
        val option = compose.onNode(hasText("Charmander") and hasClickAction())
        option.assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHeightIsAtLeast(48.dp)
        option.onChildren().filter(hasClickAction()).assertCountEquals(0)
        option.performClick().assertIsSelected()
        assertNull(result)
        compose.onNodeWithText("Enable").performClick()
        assertEquals(4 to null, result)
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-rIT-w320dp-h560dp")
    fun compactPortraitWithLongTitleAndLargeTextKeepsThePickerUsable() {
        // The 640dp Android capture had about 560dp available after system bars.
        assertCompactPickerUsable()
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-rIT-w320dp-h360dp")
    fun shortViewportWithLargeTextKeepsOptionsAndActionsReachable() {
        // A constrained window exercises scrolling; this does not simulate a real IME.
        assertCompactPickerUsable()
    }

    private fun assertCompactPickerUsable() {
        val previousScale = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(2f)
        try {
            val title = "Wild Pokemon Modifier - Generation 1"
            val family = enumerated().copy(title = title)
            var result: Pair<Int, Int?>? = null
            compose.setContent {
                MelonTheme { ModifierFamilyDialog(family, {}, {}, { v, l -> result = v to l }, initialOptionValue = 4) }
            }
            // A parent LocalDensity override does not survive the dialog's Android Owner.
            val titleLayout = mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText(title, useUnmergedTree = true).onFirst()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayout) }
            assertEquals(2f, titleLayout.single().layoutInput.density.fontScale, 0f)
            val captionLayout = mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText(title, useUnmergedTree = true).onLast()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(captionLayout) }
            assertTrue("Family name is squeezed into ${captionLayout.single().lineCount} lines",
                captionLayout.single().lineCount <= 2)
            val dialog = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
            assertEquals(320f, (dialog.right - dialog.left).value, 0.5f)
            fun assertFullyWithinDialog(node: SemanticsNodeInteraction) {
                node.assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue("Control is clipped: $bounds, dialog: $dialog",
                    bounds.left >= dialog.left && bounds.top >= dialog.top &&
                        bounds.right <= dialog.right && bounds.bottom <= dialog.bottom)
            }
            assertFullyWithinDialog(compose.onNodeWithText("Annulla"))
            assertFullyWithinDialog(compose.onNodeWithText("Attiva").assertIsEnabled())
            val content = compose.onNode(hasScrollToIndexAction())
            content.performScrollToNode(hasText("Come funziona"))
            assertFullyWithinDialog(compose.onNodeWithText("Come funziona"))
            content.performScrollToNode(hasSetTextAction())
            assertFullyWithinDialog(compose.onNode(hasSetTextAction() and hasText("Charmander")))
            content.performScrollToNode(hasText("Charmander") and isSelectable())
            val option = compose.onNode(hasText("Charmander") and hasClickAction() and !hasSetTextAction())
            assertFullyWithinDialog(option.assertHeightIsAtLeast(48.dp))
            assertFullyWithinDialog(compose.onNodeWithText("Annulla"))
            assertFullyWithinDialog(compose.onNodeWithText("Attiva"))
            assertNull(result)
            compose.onNodeWithText("Attiva").performClick()
            assertEquals(4 to null, result)
        } finally {
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-rIT-w640dp-h320dp-land")
    fun dialogLeavesImeInsetsToComposeInsteadOfPlatformPanning() {
        compose.setContent { MelonTheme { ModifierFamilyDialog(enumerated(), {}, {}, { _, _ -> }) } }
        compose.runOnIdle {
            val window = checkNotNull(ShadowDialog.getLatestDialog().window)
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-rIT-w640dp-h320dp-land")
    fun shortImeViewportKeepsEditingAndDoneRestoresActionsWithoutConfirming() {
        val previousScale = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(2f)
        try {
            var result: Pair<Int, Int?>? = null
            compose.setContent { MelonTheme {
                ModifierFamilyDialog(enumerated(), {}, {}, { v, l -> result = v to l }, initialOptionValue = 4)
            } }
            val input = compose.onNode(hasSetTextAction())
            val textLayouts = mutableListOf<TextLayoutResult>()
            input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            assertTrue("Input must render at the actual system font scale", textLayouts.isNotEmpty() &&
                textLayouts.all { it.layoutInput.density.fontScale == 2f })
            input.performClick().assertIsFocused()
            // A real WindowInsets dispatch leaves 64dp: 320 - 24(system bar) - 232(IME).
            dispatchImeInsets(bottomDp = 232, visible = true)
            compose.onNode(hasScrollToIndexAction()).assertHeightIsEqualTo(64.dp)
            input.assertIsDisplayed()
            val visibleInput = input.fetchSemanticsNode().boundsInRoot
            val density = ShadowDialog.getLatestDialog().context.resources.displayMetrics.density
            assertTrue("Editable field has no usable visible line: $visibleInput", visibleInput.height / density >= 32f)
            input.performTextReplacement("Charm")
            input.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Done))
            compose.onNodeWithText("Attiva").assertDoesNotExist()
            input.performImeAction()
            input.assertIsNotFocused()
            assertNull(result)
            // The platform reports the keyboard closing after Done; this is not an IME emulator.
            dispatchImeInsets(bottomDp = 0, visible = false)
            compose.onNodeWithText("Annulla").assertIsDisplayed()
            compose.onNodeWithText("Attiva").assertIsDisplayed().assertIsEnabled()
            compose.onNode(hasSetTextAction() and hasText("Charm")).assertExists()
            assertNull(result)
        } finally {
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "it-rIT-w640dp-h64dp-land")
    fun shortWindowWithoutImeKeepsCancelAndConfirmReachable() {
        val previousScale = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(2f)
        try {
            compose.setContent { MelonTheme {
                ModifierFamilyDialog(enumerated(), {}, {}, { _, _ -> }, initialOptionValue = 4)
            } }
            val dialog = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
            for (label in listOf("Annulla", "Attiva")) {
                val action = compose.onNodeWithText(label).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                val bounds = action.getUnclippedBoundsInRoot()
                assertTrue("$label is clipped: $bounds in $dialog", bounds.top >= dialog.top && bounds.bottom <= dialog.bottom)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previousScale)
        }
    }

    private fun dispatchImeInsets(bottomDp: Int, visible: Boolean) {
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()
            val density = dialog.context.resources.displayMetrics.density
            val bars = Insets.of(0, (24 * density).toInt(), 0, 0)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars(), bars)
                .setVisible(WindowInsetsCompat.Type.systemBars(), true)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (bottomDp * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), visible)
                .build()
            ViewCompat.dispatchApplyWindowInsets(checkNotNull(dialog.window).decorView, insets)
        }
        compose.waitForIdle()
    }

    @Test fun disableIsOfferedOnlyWhenSomethingIsActive() {
        var disabled = false
        compose.setContent { MelonTheme { ModifierFamilyDialog(enumerated(activeIndex = 2), {}, { disabled = true }, { _, _ -> }) } }
        compose.onAllNodesWithText("Venusaur").assertCountEquals(2) // selected label + list row
        compose.onNodeWithText("Disable").performClick()
        assertTrue(disabled)
    }

    @Test fun cancelDoesNotSave() {
        var cancelled = false
        var saved = false
        compose.setContent { MelonTheme { ModifierFamilyDialog(itemLoad, { cancelled = true }, {}, { _, _ -> saved = true }) } }
        compose.onNodeWithText("Search name or Pokédex number").performTextInput("not a pokemon")
        compose.onNodeWithText("No Pokémon found").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
        assertFalse(saved)
    }

    @Test
    @Config(qualifiers = "it-rIT-w640dp-h360dp")
    fun landscapeKeepsSearchResultsAndActionsVisible() {
        compose.setContent { MelonTheme { ModifierFamilyDialog(itemLoad, {}, {}, { _, _ -> }) } }
        compose.onNodeWithText("Cerca nome o numero Pokédex").performTextInput("pikachu")
        compose.onNodeWithText("#025 Pikachu").assertIsDisplayed().performClick()
        compose.onNodeWithText("Attiva").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun numericSearchFallsBackToTheLabelForLevelFamilies() {
        var result: Pair<Int, Int?>? = null
        compose.setContent { MelonTheme { ModifierFamilyDialog(levelFamily(), {}, {}, { v, l -> result = v to l }) } }
        compose.onNodeWithText("Level 1–100").assertDoesNotExist()
        compose.onNodeWithText("Search name or Pokédex number").performTextInput("7")
        compose.onNodeWithText("Level 7").performClick()
        compose.onNodeWithText("Enable").performClick()
        assertEquals(0x2007 to null, result)
    }

    @Test fun loadFormActiveFamilyOffersDisable() {
        var disabled = false
        val active = ModifierFamilies.recognize("Wild Pokemon Modifier Codes", listOf(Cheat(1, 1, "Wild Pokemon Modifier v1", null, hgV1, true))).single()
        compose.setContent { MelonTheme { ModifierFamilyDialog(active, {}, { disabled = true }, { _, _ -> }) } }
        compose.onNodeWithText("Disable").performClick()
        assertTrue(disabled)
    }
}
