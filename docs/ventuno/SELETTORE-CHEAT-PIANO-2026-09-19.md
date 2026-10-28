# Selettore cheat a famiglie — piano di implementazione

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sostituire il selettore SGP-only con un meccanismo che riconosce le "famiglie modificatore" nei cheat importati di qualsiasi gioco e offre un selettore, senza dati nuovi e senza C++.

**Architecture:** Logica pura in `common/cheats/` (`ArCode` parser → `ModifierFamilies` riconoscimento/applicazione), `CheatsViewModel` che delega e mantiene lo staging esistente, `CheatListScreen` con riga collassata e `ModifierFamilyDialog` (ex `WildEncounterDialog`). SGP passa dalle stesse regole; il profilo canonico conserva il toggle L+R nativo.

**Tech Stack:** Kotlin, Jetpack Compose (material), Room (invariato), Robolectric + compose-ui-test, JUnit4. JDK 21 (`export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`).

**Spec:** `docs/ventuno/SELETTORE-CHEAT-2026-09-19.md`

## Global Constraints

- Ramo `feat/modifier-families` da `ventuno`; **nessun file fuori da** `app/src/main`, `app/src/test`, `docs/ventuno`; niente script, niente `tools/`.
- Nessuna migrazione Room; `CheatEntity` e `Cheat` invariati.
- `cpp/WildEncounterToggle.h` e `cpp/MelonInstance.cpp` invariati.
- Parole chiave §3.1 della spec: `WILD = wild|encounter|selvatic|incontr`, `STARTER = starter|iniziale`, `LEVEL = level|livell`, `NATURE = natur` (case-insensitive, sottostringa).
- Soglie §3.2/§3.3: famiglia ≥ 5 membri, 1–2 word variabili, SPECIES ≥ 80 % nomi o range 1..649 + `WILD|STARTER`; load relativa `b < 0x02000000`; massimo 2 parametri per codice.
- Test: `sh ./gradlew :app:testGitHubNightlyDebugUnitTest` (tutti), o `--tests 'me.magnum.melonds.common.cheats.*'` per la logica pura.
- Commit piccoli, messaggi tecnici in inglese, senza firme automatiche; conservare i crediti dei contributori originali.
- Il codice riscritto in forma fissa cambia **solo** l'istruzione load; nessun altro byte.

---

### Task 1: `ArCode` — parser e renderer dei codici Action Replay

**Files:**
- Create: `app/src/main/java/me/magnum/melonds/common/cheats/ArCode.kt`
- Test: `app/src/test/java/me/magnum/melonds/common/cheats/ArCodeTest.kt`

**Interfaces:**
- Produces: `data class ArInstruction(val a: Long, val b: Long)`, `data class ArBlock(val instructions: List<ArInstruction>)`, `object ArCode { fun parse(code: String): List<ArBlock>?; fun render(blocks: List<ArBlock>): String; const val SET_DATA, LOAD_16, LOAD_8, STORE_16, STORE_8, LOOP, DATA_OP, OFFSET_SET, OFFSET_ADD, END, RAM_START }`

- [ ] **Step 1: Test che fallisce**

```kotlin
package me.magnum.melonds.common.cheats

import org.junit.Assert.*
import org.junit.Test

class ArCodeTest {
    private val hgV1 = "94000130 FCFF0000 6211186C 00000000 B211186C 00000000 0000DCF4 01ED0001 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DA000000 0000DCF6 C0000000 00000027 D7000000 00032A48 D2000000 00000000"

    @Test fun splitsBlocksOnTerminatorAndKeepsTail() {
        val blocks = requireNotNull(ArCode.parse(hgV1))
        assertEquals(2, blocks.size)
        assertEquals(5, blocks[0].instructions.size)
        assertEquals(ArInstruction(0xDA000000L, 0x0000DCF6L), blocks[1].instructions[3])
        val tail = requireNotNull(ArCode.parse("52246C94 28038800 12247BEC 00002001"))
        assertEquals(1, tail.size)
        assertEquals(2, tail[0].instructions.size)
    }

    @Test fun renderIsTheAppFormatAndRoundTrips() {
        assertEquals(hgV1, ArCode.render(requireNotNull(ArCode.parse(hgV1))))
        assertEquals("D2000000 00000000", ArCode.render(requireNotNull(ArCode.parse("d2000000\n00000000"))))
    }

    @Test fun rejectsMalformedCodes() {
        assertNull(ArCode.parse(""))
        assertNull(ArCode.parse("D2000000"))
        assertNull(ArCode.parse("D200000 00000000"))
        assertNull(ArCode.parse("D2000000 0000000G"))
    }
}
```

- [ ] **Step 2: Eseguire, deve fallire per classe mancante**

`sh ./gradlew :app:testGitHubNightlyDebugUnitTest --tests 'me.magnum.melonds.common.cheats.ArCodeTest'` → compilazione fallita: `Unresolved reference: ArCode`.

- [ ] **Step 3: Implementazione**

```kotlin
package me.magnum.melonds.common.cheats

import java.util.Locale

/** One Action Replay DS instruction: two 32-bit words. The opcode lives in the top byte of [a]. */
data class ArInstruction(val a: Long, val b: Long)

/** Instructions up to and including a `D2000000 00000000` terminator, or the tail of the code. */
data class ArBlock(val instructions: List<ArInstruction>)

/** The only place that knows what an Action Replay opcode means. Semantics from AREngine.cpp. */
object ArCode {
    const val SET_DATA = 0xD5000000L   // datareg = b
    const val LOAD_16 = 0xDA000000L    // datareg = u16[b + offset]
    const val LOAD_8 = 0xDB000000L     // datareg = u8[b + offset]
    const val STORE_16 = 0xD7000000L   // u16[b + offset] = datareg
    const val STORE_8 = 0xD8000000L    // u8[b + offset] = datareg
    const val LOOP = 0xC0000000L
    const val DATA_OP = 0xD4000000L
    const val OFFSET_SET = 0xD3000000L
    const val OFFSET_ADD = 0xDC000000L
    const val END = 0xD2000000L
    /** Addresses below this are offsets relative to a pointer loaded with B2xxxxxx, not main RAM. */
    const val RAM_START = 0x02000000L

    fun parse(code: String): List<ArBlock>? {
        val words = code.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size % 2 != 0) return null
        val values = LongArray(words.size)
        for (i in words.indices) {
            val word = words[i]
            if (word.length != 8) return null
            values[i] = word.toLongOrNull(16) ?: return null
        }
        val blocks = mutableListOf<ArBlock>()
        var current = mutableListOf<ArInstruction>()
        for (i in values.indices step 2) {
            val instruction = ArInstruction(values[i], values[i + 1])
            current.add(instruction)
            if (instruction.a == END && instruction.b == 0L) {
                blocks.add(ArBlock(current))
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) blocks.add(ArBlock(current))
        return blocks
    }

    fun render(blocks: List<ArBlock>): String =
        blocks.flatMap { it.instructions }.joinToString(" ") { "%08X %08X".format(Locale.ROOT, it.a, it.b) }
}
```

- [ ] **Step 4: Eseguire, deve passare** (stesso comando).

- [ ] **Step 5: Commit** — `feat(cheats): Action Replay code parser and renderer`

---

### Task 2: Modello `ModifierFamily` e riconoscimento delle famiglie enumerate (gamba A)

**Files:**
- Create: `app/src/main/java/me/magnum/melonds/common/cheats/ModifierFamily.kt`
- Modify: `app/src/main/java/me/magnum/melonds/common/cheats/PokemonSpecies.kt` (normalizzazione pubblica)
- Test: `app/src/test/java/me/magnum/melonds/common/cheats/ModifierFamilyTest.kt`

