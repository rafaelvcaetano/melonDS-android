package me.magnum.melonds.ui.cheats.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import me.magnum.melonds.R
import me.magnum.melonds.common.cheats.ModifierFamily
import me.magnum.melonds.common.cheats.ModifierKind
import me.magnum.melonds.common.cheats.ModifierOption
import me.magnum.melonds.common.cheats.ModifierSource
import me.magnum.melonds.common.cheats.PokemonSpecies
import me.magnum.melonds.ui.common.melonButtonColors
import me.magnum.melonds.ui.common.melonOutlinedTextFieldColors
import me.magnum.melonds.ui.common.melonTextButtonColors

/**
 * Picker for a modifier family: a searchable list of options (species, natures, levels, anything enumerated)
 * and, when the family carries a level parameter, a numeric level field. [onConfirm] receives the chosen
 * option value and the level (null when the family has no level parameter).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModifierFamilyDialog(family: ModifierFamily, onDismiss: () -> Unit, onDisable: () -> Unit, onConfirm: (value: Int, level: Int?) -> Unit, initialOptionValue: Int? = null) {
    val key = "${family.folderName}/${family.title}"
    val levelOnly = family.source == ModifierSource.ITEM_LOAD && family.kind == ModifierKind.LEVEL
    val showsLevelField = family.requiresLevel || levelOnly
    val initialOption = family.options.firstOrNull { it.value == initialOptionValue }
    var value by rememberSaveable(key, initialOptionValue) { mutableStateOf(initialOption?.value ?: family.current?.value) }
    var level by rememberSaveable(key, initialOptionValue) { mutableStateOf((if (levelOnly) initialOption?.value ?: family.current?.value else family.currentLevel)?.toString() ?: "5") }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(key, initialOptionValue) { mutableStateOf(initialOption?.label.orEmpty()) }
    val matches = remember(query, family) { search(family.options, query) }
    val selected = family.options.firstOrNull { it.value == value }
    val levelValue = level.toIntOrNull()?.takeIf { it in 1..100 }
    val canConfirm = (levelOnly || selected != null) && (!showsLevelField || levelValue != null)
    val chooseLabel = when (family.kind) {
        ModifierKind.SPECIES -> if (family.role.startsWith("STARTER")) R.string.modifier_family_choose_starter else R.string.wild_encounter_choose
        ModifierKind.LEVEL -> R.string.modifier_family_choose_level
        ModifierKind.NATURE -> R.string.modifier_family_choose_nature
        ModifierKind.GENERIC -> R.string.modifier_family_choose_option
    }
    val searchLabel = when (family.kind) {
        ModifierKind.SPECIES -> R.string.wild_encounter_search
        ModifierKind.LEVEL -> R.string.modifier_family_search_level
        ModifierKind.NATURE -> R.string.modifier_family_search_nature
        ModifierKind.GENERIC -> R.string.modifier_family_search_options
    }
    val noResultsLabel = when (family.kind) {
        ModifierKind.SPECIES -> R.string.wild_encounter_no_results
        ModifierKind.LEVEL -> R.string.modifier_family_no_levels
        ModifierKind.NATURE -> R.string.modifier_family_no_natures
        ModifierKind.GENERIC -> R.string.modifier_family_no_options
    }
    val help = family.instructions ?: stringResource(
        if (family.nativeSelector && family.kind == ModifierKind.SPECIES && family.role == "WILD")
            R.string.wild_encounter_help else R.string.modifier_family_help
    )

    if (showHelp) {
        AlertDialog(onDismissRequest = { showHelp = false }, title = { Text(family.title) },
            text = { Text(help) }, confirmButton = {
                TextButton(onClick = { showHelp = false }, colors = melonTextButtonColors()) { Text(stringResource(android.R.string.ok)) }
            })
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        val isLight = MaterialTheme.colors.isLight
        DisposableEffect(window, view, isLight) {
            val controller = WindowCompat.getInsetsController(window, view)
            val originalStatusBarAppearance = controller.isAppearanceLightStatusBars
            val originalNavigationBarAppearance = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
            onDispose {
                controller.isAppearanceLightStatusBars = originalStatusBarAppearance
                controller.isAppearanceLightNavigationBars = originalNavigationBarAppearance
            }
        }
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val keyboardActions = KeyboardActions(onDone = {
            keyboardController?.hide()
            focusManager.clearFocus()
        })
        Surface(Modifier.fillMaxSize().semantics { paneTitle = family.title }, color = MaterialTheme.colors.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
                val compact = maxWidth < 600.dp
                val limitedHeight = maxHeight < 160.dp
                val editingInShortWindow = limitedHeight && WindowInsets.isImeVisible
                Column(
                    Modifier.fillMaxSize().padding(vertical = if (limitedHeight) 0.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (limitedHeight) 0.dp else 8.dp),
                ) {
                    if (!editingInShortWindow) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (compact && !limitedHeight) {
                                Text(family.title, style = MaterialTheme.typography.h6, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                if (!compact && !limitedHeight) {
                                    Text(family.title, Modifier.weight(1f), style = MaterialTheme.typography.h6, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onDismiss, colors = melonTextButtonColors()) { Text(stringResource(android.R.string.cancel)) }
                                Button(modifier = Modifier.heightIn(min = 48.dp), enabled = canConfirm, colors = melonButtonColors(), onClick = {
                                    if (levelOnly) onConfirm(levelValue ?: return@Button, null)
                                    else onConfirm(selected?.value ?: return@Button, if (family.requiresLevel) levelValue else null)
                                }) { Text(stringResource(R.string.wild_encounter_enable)) }
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().selectableGroup()) {
                        item(key = "context") {
                            ModifierFamilyContext(family, compact, onDisable, onHelp = { showHelp = true })
                            Spacer(Modifier.height(8.dp))
                        }
                        item(key = "fields") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (!levelOnly) {
                                    OutlinedTextField(
                                        modifier = Modifier.weight(1f), value = query, onValueChange = { query = it },
                                        label = { Text(stringResource(searchLabel)) }, singleLine = true,
                                        colors = melonOutlinedTextFieldColors(),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = keyboardActions,
                                    )
                                }
                                if (showsLevelField) {
                                    OutlinedTextField(
                                        modifier = if (levelOnly) Modifier.weight(1f) else Modifier.width(124.dp),
                                        value = level,
                                        onValueChange = { new -> if (new.length <= 3 && new.all { it in '0'..'9' }) level = new },
                                        label = { Text(stringResource(R.string.wild_encounter_level)) },
                                        isError = levelValue == null,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        keyboardActions = keyboardActions,
                                        colors = melonOutlinedTextFieldColors(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (!levelOnly) {
                            item(key = "selection") {
                                Text(selected?.label ?: stringResource(chooseLabel))
                                if (family.kind == ModifierKind.SPECIES && value == 201) Text(stringResource(R.string.wild_encounter_unown), style = MaterialTheme.typography.caption)
                                if (matches.isEmpty()) Text(stringResource(noResultsLabel))
                                Spacer(Modifier.height(8.dp))
                            }
                            items(matches, key = { it.value }) { option ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(selected = value == option.value, role = Role.RadioButton, onClick = { value = option.value })
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(modifier = Modifier.size(48.dp), selected = value == option.value, onClick = null)
                                    Text(option.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModifierFamilyContext(family: ModifierFamily, compact: Boolean, onDisable: () -> Unit, onHelp: () -> Unit) {
    val actions: @Composable () -> Unit = {
        if (family.active) {
            TextButton(onClick = onDisable, colors = melonTextButtonColors()) { Text(stringResource(R.string.modifier_family_disable)) }
        }
        TextButton(onClick = onHelp, colors = melonTextButtonColors()) { Text(stringResource(R.string.wild_encounter_details)) }
    }
    if (compact) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(family.folderName, style = MaterialTheme.typography.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                actions()
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(family.folderName, Modifier.weight(1f), style = MaterialTheme.typography.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            actions()
        }
    }
}

/**
 * A number matches an option value ("#025" → Pikachu); when no value matches — level and nature families
 * carry raw code words as values — it falls back to a text match on the label, so "7" still finds "Level 7".
 */
private fun search(options: List<ModifierOption>, query: String): List<ModifierOption> {
    val text = query.trim()
    if (text.isEmpty()) return options
    val byValue = text.removePrefix("#").toIntOrNull()?.let { number -> options.filter { it.value == number } }
    if (!byValue.isNullOrEmpty()) return byValue
    val needle = PokemonSpecies.normalize(text)
    return options.filter { PokemonSpecies.normalize(it.label).contains(needle) }
}
