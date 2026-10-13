import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureRulesTest {
    private val mainKt = "companion/ui/src/main/java/com/stormpanda/megingiard/Foo.kt"
    private val testKt = "companion/ui/src/test/java/com/stormpanda/megingiard/FooTest.kt"

    private fun rules(
        path: String,
        vararg lines: String,
    ) = ArchitectureRules.checkSourceFile(path, lines.toList()).map { it.ruleId }

    @Test
    fun `star import is flagged in main and test sources`() {
        assertEquals(listOf(ArchitectureRules.STAR_IMPORT), rules(mainKt, "import kotlinx.coroutines.*"))
        assertEquals(listOf(ArchitectureRules.STAR_IMPORT), rules(testKt, "import org.junit.*"))
    }

    @Test
    fun `shared core must not import android`() {
        val path = "shared/core/src/main/kotlin/com/stormpanda/megingiard/Model.kt"
        assertEquals(listOf(ArchitectureRules.CORE_ANDROID_IMPORT), rules(path, "import android.graphics.Bitmap"))
    }

    @Test
    fun `domain must not import compose`() {
        val path = "companion/domain/src/main/java/com/stormpanda/megingiard/Manager.kt"
        assertEquals(listOf(ArchitectureRules.DOMAIN_UI_IMPORT), rules(path, "import androidx.compose.ui.Modifier"))
    }

    @Test
    fun `android util Log is flagged except in AppLog`() {
        assertEquals(listOf(ArchitectureRules.ANDROID_LOG), rules(mainKt, "import android.util.Log"))
        assertEquals(emptyList<String>(), rules("shared/core/src/main/kotlin/x/AppLog.kt", "fun d() = android.util.Log.d(\"a\", \"b\")"))
    }

    @Test
    fun `kotlin language rules`() {
        assertEquals(listOf(ArchitectureRules.ENUM_VALUES), rules(mainKt, "val all = Mode.values()"))
        assertEquals(listOf(ArchitectureRules.JAVA_MATH), rules(mainKt, "val m = Math.max(a, b)"))
        assertEquals(emptyList<String>(), rules(mainKt, "val m = max(a, b)", "val e = Mode.entries"))
    }

    @Test
    fun `bare Job in CoroutineScope is flagged but SupervisorJob is not`() {
        assertEquals(listOf(ArchitectureRules.BARE_JOB), rules(mainKt, "private val scope = CoroutineScope(Job() + Dispatchers.Main)"))
        assertEquals(emptyList<String>(), rules(mainKt, "private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)"))
    }

    @Test
    fun `volume uuid path flagged in main but allowed in tests and comments`() {
        assertEquals(listOf(ArchitectureRules.VOLUME_UUID_PATH), rules(mainKt, "val root = \"/storage/6914-318F/ROMs\""))
        assertEquals(emptyList<String>(), rules(testKt, "val root = \"/storage/6914-318F/ROMs\""))
        assertEquals(emptyList<String>(), rules(mainKt, " * e.g. `/storage/A1B2-C3D4`"))
    }

    @Test
    fun `non private MutableStateFlow member is flagged`() {
        assertEquals(listOf(ArchitectureRules.PUBLIC_MUTABLE_STATE_FLOW), rules(mainKt, "    val state = MutableStateFlow(0)"))
        assertEquals(
            listOf(ArchitectureRules.PUBLIC_MUTABLE_STATE_FLOW),
            rules(mainKt, "    internal val state: MutableStateFlow<Int> = x"),
        )
        assertEquals(emptyList<String>(), rules(mainKt, "    private val _state = MutableStateFlow(0)"))
        assertEquals(emptyList<String>(), rules(mainKt, "    val state: StateFlow<Int> = _state.asStateFlow()"))
    }

    @Test
    fun `substring role guessing is flagged`() {
        assertEquals(listOf(ArchitectureRules.HEURISTIC_SUBSTRING), rules(mainKt, "if (pkg.contains(\"launcher\")) {"))
        assertEquals(listOf(ArchitectureRules.HEURISTIC_SUBSTRING), rules(mainKt, "pkg.contains(\"Game\", ignoreCase = true)"))
    }

    @Test
    fun `design tokens are enforced outside theme definition files`() {
        assertEquals(listOf(ArchitectureRules.INLINE_FONT_SIZE), rules(mainKt, "Text(\"x\", fontSize = 12.sp)"))
        assertEquals(listOf(ArchitectureRules.HARDCODED_COLOR), rules(mainKt, "val c = Color(0xFF121212)"))
        val theme = "shared/ui/src/main/java/com/stormpanda/megingiard/ui/AppTheme.kt"
        assertEquals(emptyList<String>(), rules(theme, "TextStyle(fontSize = 18.sp)", "val c = Color(0xFF121212)"))
    }

    @Test
    fun `unused TAG is flagged and used TAG passes`() {
        assertEquals(listOf(ArchitectureRules.UNUSED_TAG), rules(mainKt, "private const val TAG = \"Foo\""))
        assertEquals(emptyList<String>(), rules(mainKt, "private const val TAG = \"Foo\"", "AppLog.d(TAG, \"hi\")"))
    }

    @Test
    fun `translations report missing and stale keys`() {
        val result =
            ArchitectureRules.checkTranslations(
                localePath = "companion/ui/src/main/res/values-de/strings.xml",
                translatableDefaultNames = setOf("a", "b"),
                allDefaultNames = setOf("a", "b", "url"),
                localeNames = setOf("a", "old"),
            )
        assertEquals(
            listOf(ArchitectureRules.MISSING_TRANSLATION, ArchitectureRules.STALE_TRANSLATION),
            result.map { it.ruleId },
        )
        assertTrue(result[0].message.contains("'b'"))
        assertTrue(result[1].message.contains("'old'"))
    }

    @Test
    fun `baseline round trip and enforcement`() {
        val violations =
            ArchitectureRules.checkSourceFile(
                mainKt,
                listOf("val a = Color(0xFF000000)", "val b = Color(0xFF111111)"),
            )
        val baselineText = ArchitectureRules.formatBaseline(ArchitectureRules.countByKey(violations))
        val baseline = ArchitectureRules.parseBaseline(baselineText)
        assertEquals(2, baseline[ArchitectureRules.BaselineKey(ArchitectureRules.HARDCODED_COLOR, mainKt)])

        assertTrue(ArchitectureRules.applyBaseline(violations, baseline).failures.isEmpty())

        val more = violations + violations.first().copy(line = 3)
        assertTrue(ArchitectureRules.applyBaseline(more, baseline).failures.isNotEmpty())

        val fewer = violations.take(1)
        val result = ArchitectureRules.applyBaseline(fewer, baseline)
        assertTrue(result.failures.isEmpty())
        assertEquals(1, result.shrinkable.size)
    }
}