**Interfaces:**
- Consumes: `ArCode.parse`, `PokemonSpecies.all`
- Produces: `enum ModifierKind { SPECIES, LEVEL, NATURE, GENERIC }`, `enum ModifierSource { ENUMERATED, ITEM_LOAD }`, `data class ModifierOption(value: Int, label: String, cheat: Cheat?)`, `data class ModifierParameter(blockIndex: Int, instructionIndex: Int, width: Int, kind: ModifierKind, current: Int?)`, `data class ModifierFamily(title, kind, source, folderName, role, members, options, parameters, instructions)` con `current`, `currentLevel`, `hasLevelParameter`, `exclusionGroups`, `conflictsWith(other)`, `contains(cheat)`, `sameAs(other)`; `object ModifierFamilies { fun recognize(folderName: String, cheats: List<Cheat>): List<ModifierFamily>; fun recognize(folder: CheatFolder) }`; `PokemonSpecies.normalize(name): String`, `PokemonSpecies.normalizedNames: Set<String>`.

- [ ] **Step 1: Test che fallisce** (codici reali di Black USA, HG USA, SGP)

```kotlin
package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.Cheat
import org.junit.Assert.*
import org.junit.Test

class ModifierFamilyTest {
    private var nextId = 1L
    private fun cheat(name: String, code: String, enabled: Boolean = false, description: String? = null) =
        Cheat(nextId++, 1, name, description, code, enabled)

    // Black USA, "Wild Pokemon Modifier - Generation 1": one word varies (the species)
    private fun blackWild(number: Int, name: String) = cheat(name,
        "94000130 FFFB0000 6214617C 00000000 B214617C 00000000 C0000000 0000002F 00005DD4 %08X DC000000 00000004 D2000000 00000000".format(number),
        description = "(Press and hold Select before encountering)")
    // SGP 44 "Wild · nature": two correlated words vary (0x2400+n, 0x2700+n)
    private fun sgpNature(n: Int, name: String) = cheat(name,
        "5206E108 B086B5F8 5206E14C B089B5F0 1206E110 00009C0C 1206E11A 00001C05 1206E15C 00009E0E 1206E15E 00009F0F 1206E110 %08X 1206E15E %08X D2000000 00000000".format(0x2400 + n, 0x2700 + n))
    // SGP 49 / HG "Level N"
    private fun hgLevel(level: Int) = cheat("Level $level", "52246C94 28038800 12247BEC %08X D2000000 00000000".format(0x2000 + level))

    @Test fun enumeratedSpeciesFamilyFromNamesAndSingleVaryingWord() {
        val cheats = listOf(151 to "Mew", 1 to "Bulbasaur", 2 to "Ivysaur", 3 to "Venusaur", 25 to "Pikachu", 4 to "Charmander").map { blackWild(it.first, it.second) }
        val family = ModifierFamilies.recognize("Wild Pokemon Modifier - Generation 1", cheats).single()
        assertEquals(ModifierKind.SPECIES, family.kind)
        assertEquals(ModifierSource.ENUMERATED, family.source)
        assertEquals("WILD", family.role)
        assertEquals(listOf(151, 1, 2, 3, 25, 4), family.options.map { it.value })
        assertEquals("Pikachu", family.options[4].label)
        assertSame(cheats[4], family.options[4].cheat)
        assertNull(family.current)
        assertEquals("(Press and hold Select before encountering)", family.instructions)
    }

    @Test fun mixedFolderGroupsByLengthAndLeavesTheOddOneOut() {
        val readme = cheat("LEGGIMI", "D2000000 00000000")
        val members = listOf(1, 5, 10, 20, 30, 40).map(::hgLevel)
        val families = ModifierFamilies.recognize("49 - Selvatici · livello 1-100", listOf(readme) + members)
        val family = families.single()
        assertEquals(ModifierKind.LEVEL, family.kind)
        assertEquals(6, family.members.size)
        assertFalse(family.contains(readme))
    }

    @Test fun natureFamilyAllowsTwoCorrelatedVaryingWords() {
        val cheats = listOf("Hardy (=)", "Lonely (+A -D)", "Brave (+A -S)", "Adamant (+A -SA)", "Naughty (+A -SD)").mapIndexed { i, n -> sgpNature(i, n) }
        val family = ModifierFamilies.recognize("44 - Wild · nature", cheats).single()
        assertEquals(ModifierKind.NATURE, family.kind)
        assertEquals(listOf(0x2400, 0x2401, 0x2402, 0x2403, 0x2404), family.options.map { it.value })
    }

    @Test fun speciesByRangeWhenNamesAreUnknownGenFive() {
        val cheats = (494..499).map { blackWild(it, "Gen5 #$it") }
        val family = ModifierFamilies.recognize("Wild Pokemon Modifier - Generation 5", cheats).single()
        assertEquals(ModifierKind.SPECIES, family.kind)
    }

    @Test fun rejectsSmallGroupsDuplicatesAndTooManyVaryingWords() {
        assertTrue(ModifierFamilies.recognize("Wild", (1..4).map { blackWild(it, "x$it") }).isEmpty())
        val dup = (1..5).map { blackWild(7, "same") }
        assertTrue(ModifierFamilies.recognize("Wild", dup).isEmpty())
        val threeVary = (1..5).map { cheat("v$it", "0200%04X 0000%04X 0201%04X 00000000".format(it, it, it)) }
        assertTrue(ModifierFamilies.recognize("Wild", threeVary).isEmpty())
    }

    @Test fun rolesAndExclusionGroups() {
        val wild = ModifierFamilies.recognize("Wild Pokemon Modifier - Generation 1", (1..5).map { blackWild(it, "w$it") }).single()
        val starter1 = ModifierFamilies.recognize("Starter #1 Modifier - Generation 1", (1..5).map { blackWild(it, "s$it") }).single()
        val starter2 = ModifierFamilies.recognize("Starter #2 Modifier - Generation 1", (1..5).map { blackWild(it, "s$it") }).single()
        val playAs = ModifierFamilies.recognize("Play As Pokemon - Generation 1", (1..5).map { blackWild(it, "p$it") }).single()
        assertEquals("STARTER#1", starter1.role)
        assertFalse(wild.conflictsWith(starter1))
        assertFalse(starter1.conflictsWith(starter2))
        assertFalse(wild.conflictsWith(playAs))
        assertTrue(wild.conflictsWith(wild.copy(title = "Wild Pokemon Modifier - Generation 2")))
    }

    @Test fun speciesNormalizationIgnoresParenthesesAndNumbers() {
        assertEquals("meganium", PokemonSpecies.normalize("Meganium (Male)"))
        assertEquals("bulbasaur", PokemonSpecies.normalize("#001 Bulbasaur"))
        assertEquals("mrmime", PokemonSpecies.normalize("Mr. Mime"))
        assertTrue("nidoran" in PokemonSpecies.normalize("Nidoran♀"))
    }
}
```

- [ ] **Step 2: Eseguire, deve fallire** (`Unresolved reference: ModifierFamilies`).

- [ ] **Step 3: `PokemonSpecies.kt` — normalizzazione pubblica**

Sostituire il `normalize` privato con:

```kotlin
        /** Case-, accent-, punctuation-insensitive key; parenthesised suffixes and "#001" prefixes are ignored. */
        fun normalize(value: String): String = Normalizer.normalize(value.replace(Regex("\\(.*?\\)|#\\d+"), ""), Normalizer.Form.NFD)
            .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        val normalizedNames: Set<String> by lazy { all.map { normalize(it.name) }.toSet() }
```

(`search()` continua a usare `normalize`.)

- [ ] **Step 4: `ModifierFamily.kt` — modello + gamba A**

```kotlin
package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder

enum class ModifierKind { SPECIES, LEVEL, NATURE, GENERIC }
enum class ModifierSource { ENUMERATED, ITEM_LOAD }

data class ModifierOption(val value: Int, val label: String, val cheat: Cheat?)

/** One rewritable load inside an ITEM_LOAD code. [current] is the value when the block is already in fixed form. */
data class ModifierParameter(val blockIndex: Int, val instructionIndex: Int, val width: Int, val kind: ModifierKind, val current: Int?)

/**
 * A group of cheats that differ only by a value (ENUMERATED) or a single cheat whose value is read from
 * an item count or the Pokétch calculator (ITEM_LOAD). Computed from the folder contents, never persisted.
 */
data class ModifierFamily(
    val title: String,
    val kind: ModifierKind,
    val source: ModifierSource,
    val folderName: String,
    val role: String,
    val members: List<Cheat>,
    val options: List<ModifierOption>,
    val parameters: List<ModifierParameter>,
    val instructions: String?,
) {
    val current: ModifierOption?
        get() = when (source) {
            ModifierSource.ENUMERATED -> options.firstOrNull { it.cheat?.enabled == true }
            ModifierSource.ITEM_LOAD -> members.single().takeIf { it.enabled }
                ?.let { parameters.first().current }
                ?.let { value -> options.firstOrNull { it.value == value } }
        }
    val currentLevel: Int? get() = parameters.getOrNull(1)?.current
    val hasLevelParameter: Boolean get() = parameters.size == 2 && parameters[1].kind == ModifierKind.LEVEL

    /** Families sharing a group must not be active at the same time. NATURE ignores the role. */
    val exclusionGroups: Set<Pair<ModifierKind, String>>
        get() = when {
            source == ModifierSource.ITEM_LOAD -> parameters.map { it.kind to roleFor(it.kind) }.toSet()
            kind == ModifierKind.GENERIC -> emptySet()
            else -> setOf(kind to roleFor(kind))
        }

    private fun roleFor(kind: ModifierKind) = if (kind == ModifierKind.NATURE) "" else role
    fun conflictsWith(other: ModifierFamily) = exclusionGroups.any { it in other.exclusionGroups }
    fun contains(cheat: Cheat) = members.any { it.id == cheat.id }
    fun sameAs(other: ModifierFamily) = title == other.title && folderName == other.folderName && source == other.source
}

object ModifierFamilies {
    private val WILD = Regex("wild|encounter|selvatic|incontr", RegexOption.IGNORE_CASE)
    private val STARTER = Regex("starter|iniziale", RegexOption.IGNORE_CASE)
    private val LEVEL = Regex("level|livell", RegexOption.IGNORE_CASE)
    private val NATURE = Regex("natur", RegexOption.IGNORE_CASE)
    private val SLOT = Regex("#\\s*(\\d+)")
    private const val MIN_MEMBERS = 5
    private const val MAX_VARYING = 2
    private const val MAX_SPECIES = 649L
    private const val SPECIES_NAME_RATIO = 0.8

    fun recognize(folder: CheatFolder): List<ModifierFamily> = recognize(folder.name, folder.cheats)

    fun recognize(folderName: String, cheats: List<Cheat>): List<ModifierFamily> {
        val parsed = cheats.mapNotNull { cheat -> ArCode.parse(cheat.code)?.let { cheat to it } }
        val enumerated = parsed
            .groupBy { (_, blocks) -> blocks.sumOf { it.instructions.size } }
            .values.filter { it.size >= MIN_MEMBERS }
            .mapNotNull { group -> enumeratedFamily(folderName, group) }
        val taken = enumerated.flatMap { family -> family.members.map { it.id } }.toSet()
        val loads = parsed
            .filter { (cheat, _) -> cheat.id !in taken && (WILD.containsMatchIn(folderName) || WILD.containsMatchIn(cheat.name)) }
            .mapNotNull { (cheat, blocks) -> itemLoadFamily(folderName, cheat, blocks) }
        return enumerated + loads
    }

    private fun enumeratedFamily(folderName: String, group: List<Pair<Cheat, List<ArBlock>>>): ModifierFamily? {
        val words = group.map { (_, blocks) -> blocks.flatMap { it.instructions }.flatMap { listOf(it.a, it.b) } }
        val varying = words.first().indices.filter { i -> words.map { it[i] }.toSet().size > 1 }
        if (varying.isEmpty() || varying.size > MAX_VARYING) return null
        val tuples = words.map { w -> varying.map { w[it] } }
        if (tuples.toSet().size != tuples.size) return null
        val values = words.map { it[varying.first()] }
        if (values.any { it > Int.MAX_VALUE }) return null
        val names = group.map { (cheat, _) -> cheat.name }
        val speciesHits = names.count { PokemonSpecies.normalize(it) in PokemonSpecies.normalizedNames }
        val speciesRange = values.all { it in 1..MAX_SPECIES }
        val kind = when {
            varying.size == 1 && (speciesHits >= SPECIES_NAME_RATIO * names.size ||
                (speciesRange && (WILD.containsMatchIn(folderName) || STARTER.containsMatchIn(folderName)))) -> ModifierKind.SPECIES
            NATURE.containsMatchIn(folderName) -> ModifierKind.NATURE
            LEVEL.containsMatchIn(folderName) -> ModifierKind.LEVEL
            else -> ModifierKind.GENERIC
        }
        val members = group.map { it.first }
        return ModifierFamily(
            title = folderName, kind = kind, source = ModifierSource.ENUMERATED, folderName = folderName, role = roleOf(folderName),
            members = members,
            options = members.mapIndexed { i, cheat -> ModifierOption(values[i].toInt(), cheat.name, cheat) },
            parameters = emptyList(),
            instructions = members.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) },
        )
    }

    private fun itemLoadFamily(folderName: String, cheat: Cheat, blocks: List<ArBlock>): ModifierFamily? = null  // Task 3

    internal fun roleOf(title: String): String = when {
        STARTER.containsMatchIn(title) -> "STARTER" + (SLOT.find(title)?.groupValues?.get(1)?.let { "#$it" } ?: "")
        WILD.containsMatchIn(title) -> "WILD"
        else -> "OTHER"
    }
}
```

- [ ] **Step 5: Eseguire, deve passare.**

- [ ] **Step 6: Commit** — `feat(cheats): recognise enumerated modifier families in a cheat folder`

---

### Task 3: Riconoscimento dei codici a lettura da oggetto/calcolatrice (gamba B)

**Files:**
- Modify: `app/src/main/java/me/magnum/melonds/common/cheats/ModifierFamily.kt` (`itemLoadFamily`, `parameterIn`)
- Test: `app/src/test/java/me/magnum/melonds/common/cheats/ModifierFamilyTest.kt` (aggiunte)

**Interfaces:**
- Produces: famiglie `ITEM_LOAD` con `parameters` (1–2), `kind` del primo parametro, `options` da `PokemonSpecies.all` (SPECIES) o `1..100` (LEVEL).

- [ ] **Step 1: Test che falliscono** (codici reali; aggiungere alla classe di Task 2)

```kotlin
    private val hgV1 = "94000130 FCFF0000 6211186C 00000000 B211186C 00000000 0000DCF4 01ED0001 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DA000000 0000DCF6 C0000000 00000027 D7000000 00032A48 D2000000 00000000"
    private val hgSpeciesAndLevel = "94000130 FCFF0000 6211186C 00000000 B211186C 00000000 0000DCF4 01ED0001 0000DCF8 00640002 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DA000000 0000DCF6 C0000000 00000027 D7000000 00032A48 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DB000000 0000DCFA C0000000 0000000B D8000000 00032A3C D2000000 00000000"
    private val hgLevelOnly = "94000130 FCFF0000 6211186C 00000000 B211186C 00000000 0000DCF8 00640002 D2000000 00000000 " +
        "94000130 FDFF0000 6211186C 00000000 B211186C 00000000 DB000000 0000DCFA C0000000 0000000B D8000000 00032A3C D2000000 00000000"
    private val platinumCalculator = "94000130 FDFF0000 62101D2C 00000000 B2101D2C 00000000 DA000000 0011ECF0 C0000000 0000000B D7000000 000303CC DC000000 00000006 D2000000 00000000 " +
        "94000130 FEFF0000 62101D2C 00000000 B2101D2C 00000000 DA000000 0011ECF0 C0000000 0000000B D7000000 000303C8 DC000000 00000006 D2000000 00000000 " +
        "94000130 FFFB0000 2207404D 00000024 62101D2C 00000000 B2101D2C 00000000 DB000000 0011ECF0 D3000000 00000000 D8000000 0207404C D2000000 00000000"
    private val randomEncounterAbsolute = "923FFFFE 00000001 62250010 00000000 DA000000 02250010 D4000000 00000029 D7000000 02250010 D3000000 00000000 D2000000 00000000"
    private val maxIvs = "1206E012 0000201F 1206E028 0000201F 1206E03E 0000201F 1206E054 0000201F 1206E06A 0000201F 1206E080 0000201F"

    @Test fun itemLoadSpeciesFromHeartGoldMasterBallCode() {
        val cheat = cheat("Wild Pokemon Modifier v1", hgV1, description = "(Press L+R): You will get 493 Master Balls.")
        val family = ModifierFamilies.recognize("Wild Pokemon Modifier Codes", listOf(cheat)).single()
        assertEquals(ModifierSource.ITEM_LOAD, family.source)
        assertEquals(ModifierKind.SPECIES, family.kind)
        assertEquals(listOf(ModifierParameter(1, 3, 16, ModifierKind.SPECIES, null)), family.parameters)
        assertEquals(493, family.options.size)
        assertEquals("#025 Pikachu", family.options[24].label)
        assertFalse(family.hasLevelParameter)
        assertNull(family.current)
    }

    @Test fun secondBlockIsLevelAndEightBitLoadIsLevel() {
        val both = ModifierFamilies.recognize("Wild Pokemon Modifier Codes", listOf(cheat("Wild Pokemon and Level Modifier", hgSpeciesAndLevel))).single()
        assertEquals(listOf(ModifierKind.SPECIES, ModifierKind.LEVEL), both.parameters.map { it.kind })
        assertTrue(both.hasLevelParameter)
        val level = ModifierFamilies.recognize("Wild Pokemon Level Modifier Codes", listOf(cheat("Level Modifier Code", hgLevelOnly))).single()
        assertEquals(ModifierKind.LEVEL, level.kind)
        assertEquals(8, level.parameters.single().width)
        assertEquals((1..100).toList(), level.options.map { it.value })
        val platinum = ModifierFamilies.recognize("Encounter Codes", listOf(cheat("Wild Pokemon Modifier Code (Calculator)", platinumCalculator))).single()
        assertEquals(listOf(ModifierKind.SPECIES, ModifierKind.LEVEL), platinum.parameters.map { it.kind })
        assertEquals(listOf(0, 1), platinum.parameters.map { it.blockIndex })
    }

    @Test fun fixedFormIsRecognisedWithItsValue() {
        val canonical = cheat("Choose Pokémon and level", WildEncounterCheat.code(25, 5), enabled = true)
        val family = ModifierFamilies.recognize("40 - Wild encounters · choose Pokémon and level", listOf(canonical)).single()
        assertEquals(25, family.parameters[0].current)
        assertEquals(5, family.parameters[1].current)
        assertEquals(25, family.current?.value)
        assertEquals(5, family.currentLevel)
    }

    @Test fun absoluteLoadsAndPlainCodesAreNotFamilies() {
        val folder = "Encounter Codes"
        assertTrue(ModifierFamilies.recognize(folder, listOf(cheat("Encounter Random Wild Pokemon", randomEncounterAbsolute))).isEmpty())
        assertTrue(ModifierFamilies.recognize(folder, listOf(cheat("Wild Pokemon Have Max IVs", maxIvs))).isEmpty())
        assertTrue(ModifierFamilies.recognize("Miscellaneous Codes", listOf(cheat("Recollect 2nd Generation Starters", hgV1))).isEmpty())
        assertTrue(ModifierFamilies.recognize("Miscellaneous Codes", listOf(cheat("Money", hgV1))).isEmpty())
    }
```

- [ ] **Step 2: Eseguire, devono fallire** (`itemLoadFamily` restituisce null).

- [ ] **Step 3: Implementazione** — sostituire lo stub di Task 2:

```kotlin
    private val SKIPPABLE = setOf(ArCode.LOOP, ArCode.DATA_OP, ArCode.OFFSET_SET, ArCode.OFFSET_ADD)
    private const val MAX_PARAMETERS = 2
    private const val MAX_LEVEL = 100

    private fun itemLoadFamily(folderName: String, cheat: Cheat, blocks: List<ArBlock>): ModifierFamily? {
        val parameters = mutableListOf<ModifierParameter>()
        for ((blockIndex, block) in blocks.withIndex()) {
            if (parameters.size == MAX_PARAMETERS) break
            val found = parameterIn(block) ?: continue
            val kind = if (found.width == 8 || parameters.isNotEmpty()) ModifierKind.LEVEL else ModifierKind.SPECIES
            parameters += found.copy(blockIndex = blockIndex, kind = kind)
        }
        if (parameters.isEmpty()) return null
        val kind = parameters.first().kind
        val options = if (kind == ModifierKind.SPECIES) PokemonSpecies.all.map { ModifierOption(it.number, it.label, null) }
            else (1..MAX_LEVEL).map { ModifierOption(it, it.toString(), null) }
        return ModifierFamily(
            title = cheat.name, kind = kind, source = ModifierSource.ITEM_LOAD, folderName = folderName,
            role = roleOf("$folderName ${cheat.name}"), members = listOf(cheat), options = options, parameters = parameters,
            instructions = cheat.description?.takeIf(String::isNotBlank),
        )
    }

    /**
     * `DA/DB addr` (relative) or `D5 value` (fixed form), then only loop/data/offset opcodes, then a relative
     * `D7/D8` store. Absolute loads read game state (RNG), not a user-controlled count: never rewritten.
     */
    private fun parameterIn(block: ArBlock): ModifierParameter? {
        val ins = block.instructions
        for (i in ins.indices) {
            val loadWidth = when (ins[i].a) {
                ArCode.LOAD_16 -> 16
                ArCode.LOAD_8 -> 8
                ArCode.SET_DATA -> 0
                else -> continue
            }
            if (loadWidth != 0 && ins[i].b >= ArCode.RAM_START) continue
            var j = i + 1
            while (j < ins.size && ins[j].a in SKIPPABLE) j++
            val store = ins.getOrNull(j) ?: continue
            val storeWidth = when (store.a) { ArCode.STORE_16 -> 16; ArCode.STORE_8 -> 8; else -> continue }
            if (store.b >= ArCode.RAM_START) continue
            val width = if (loadWidth == 0) storeWidth else loadWidth
            val current = if (loadWidth == 0) ins[i].b.takeIf { it in 0..Int.MAX_VALUE }?.toInt() else null
            return ModifierParameter(blockIndex = 0, instructionIndex = i, width = width, kind = ModifierKind.SPECIES, current = current)
        }
        return null
    }
```

- [ ] **Step 4: Eseguire, devono passare.**

- [ ] **Step 5: Commit** — `feat(cheats): recognise item-count and calculator modifier codes`

---

### Task 4: Applicare la scelta — selezione, riscrittura, profilo canonico, mutua esclusione

**Files:**
- Modify: `app/src/main/java/me/magnum/melonds/common/cheats/ModifierFamily.kt` (`ModifierFamilies.select/activate/disable/exclusions`)
- Modify: `app/src/main/java/me/magnum/melonds/common/cheats/WildEncounterCheat.kt` (ridotto)
- Delete: `app/src/main/java/me/magnum/melonds/common/cheats/NatureEncounterCheat.kt`, `app/src/test/java/me/magnum/melonds/common/cheats/NatureEncounterCheatTest.kt`
- Modify: `app/src/test/java/me/magnum/melonds/common/cheats/WildEncounterCheatTest.kt` (solo canonico + supports)
- Test: `ModifierFamilyTest.kt` (aggiunte)

**Interfaces:**
- Produces: `ModifierFamilies.select(family, value: Int, level: Int?, families: List<ModifierFamily>, canonical: ((Int, Int) -> String)?): List<Cheat>`, `ModifierFamilies.activate(family, cheat, families): List<Cheat>`, `ModifierFamilies.disable(family): List<Cheat>`, `ModifierFamilies.exclusions(family, exceptId: Long?, families): List<Cheat>`, `ModifierFamilies.rewrite(code: String, parameters: List<ModifierParameter>, values: List<Int>): String`.
- `WildEncounterCheat` diventa: `fun supports(gameCode, checksum): Boolean`, `fun code(species: Int, level: Int): String` (invariato nel risultato), nient'altro.

- [ ] **Step 1: Test che falliscono**

```kotlin
    @Test fun enumeratedSelectionEnablesOneAndDisablesSiblingsAndSameGroupFamilies() {
        val gen1 = (1..5).map { blackWild(it, "g1-$it") }.mapIndexed { i, c -> if (i == 0) c.copy(enabled = true) else c }
        val gen2 = (152..156).map { blackWild(it, "g2-$it") }.mapIndexed { i, c -> if (i == 1) c.copy(enabled = true) else c }
        val starter = (1..5).map { blackWild(it, "s$it") }.map { it.copy(enabled = true) }
        val families = ModifierFamilies.recognize("Wild Pokemon Modifier - Generation 1", gen1) +
            ModifierFamilies.recognize("Wild Pokemon Modifier - Generation 2", gen2) +
            ModifierFamilies.recognize("Starter Modifier - Generation 1", starter)
        val changes = ModifierFamilies.select(families[1], 154, null, families, null)
        assertEquals(setOf(gen1[0].id, gen2[1].id, gen2[2].id), changes.map { it.id }.toSet())
        assertEquals(listOf(gen2[2].id), changes.filter { it.enabled }.map { it.id })
    }

    @Test fun itemLoadSelectionRewritesOnlyTheLoad() {
        val cheat = cheat("Wild Pokemon Modifier v1", hgV1)
        val families = ModifierFamilies.recognize("Wild Pokemon Modifier Codes", listOf(cheat))
        val changed = ModifierFamilies.select(families.single(), 25, null, families, null).single()
        assertTrue(changed.enabled)
        assertEquals(hgV1.replace("DA000000 0000DCF6", "D5000000 00000019"), changed.code)
        val again = ModifierFamilies.recognize("Wild Pokemon Modifier Codes", listOf(changed)).single()
        assertEquals(25, again.current?.value)
        val reconfigured = ModifierFamilies.select(again, 1, null, listOf(again), null).single()
        assertEquals(hgV1.replace("DA000000 0000DCF6", "D5000000 00000001"), reconfigured.code)
    }

    @Test fun canonicalProfileReproducesTheSgpCodeByteForByte() {
        val expected = "52246C94 28038800 6211186C 00000000 B211186C 00000000 D5000000 00000019 C0000000 00000027 D7000000 00032A48 D2000000 00000000 " +
            "52246C94 28038800 6211186C 00000000 B211186C 00000000 D5000000 00000019 C0000000 0000000B D8000000 00032A3C D2000000 00000000"
        assertEquals(expected, WildEncounterCheat.code(25, 25))
        val legacy = cheat("Wild Pokemon and Level Modifier", hgSpeciesAndLevel)
        val families = ModifierFamilies.recognize("40 - Wild encounters", listOf(legacy))
        val changed = ModifierFamilies.select(families.single(), 25, 25, families, WildEncounterCheat::code).single()
        assertEquals(expected, changed.code)
    }

    @Test fun levelIsRequiredWhenTheFamilyHasALevelParameter() {
        val families = ModifierFamilies.recognize("Wild", listOf(cheat("Wild Pokemon and Level Modifier", hgSpeciesAndLevel)))
        assertThrows(IllegalArgumentException::class.java) { ModifierFamilies.select(families.single(), 25, null, families, null) }
        assertThrows(IllegalArgumentException::class.java) { ModifierFamilies.select(families.single(), 0, 5, families, null) }
    }

    @Test fun disableAndActivateFollowExclusionGroups() {
        val selector = cheat("Choose", WildEncounterCheat.code(25, 5), enabled = true)
        val levels = listOf(1, 5, 10, 20, 30).map(::hgLevel)
        val families = ModifierFamilies.recognize("40 - Wild encounters", listOf(selector)) + ModifierFamilies.recognize("49 - Wild · level 1-100", levels)
        val activated = ModifierFamilies.activate(families[1], levels[2], families)
        assertEquals(setOf(selector.id, levels[2].id), activated.map { it.id }.toSet())
        assertFalse(activated.single { it.id == selector.id }.enabled)
        assertEquals(listOf(selector.copy(enabled = false)), ModifierFamilies.disable(families[0]))
    }
```

- [ ] **Step 2: Eseguire, devono fallire.**

- [ ] **Step 3: Implementazione** in `ModifierFamilies`:

```kotlin
    /** Cheats to stage so that [value] (and [level]) become the active choice of [family]. */
    fun select(family: ModifierFamily, value: Int, level: Int?, families: List<ModifierFamily>, canonical: ((Int, Int) -> String)?): List<Cheat> {
        require(family.options.any { it.value == value }) { "value $value is not an option of ${family.title}" }
        val chosen = when (family.source) {
            ModifierSource.ENUMERATED -> requireNotNull(family.options.first { it.value == value }.cheat)
            ModifierSource.ITEM_LOAD -> {
                val cheat = family.members.single()
                val code = if (family.hasLevelParameter) {
                    val chosenLevel = requireNotNull(level) { "${family.title} needs a level" }
                    require(chosenLevel in 1..MAX_LEVEL) { "level $chosenLevel out of range" }
                    canonical?.takeIf { family.kind == ModifierKind.SPECIES }?.invoke(value, chosenLevel)
                        ?: rewrite(cheat.code, family.parameters, listOf(value, chosenLevel))
                } else rewrite(cheat.code, family.parameters, listOf(value))
                cheat.copy(code = code)
            }
        }
        return activate(family, chosen, families)
    }

    /** [cheat] (a member of [family]) becomes enabled; conflicting active cheats are disabled. */
    fun activate(family: ModifierFamily, cheat: Cheat, families: List<ModifierFamily>): List<Cheat> =
        exclusions(family, cheat.id, families) + cheat.copy(enabled = true)

    fun disable(family: ModifierFamily): List<Cheat> = family.members.filter { it.enabled }.map { it.copy(enabled = false) }

    /** Active members of [family] and of every family it conflicts with, except [exceptId], as disabled copies. */
    fun exclusions(family: ModifierFamily, exceptId: Long?, families: List<ModifierFamily>): List<Cheat> =
        (listOf(family) + families.filter { !it.sameAs(family) && it.conflictsWith(family) })
            .flatMap { it.members }
            .filter { it.enabled && it.id != exceptId }
            .distinctBy { it.id }
            .map { it.copy(enabled = false) }

    /** Replace each parameter's load with `D5000000 value`; every other word stays as it is. */
    fun rewrite(code: String, parameters: List<ModifierParameter>, values: List<Int>): String {
        val blocks = requireNotNull(ArCode.parse(code)) { "unparsable code" }.map { it.instructions.toMutableList() }
        parameters.zip(values).forEach { (parameter, value) ->
            require(value >= 0) { "negative value" }
            blocks[parameter.blockIndex][parameter.instructionIndex] = ArInstruction(ArCode.SET_DATA, value.toLong())
        }
        return ArCode.render(blocks.map { ArBlock(it) })
    }
```

`WildEncounterCheat.kt` ridotto a:

```kotlin
package me.magnum.melonds.common.cheats

import java.util.Locale

/** Sacred Gold Plus 1.2/1.2.1 (a HeartGold hack): the native L+R toggle understands this exact code shape. */
object WildEncounterCheat {
    fun supports(gameCode: String, checksum: String) =
        gameCode == "IPKE" && checksum.uppercase(Locale.ROOT) in setOf("19D1EEBB", "1C1F741C")

    private const val SPECIES = "52246C94 28038800 6211186C 00000000 B211186C 00000000 D5000000 %08X C0000000 00000027 D7000000 00032A48 D2000000 00000000"
    private const val LEVEL = "52246C94 28038800 6211186C 00000000 B211186C 00000000 D5000000 %08X C0000000 0000000B D8000000 00032A3C D2000000 00000000"

    /** The canonical selector code: overlay signature instead of a button trigger, fixed species and level. */
    fun code(species: Int, level: Int): String {
        require(species in 1..493 && level in 1..100)
        return (SPECIES + " " + LEVEL).format(Locale.ROOT, species, level)
    }
}
```

`WildEncounterCheatTest.kt` → tenere `catalogAndSearch`, `supports`, e un test `codeHasNoButtonTriggerAndNoInventoryWrites` (loop 1..493 × {1,10,100}: `assertFalse(code.contains("94000130"))`, `assertFalse(code.contains("0000DCF"))`, `assertThrows` per valori fuori range). Rimuovere `selection`/`configure`. Eliminare `NatureEncounterCheat.kt` e il suo test (`git rm`).

- [ ] **Step 4: Eseguire `--tests 'me.magnum.melonds.common.cheats.*'`, deve passare.**

- [ ] **Step 5: Commit** — `feat(cheats): apply a family selection — enable, rewrite, canonical SGP code, exclusion groups`

---

### Task 5: Fixture reale e test attraverso il parser XML dell'app

**Files:**
- Create: `app/src/test/resources/cheats/modifier-families.xml`
- Test: `app/src/test/java/me/magnum/melonds/common/cheats/ModifierFamilyFixtureTest.kt`

**Interfaces:**
- Consumes: `XmlCheatDatabaseParser.parseCheatDatabase(ProgressTrackerInputStream, CheatDatabaseParserListener)`, `ModifierFamilies.recognize(CheatFolder)`.

- [ ] **Step 1: Fixture** — estratta dai cataloghi reali (formato `<codelist>`), giochi e cartelle:
  - `Pokemon - HeartGold Version (USA)` `IPKE 4DFFBF91`: `Wild Pokemon Modifier Codes` (4 cheat), `Wild Pokemon Level Modifier Codes` (13), `Wild Pokemon Nature Modifier Codes` (25), `Miscellaneous Codes` (i 2 `Recollect … Starters`).
  - `Pokemon - Platinum Version (USA)` `CPUE D074D1B3`: `Encounter Codes` (5), `Starter #1 Modifier - Generation 1` (prime 6), `Starter #2 Modifier - Generation 1` (prime 6).
  - `Pokemon - Black Version (USA, Europe)` `IRBO 106820A5`: `Wild Pokemon Modifier - Generation 1` (prime 10), `Wild Pokemon Modifier - Generation 2` (prime 6), `Wild Pokemon Level Modifier Codes` (5), `Encounter Codes` (6), `Starter Modifier - Generation 1` (prime 6).
  - `Sacred Gold Plus 1.2.1 EN` `IPKE 19D1EEBB`: cartelle 40, 41, 44, 45, 49 (prime 10), 50 (`LEGGIMI` + prime 10).
  Generarla con uno script *fuori dal repo* (scratchpad) che filtra i due XML; in repo entra solo l'XML risultante. Verificare a mano che ogni `<codes>` sia una riga per istruzione come nell'originale.

- [ ] **Step 2: Test**

```kotlin
package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.CheatDatabase
import me.magnum.melonds.domain.model.Game
import org.junit.Assert.*
import org.junit.Test

class ModifierFamilyFixtureTest {
    private val games: Map<String, Game> by lazy {
        val stream = requireNotNull(javaClass.getResourceAsStream("/cheats/modifier-families.xml"))
        val parsed = mutableListOf<Game>()
        XmlCheatDatabaseParser().parseCheatDatabase(ProgressTrackerInputStream(stream), object : CheatDatabaseParserListener {
            override fun onDatabaseParseStart(databaseName: String) = CheatDatabase(1, databaseName)
            override fun onGameParseStart(gameName: String) {}
            override fun onGameParsed(game: Game) { parsed += game }
            override fun onParseComplete() {}
        })
        parsed.associateBy { it.gameChecksum }
    }
    private fun families(checksum: String) = games.getValue(checksum).cheats.flatMap { ModifierFamilies.recognize(it) }

    @Test fun heartGoldExposesSpeciesLevelAndNatureFamilies() {
        val byTitle = families("4DFFBF91").associateBy { it.title }
        assertEquals(setOf(ModifierKind.SPECIES, ModifierKind.LEVEL), byTitle.getValue("Wild Pokemon and Level Modifier").parameters.map { it.kind }.toSet())
        assertEquals(ModifierKind.SPECIES, byTitle.getValue("Wild Pokemon Modifier v1").kind)
        assertEquals(ModifierKind.LEVEL, byTitle.getValue("Level Modifier Code").kind)
        assertEquals(12, byTitle.getValue("Wild Pokemon Level Modifier Codes").members.size)
        assertEquals(ModifierKind.NATURE, byTitle.getValue("Wild Pokemon Nature Modifier Codes").kind)
        assertFalse(byTitle.containsKey("Recollect 2nd Generation Starters"))
        assertFalse(byTitle.containsKey("Wild Pokemon Have Max IVs"))
    }

    @Test fun platinumCalculatorAndStarterSlots() {
        val fams = families("D074D1B3")
        val calc = fams.single { it.title == "Wild Pokemon Modifier Code (Calculator)" }
        assertEquals(listOf(ModifierKind.SPECIES, ModifierKind.LEVEL), calc.parameters.map { it.kind })
        val s1 = fams.single { it.title.startsWith("Starter #1") }; val s2 = fams.single { it.title.startsWith("Starter #2") }
        assertFalse(s1.conflictsWith(s2))
        assertTrue(fams.none { it.title == "Wild Shiny Encounters" })
    }

    @Test fun blackFamiliesAreEnumeratedAndRandomEncountersAreNot() {
        val fams = families("106820A5")
        val gen1 = fams.single { it.title == "Wild Pokemon Modifier - Generation 1" }
        assertEquals(ModifierSource.ENUMERATED, gen1.source); assertEquals(ModifierKind.SPECIES, gen1.kind)
        assertEquals("Bulbasaur", gen1.options.first { it.value == 1 }.label)
        assertTrue(gen1.conflictsWith(fams.single { it.title == "Wild Pokemon Modifier - Generation 2" }))
        assertFalse(gen1.conflictsWith(fams.single { it.title == "Starter Modifier - Generation 1" }))
        assertTrue(fams.none { it.title == "Encounter Random Wild Pokemon" })
        assertTrue(fams.none { it.title == "Wild Pokemon Modifier v1" })  // absolute load, and gamba A covers B/W
    }

    @Test fun sacredGoldPlusIsFullyCovered() {
        val fams = families("19D1EEBB")
        val selector = fams.single { it.title == "Choose Pokémon and level" }
        assertEquals(ModifierSource.ITEM_LOAD, selector.source); assertTrue(selector.hasLevelParameter)
        val species50 = fams.single { it.folderName.startsWith("50 ") }
        assertEquals(10, species50.members.size); assertEquals(ModifierKind.SPECIES, species50.kind)
        assertTrue(selector.conflictsWith(species50))
        assertTrue(selector.conflictsWith(fams.single { it.folderName.startsWith("49 ") }))
        val natures = fams.filter { it.kind == ModifierKind.NATURE }
        assertEquals(2, natures.size); assertTrue(natures[0].conflictsWith(natures[1]))
        val classic = fams.single { it.folderName.startsWith("41 ") && it.source == ModifierSource.ITEM_LOAD }
        assertEquals(ModifierKind.LEVEL, classic.kind)
    }
}
```

- [ ] **Step 3: Eseguire, deve passare** (se `ProgressTrackerInputStream` ha un'altra firma, adattare la riga di costruzione).

- [ ] **Step 4: Commit** — `test(cheats): real-catalogue fixture through the XML importer`

---

### Task 6: `CheatsViewModel` — voci di lista, selezione, esclusione, annulla

**Files:**
- Create: `app/src/main/java/me/magnum/melonds/ui/cheats/model/CheatListItem.kt`
- Modify: `app/src/main/java/me/magnum/melonds/ui/cheats/CheatsViewModel.kt`
- Delete: `app/src/test/java/me/magnum/melonds/ui/cheats/WildEncounterViewModelTest.kt` → Create: `ModifierFamilyViewModelTest.kt` (stessi scenari, `FakeRepository` invariato)

**Interfaces:**
- Produces: `sealed class CheatListItem { Single(cheat); Family(family) }` con `key: Any`; `CheatsViewModel.folderItems: SharedFlow<CheatsScreenUiState<List<CheatListItem>>>`, `fun selectFamilyOption(family: ModifierFamily, value: Int, level: Int?)`, `fun disableFamily(family: ModifierFamily)`. Rimossi: `wildEncounterSupported`, `configureWildEncounter`, `disabledConflicts`.

- [ ] **Step 1: `CheatListItem.kt`**

```kotlin
package me.magnum.melonds.ui.cheats.model

import me.magnum.melonds.common.cheats.ModifierFamily
import me.magnum.melonds.domain.model.Cheat

sealed class CheatListItem {
    abstract val key: Any
    data class Single(val cheat: Cheat) : CheatListItem() { override val key: Any get() = cheat.id ?: cheat.code }
    data class Family(val family: ModifierFamily) : CheatListItem() { override val key: Any get() = "family:${family.folderName}:${family.title}:${family.source}" }
}
```

- [ ] **Step 2: Test che fallisce** (`ModifierFamilyViewModelTest`, stesso `handle()`/`pending()`/`FakeRepository` del file attuale; `selected`/`prior` diventano un ITEM_LOAD e le nature usano `sgp-nature-codes.tsv`):

```kotlin
    @Test fun selectingAFamilyOptionRewritesTheCodeAndDisablesTheConflicts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakeRepository(folder)   // folder: selected = HG "Wild Pokemon and Level Modifier" (load form), prior = canonical code(1,5) enabled
            val state = handle()
            val vm = CheatsViewModel(repo, state)
            val family = (vm.folderItems.first { it is CheatsScreenUiState.Ready } as CheatsScreenUiState.Ready).data
                .filterIsInstance<CheatListItem.Family>().single { it.family.title == "Wild Pokemon and Level Modifier" }.family
            vm.selectFamilyOption(family, 493, 100)
            runCurrent()
            assertEquals(setOf(1L, 2L), pending(state).map { it.id }.toSet())
            assertEquals(WildEncounterCheat.code(493, 100), pending(state).single { it.enabled }.code)   // game is SGP → canonical profile
            vm.commitCheatChanges(); runCurrent()
            assertFalse(repo.rows.getValue(2).enabled)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun folderItemsCollapseAFamilyIntoOneRowAtItsFirstMember() = runTest { /* Level 1..5 + un cheat singolo prima e uno dopo → 3 item: Single, Family(5), Single */ }
    @Test fun togglingAFamilyMemberFromTheExpandedListAppliesExclusions() = runTest { /* enable Level 10 → il selettore canonico attivo viene spento */ }
    @Test fun disableFamilyTurnsTheActiveMemberOff() = runTest { ... }
    // + riscritti 1:1 dagli scenari esistenti:
    // genderAndNatureSelectionsAreExclusiveAndKeepTheSpeciesSelector, configurationBlocksCommitUntilFreshRepositoryReadCompletes,
    // deletingPendingCheatDoesNotPoisonTheRemainingBatch, undoKeepsEffectiveSelectionWithoutReintroducingDeletedId,
    // enablingStandaloneLevelDisablesSelectorAndKeepsStandaloneSpecies, failedConfigurationKeepsPendingChangesAndReportsWithoutExit,
    // undoOfActiveSelectorReplacesASelectionEnabledAfterDeletion — sostituendo `configureWildEncounter(selected, form)` con
    // `selectFamilyOption(family, 493, 100)` e `WildEncounterCheat.conflicts` con l'appartenenza alla famiglia.
```

- [ ] **Step 3: ViewModel** — sostituzioni puntuali:

```kotlin
    // import: rimuovere NatureEncounterCheat; aggiungere ModifierFamilies, ModifierFamily, CheatListItem, combine

    private fun canonicalFor(game: Game): ((Int, Int) -> String)? =
        if (WildEncounterCheat.supports(game.gameCode, game.gameChecksum)) WildEncounterCheat::code else null

    val folderItems: SharedFlow<CheatsScreenUiState<List<CheatListItem>>> by lazy {
        combine(folderCheats, selectedCheatFolder.filterNotNull()) { state, folder ->
            when (state) {
                is CheatsScreenUiState.Loading -> CheatsScreenUiState.Loading()
                is CheatsScreenUiState.Ready -> CheatsScreenUiState.Ready(listItems(folder.name, state.data))
            }
        }.shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1000L), replay = 1)
    }

    private fun listItems(folderName: String, cheats: List<Cheat>): List<CheatListItem> {
        val families = ModifierFamilies.recognize(folderName, cheats)
        val placed = mutableSetOf<Long?>()
        return cheats.mapNotNull { cheat ->
            val family = families.firstOrNull { it.contains(cheat) } ?: return@mapNotNull CheatListItem.Single(cheat)
            if (family.members.first().id in placed) null else { placed += family.members.first().id; CheatListItem.Family(family) }
        }
    }

    private suspend fun currentGameFolders(game: Game): List<CheatFolder> {
        val pending = modifiedCheatSet.value.associateBy { it.id }
        return cheatsRepository.getAllGameCheats(game).first().map { folder -> folder.copy(cheats = folder.cheats.map { pending[it.id] ?: it }) }
    }

    fun selectFamilyOption(family: ModifierFamily, value: Int, level: Int?) {
        val game = savedStateHandle.get<GameParcelable>(KEY_SELECTED_GAME)?.toGame() ?: return
        modifyCheats {
            val families = currentGameFolders(game).flatMap { ModifierFamilies.recognize(it) }
            val live = families.firstOrNull { it.sameAs(family) } ?: return@modifyCheats
            stageCheats(ModifierFamilies.select(live, value, level, families, canonicalFor(game)))
        }
    }

    fun disableFamily(family: ModifierFamily) {
        val game = savedStateHandle.get<GameParcelable>(KEY_SELECTED_GAME)?.toGame() ?: return
        modifyCheats {
            val live = currentGameFolders(game).flatMap { ModifierFamilies.recognize(it) }.firstOrNull { it.sameAs(family) } ?: return@modifyCheats
            stageCheats(ModifierFamilies.disable(live))
        }
    }

    fun toggleCheat(cheat: Cheat) {
        if (committingCheatsChangesState.value) return
        val effective = modifiedCheatSet.value.firstOrNull { it.id == cheat.id } ?: cheat
        val game = savedStateHandle.get<GameParcelable>(KEY_SELECTED_GAME)?.toGame()
        if (!effective.enabled && game != null) {
            modifyCheats {
                val families = currentGameFolders(game).flatMap { ModifierFamilies.recognize(it) }
                val family = families.firstOrNull { it.contains(effective) && it.exclusionGroups.isNotEmpty() }
                stageCheats(if (family != null) ModifierFamilies.activate(family, effective, families) else listOf(effective.copy(enabled = true)))
            }
        } else {
            stageCheats(listOf(effective.copy(enabled = !effective.enabled)))
        }
    }

    // undoCheatDeletion: sostituire il calcolo di `conflicts` con
            val conflicts = if (restored.enabled && game != null) {
                val folders = currentGameFolders(game).map { if (it.id == deletedCheat.folder.id) it.copy(cheats = it.cheats + restored) else it }
                val families = folders.flatMap { ModifierFamilies.recognize(it) }
                families.firstOrNull { it.contains(restored) && it.exclusionGroups.isNotEmpty() }
                    ?.let { ModifierFamilies.exclusions(it, restored.id, families) }.orEmpty()
            } else emptyList()
```

Rimuovere `wildEncounterSupported`, `disabledConflicts`, `configureWildEncounter`, `currentGameCheats` (se non più usato).

- [ ] **Step 4: Eseguire `--tests 'me.magnum.melonds.ui.cheats.ModifierFamilyViewModelTest'`, deve passare.**

- [ ] **Step 5: Commit** — `feat(cheats): view model exposes modifier families and applies selections through them`

---

### Task 7: Interfaccia — riga famiglia, espansione, `ModifierFamilyDialog`, stringhe

**Files:**
- Rename: `ui/cheats/ui/WildEncounterDialog.kt` → `ui/cheats/ui/ModifierFamilyDialog.kt`
- Modify: `ui/cheats/ui/CheatListScreen.kt`, `ui/cheats/ui/CheatsScreen.kt` (righe 187–195)
- Create: `ui/cheats/ui/item/FamilyItem.kt`
- Modify: `res/values/wild_encounters.xml`, `res/values-it/wild_encounters.xml`
- Rename test: `ui/cheats/WildEncounterDialogTest.kt` → `ModifierFamilyDialogTest.kt`

**Interfaces:**
- `ModifierFamilyDialog(family: ModifierFamily, onDismiss: () -> Unit, onDisable: () -> Unit, onConfirm: (value: Int, level: Int?) -> Unit)`
- `FamilyItem(modifier, family: ModifierFamily, expanded: Boolean, onClick, onToggleExpanded)`
- `CheatListScreen(modifier, contentPadding, items: CheatsScreenUiState<List<CheatListItem>>, onSelectFamilyOption: (ModifierFamily, Int, Int?) -> Unit, onDisableFamily: (ModifierFamily) -> Unit, onCheatClick, onAddNewCheat, onUpdateCheat, onDeleteCheatClick)`

- [ ] **Step 1: Stringhe** (EN, poi IT):

```xml
    <string name="modifier_family_show_all">Show all (%1$d)</string>
    <string name="modifier_family_hide">Hide</string>
    <string name="modifier_family_inactive">Not active</string>
    <string name="modifier_family_disable">Disable</string>
    <string name="modifier_family_level_only">Level</string>
```
IT: `Mostra tutte (%1$d)`, `Nascondi`, `Non attivo`, `Disattiva`, `Livello`.

- [ ] **Step 2: Test del dialog che fallisce** (`ModifierFamilyDialogTest`): `searchSelectValidateAndConfirm` con una famiglia ITEM_LOAD a due parametri (HG `and Level`) → `onConfirm(493, 100)`; `enumeratedFamilyHasNoLevelField` (famiglia Black Gen 1 → nessun nodo "Level 1–100", conferma → `onConfirm(25, null)`); `disableButtonOnlyWhenActive`; `cancelDoesNotSave`; `landscapeKeepsSearchResultsAndActionsVisible` invariato.

- [ ] **Step 3: `ModifierFamilyDialog.kt`** — corpo di `WildEncounterDialog` con: `initialValue = family.current?.value`, `initialLevel = family.currentLevel ?: 5`; la lista mostra `family.options` filtrate da `PokemonSpecies.search`-style su `label` (usare `PokemonSpecies.normalize(label).contains(normalize(query)) || value == query.toIntOrNull()`); il campo livello solo `if (family.hasLevelParameter || family.kind == ModifierKind.LEVEL)`; per `kind == LEVEL` nessuna lista, solo il campo, conferma `onConfirm(level, null)`; help = `family.instructions ?: stringResource(R.string.wild_encounter_help)`; pulsante `modifier_family_disable` se `family.current != null`; titolo = `family.title`.

- [ ] **Step 4: `FamilyItem.kt`** — stessa struttura di `CheatItem` (Row cliccabile, `Checkbox(checked = family.current != null, onCheckedChange = null)`, colonna con `family.title` e `CaptionText` = `current?.label?.let { if (family.currentLevel != null) "$it · Lv ${family.currentLevel}" else it } ?: stringResource(R.string.modifier_family_inactive)`, `TextButton` a destra con `modifier_family_show_all`/`modifier_family_hide`).

- [ ] **Step 5: `CheatListScreen.kt`** — parametri come in Interfaces; `var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }`; `var dialogFamilyKey by rememberSaveable { mutableStateOf<String?>(null) }`; nella `LazyColumn`: per `Single` → `CheatItem` come oggi (senza il ramo `wildEncounterSupported`); per `Family` → `FamilyItem` + `if (key in expanded) family.members.forEach { CheatItem(...) }` con `items` piatti (costruire prima la lista piatta di righe da renderizzare, chiave stabile per riga). Dialog: `dialogFamilyKey?.let { key -> items.filterIsInstance<Family>().firstOrNull { it.key == key }?.let { ModifierFamilyDialog(it.family, onDismiss = { dialogFamilyKey = null }, onDisable = { onDisableFamily(it.family); dialogFamilyKey = null }, onConfirm = { v, l -> onSelectFamilyOption(it.family, v, l); dialogFamilyKey = null }) } }`.

- [ ] **Step 6: `CheatsScreen.kt`** — `val items by viewModel.folderItems.collectAsStateWithLifecycle(CheatsScreenUiState.Loading())`; passare `items`, `onSelectFamilyOption = viewModel::selectFamilyOption`, `onDisableFamily = viewModel::disableFamily`; rimuovere `wildEncounterSupported`/`onConfigureWildEncounter`.

- [ ] **Step 7: Eseguire `--tests 'me.magnum.melonds.ui.cheats.*'` e poi tutta la suite; deve passare.**

- [ ] **Step 8: Commit** — `feat(cheats): collapsed family rows and a generic modifier dialog`

---

### Task 8: Persistenza, suite completa, lint

**Files:**
- Rename: `test/impl/WildEncounterPersistenceTest.kt` → `ModifierFamilyPersistenceTest.kt` (stesso scenario, codici da `ModifierFamilies.select` su HG `v1`)

- [ ] **Step 1:** rinominare e adattare il test; eseguire.
- [ ] **Step 2:** `sh ./gradlew :app:testGitHubNightlyDebugUnitTest` completa → tutti verdi; annotare il numero di test.
- [ ] **Step 3:** `sh ./gradlew :app:lintGitHubNightlyDebug` → nessun errore nuovo (confrontare con `ventuno`).
- [ ] **Step 4:** `grep -rn "WildEncounterDialog\|NatureEncounter\|configureWildEncounter\|wildEncounterSupported" app/src` → vuoto.
- [ ] **Step 5: Commit** — `test(cheats): persistence scenario on the generic selector`

---

### Task 9: Build, installazione in place sulla Thor, controlli via adb

- [ ] **Step 1:** `sh ./gradlew :app:assembleGitHubNightlyProfiling -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false`.
- [ ] **Step 2:** copiare l'APK in `~/Developer/android-test/apk/ventuno-2.1.4/` con `SHA256SUMS` (dopo Task 10 step 1, la versione).
- [ ] **Step 3:** `adb -s 20e51bf7 install -r -t --no-incremental <apk>` — firma `~/.android/debug.keystore`, dati conservati; `adb shell dumpsys package me.magnum.melonds.nightly.perf | grep versionName` → `2.1.4`.
- [ ] **Step 4:** `adb shell input keyevent KEYCODE_WAKEUP`; avviare l'app; aprire la lista ROM → SGP → Cheats → cartella 50: `screencap` → una riga famiglia con «Mostra tutte (50)»; cartella 40: tocco → dialog → screenshot. Salvare gli screenshot in `~/Developer/android-test/thor/aggiornamento-20260919-sera/`.
- [ ] **Step 5:** `adb logcat -d | grep -iE 'AndroidRuntime|FATAL|melonds.*Exception'` → vuoto.

---

### Task 10: Merge, versione 2.1.4, tag, push, documentazione

- [ ] **Step 1:** su `feat/modifier-families`: `AppConfig.kt` → `versionCode = 45`, `versionName = "2.1.4"`; commit `chore(release): version 2.1.4 (versionCode 45)`.
- [ ] **Step 2:** `git fetch upstream && git rev-list --count ventuno..upstream/master` → se > 0, merge di `upstream/master` in `ventuno` prima.
- [ ] **Step 3:** `git checkout ventuno && git merge --no-ff feat/modifier-families`; `git tag v2.1.4`.
- [ ] **Step 4:** docs: `STATO-VENTUNO.md` (nuova sezione 2.1.4), `WHATS-NEW-VS-UPSTREAM.md` (voce «Pick your wild Pokémon in every game the cheat database supports»), `PR-DRAFT-APP.md` (nota: `pr-prep/app` da rigenerare); note di lavoro locali non versionate.
- [ ] **Step 5:** `git push origin ventuno --tags`; verificare `git rev-list --left-right --count ventuno...origin/ventuno` → `0 0`.
