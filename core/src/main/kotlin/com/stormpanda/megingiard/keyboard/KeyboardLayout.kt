package com.stormpanda.megingiard.keyboard

import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_0
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_1
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_2
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_3
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_4
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_5
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_6
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_7
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_8
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_9
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_A
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_APOSTROPHE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_B
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_BACKSLASH
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_BACKSPACE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_C
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_CAPSLOCK
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_COMMA
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_D
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_DELETE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_DOT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_DOWN
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_E
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_ENTER
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_EQUAL
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_ESC
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F1
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F10
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F11
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F12
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F2
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F3
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F4
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F5
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F6
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F7
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F8
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_F9
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_G
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_GRAVE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_H
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_I
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_INSERT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_J
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_K
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_L
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTALT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTBRACE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTCTRL
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTMETA
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_LEFTSHIFT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_M
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_MINUS
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_N
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_O
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_P
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_Q
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_R
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHTALT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHTBRACE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHTSHIFT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_S
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_SEMICOLON
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_SLASH
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_SPACE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_SYSRQ
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_T
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_TAB
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_U
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_UP
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_V
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_W
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_X
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_Y
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_Z
import kotlinx.serialization.Serializable

/**
 * Defines the logical layout of the virtual keyboard.
 *
 * Each [KeyDef] represents one key cap. [widthWeight] is relative to a
 * standard key (1.0f). Rows are ordered top-to-bottom.
 *
 * Special keys:
 * - [KeyType.TRACKPOINT] — renders the accent-colored trackpoint dot; no key injection
 * - [KeyType.MODIFIER]   — participates in the sticky/hold modifier state machine
 * - [KeyType.NORMAL]     — regular character or function key
 */
data class KeyDef(
    val id: String,
    val label: String,
    val linuxKeycode: Int,
    val widthWeight: Float = 1f,
    val type: KeyType = KeyType.NORMAL,
    val shiftLabel: String? = null,
    val altGrLabel: String? = null,
    val superscript: String? = null,
    val autoModifiers: List<Int> = emptyList(),
)

enum class KeyType { NORMAL, MODIFIER, TRACKPOINT }

enum class KeyboardMode { LETTERS, SYMBOLS_1, SYMBOLS_2, NUMERIC }

@Serializable
enum class KbLayout { QWERTZ, QWERTY, AZERTY }

enum class KbMouseBtnPos { LEFT, RIGHT, BOTH }

data class KeyboardLayoutState(
    val mode: KeyboardMode,
    val grid: List<List<KeyDef>>,
)

fun getPopupOptions(
    keyDef: KeyDef,
    isUpper: Boolean,
): List<String> {
    val id = keyDef.id
    when (id) {
        "j" -> return listOf("(", "[", "{", "<")
        "k" -> return listOf(")", "]", "}", ">")
        "l" -> return listOf("/", "\\")
        "s" -> return listOf("#", "$", "%")
        "lparen" -> return listOf("[", "{", "<")
        "rparen" -> return listOf("]", "}", ">")
        "slash" -> return listOf("\\")
        "hash" -> return listOf("$", "%")
    }
    val superscript = keyDef.superscript ?: return emptyList()
    return listOf(superscript)
}

fun getSuperscriptDisplayLabel(keyDef: KeyDef): String? =
    when (keyDef.id) {
        "j" -> "([{<"
        "k" -> ")]}>"
        "l" -> "/\\"
        "s" -> "#$%"
        "lparen" -> "[{<"
        "rparen" -> "]}>"
        "slash" -> "\\"
        "hash" -> "$%"
        else -> keyDef.superscript
    }

// ---------------------------------------------------------------------------
// Layout factories
// ---------------------------------------------------------------------------

fun qwertzLayout(mode: KeyboardMode = KeyboardMode.LETTERS): List<List<KeyDef>> =
    when (mode) {
        KeyboardMode.LETTERS -> {
            listOf(
                qwertzLettersRow1(),
                homeRowQwertz(),
                bottomRowQwertz(),
                bottomBarRow(),
            )
        }

        KeyboardMode.SYMBOLS_1 -> {
            symbols1Layout()
        }

        KeyboardMode.SYMBOLS_2 -> {
            symbols2Layout()
        }

        KeyboardMode.NUMERIC -> {
            numericLayout()
        }
    }

fun qwertyLayout(mode: KeyboardMode = KeyboardMode.LETTERS): List<List<KeyDef>> =
    when (mode) {
        KeyboardMode.LETTERS -> {
            listOf(
                qwertyLettersRow1(),
                homeRowQwerty(),
                bottomRowQwerty(),
                bottomBarRow(),
            )
        }

        KeyboardMode.SYMBOLS_1 -> {
            symbols1Layout()
        }

        KeyboardMode.SYMBOLS_2 -> {
            symbols2Layout()
        }

        KeyboardMode.NUMERIC -> {
            numericLayout()
        }
    }

fun azertyLayout(mode: KeyboardMode = KeyboardMode.LETTERS): List<List<KeyDef>> =
    when (mode) {
        KeyboardMode.LETTERS -> {
            listOf(
                azertyLettersRow1(),
                homeRowAzerty(),
                bottomRowAzerty(),
                bottomBarRow(),
            )
        }

        KeyboardMode.SYMBOLS_1 -> {
            symbols1Layout()
        }

        KeyboardMode.SYMBOLS_2 -> {
            symbols2Layout()
        }

        KeyboardMode.NUMERIC -> {
            numericLayout()
        }
    }

// ---------------------------------------------------------------------------
// Gboard visual rows
// ---------------------------------------------------------------------------

private fun qwertzLettersRow1(): List<KeyDef> =
    listOf(
        KeyDef("q", "q", KEY_Q, superscript = "1"),
        KeyDef("w", "w", KEY_W, superscript = "2"),
        KeyDef("e", "e", KEY_E, superscript = "3"),
        KeyDef("r", "r", KEY_R, superscript = "4"),
        KeyDef("t", "t", KEY_T, superscript = "5"),
        KeyDef("z", "z", KEY_Z, superscript = "6"),
        KeyDef("u", "u", KEY_U, superscript = "7"),
        KeyDef("i", "i", KEY_I, superscript = "8"),
        KeyDef("o", "o", KEY_O, superscript = "9"),
        KeyDef("p", "p", KEY_P, superscript = "0"),
    )

private fun qwertyLettersRow1(): List<KeyDef> =
    listOf(
        KeyDef("q", "q", KEY_Q, superscript = "1"),
        KeyDef("w", "w", KEY_W, superscript = "2"),
        KeyDef("e", "e", KEY_E, superscript = "3"),
        KeyDef("r", "r", KEY_R, superscript = "4"),
        KeyDef("t", "t", KEY_T, superscript = "5"),
        KeyDef("y", "y", KEY_Y, superscript = "6"),
        KeyDef("u", "u", KEY_U, superscript = "7"),
        KeyDef("i", "i", KEY_I, superscript = "8"),
        KeyDef("o", "o", KEY_O, superscript = "9"),
        KeyDef("p", "p", KEY_P, superscript = "0"),
    )

private fun azertyLettersRow1(): List<KeyDef> =
    listOf(
        KeyDef("a", "a", KEY_A, superscript = "1"),
        KeyDef("z", "z", KEY_Z, superscript = "2"),
        KeyDef("e", "e", KEY_E, superscript = "3"),
        KeyDef("r", "r", KEY_R, superscript = "4"),
        KeyDef("t", "t", KEY_T, superscript = "5"),
        KeyDef("y", "y", KEY_Y, superscript = "6"),
        KeyDef("u", "u", KEY_U, superscript = "7"),
        KeyDef("i", "i", KEY_I, superscript = "8"),
        KeyDef("o", "o", KEY_O, superscript = "9"),
        KeyDef("p", "p", KEY_P, superscript = "0"),
    )

private fun homeRowQwertz(): List<KeyDef> =
    listOf(
        KeyDef("a", "a", KEY_A, superscript = "@"),
        KeyDef("s", "s", KEY_S, superscript = "#"),
        KeyDef("d", "d", KEY_D, superscript = "_"),
        KeyDef("f", "f", KEY_F, superscript = "&"),
        KeyDef("g", "g", KEY_G, superscript = "-"),
        KeyDef("h", "h", KEY_H, superscript = "+"),
        KeyDef("j", "j", KEY_J, superscript = "("),
        KeyDef("k", "k", KEY_K, superscript = ")"),
        KeyDef("l", "l", KEY_L, superscript = "/"),
    )

private fun homeRowQwerty(): List<KeyDef> = homeRowQwertz()

private fun homeRowAzerty(): List<KeyDef> =
    listOf(
        KeyDef("q", "q", KEY_Q, superscript = "@"),
        KeyDef("s", "s", KEY_S, superscript = "#"),
        KeyDef("d", "d", KEY_D, superscript = "_"),
        KeyDef("f", "f", KEY_F, superscript = "&"),
        KeyDef("g", "g", KEY_G, superscript = "-"),
        KeyDef("h", "h", KEY_H, superscript = "+"),
        KeyDef("j", "j", KEY_J, superscript = "("),
        KeyDef("k", "k", KEY_K, superscript = ")"),
        KeyDef("l", "l", KEY_L, superscript = "/"),
        KeyDef("m", "m", KEY_M),
    )

private fun bottomRowQwertz(): List<KeyDef> =
    listOf(
        KeyDef("lshift", "Shift", KEY_LEFTSHIFT, widthWeight = 1.3f, type = KeyType.MODIFIER),
        KeyDef("y", "y", KEY_Y, superscript = "*"),
        KeyDef("x", "x", KEY_X, superscript = "\""),
        KeyDef("c", "c", KEY_C, superscript = "'"),
        KeyDef("v", "v", KEY_V, superscript = ":"),
        KeyDef("b", "b", KEY_B, superscript = ";"),
        KeyDef("n", "n", KEY_N, superscript = "!"),
        KeyDef("m", "m", KEY_M, superscript = "?"),
        KeyDef("bksp", "⌫", KEY_BACKSPACE, widthWeight = 1.3f),
    )

private fun bottomRowQwerty(): List<KeyDef> =
    listOf(
        KeyDef("lshift", "Shift", KEY_LEFTSHIFT, widthWeight = 1.3f, type = KeyType.MODIFIER),
        KeyDef("z", "z", KEY_Z, superscript = "*"),
        KeyDef("x", "x", KEY_X, superscript = "\""),
        KeyDef("c", "c", KEY_C, superscript = "'"),
        KeyDef("v", "v", KEY_V, superscript = ":"),
        KeyDef("b", "b", KEY_B, superscript = ";"),
        KeyDef("n", "n", KEY_N, superscript = "!"),
        KeyDef("m", "m", KEY_M, superscript = "?"),
        KeyDef("bksp", "⌫", KEY_BACKSPACE, widthWeight = 1.3f),
    )

private fun bottomRowAzerty(): List<KeyDef> =
    listOf(
        KeyDef("lshift", "Shift", KEY_LEFTSHIFT, widthWeight = 1.3f, type = KeyType.MODIFIER),
        KeyDef("w", "w", KEY_W, superscript = "*"),
        KeyDef("x", "x", KEY_X, superscript = "\""),
        KeyDef("c", "c", KEY_C, superscript = "'"),
        KeyDef("v", "v", KEY_V, superscript = ":"),
        KeyDef("b", "b", KEY_B, superscript = ";"),
        KeyDef("n", "n", KEY_N, superscript = "!"),
        KeyDef("question_azerty", "?", KEY_SLASH, autoModifiers = listOf(KEY_LEFTSHIFT), superscript = "?"),
        KeyDef("bksp", "⌫", KEY_BACKSPACE, widthWeight = 1.3f),
    )

private fun bottomBarRow(): List<KeyDef> =
    listOf(
        KeyDef("mode_switch", "?123", 0, widthWeight = 1.4f),
        KeyDef("comma", ",", KEY_COMMA, widthWeight = 1.0f),
        KeyDef("space", " ", KEY_SPACE, widthWeight = 5.5f),
        KeyDef("dot", ".", KEY_DOT, widthWeight = 1.0f),
        KeyDef("enter", "Enter", KEY_ENTER, widthWeight = 1.4f),
    )

private fun symbols1Layout(): List<List<KeyDef>> =
    listOf(
        listOf(
            KeyDef("1", "1", KEY_1),
            KeyDef("2", "2", KEY_2),
            KeyDef("3", "3", KEY_3),
            KeyDef("4", "4", KEY_4),
            KeyDef("5", "5", KEY_5),
            KeyDef("6", "6", KEY_6),
            KeyDef("7", "7", KEY_7),
            KeyDef("8", "8", KEY_8),
            KeyDef("9", "9", KEY_9),
            KeyDef("0", "0", KEY_0),
        ),
        listOf(
            KeyDef("at", "@", KEY_2, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("hash", "#", KEY_3, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("underscore", "_", KEY_MINUS, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("ampersand", "&", KEY_7, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("hyphen", "-", KEY_MINUS),
            KeyDef("plus", "+", KEY_EQUAL, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("lparen", "(", KEY_9, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("rparen", ")", KEY_0, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("slash", "/", KEY_SLASH),
        ),
        listOf(
            KeyDef("mode_switch_2", "", 0, widthWeight = 1.3f),
            KeyDef("asterisk", "*", KEY_8, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("quote", "\"", KEY_APOSTROPHE, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("singlequote", "'", KEY_APOSTROPHE),
            KeyDef("colon", ":", KEY_SEMICOLON, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("semicolon", ";", KEY_SEMICOLON),
            KeyDef("excl", "!", KEY_1, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("question", "?", KEY_SLASH, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("bksp", "⌫", KEY_BACKSPACE, widthWeight = 1.3f),
        ),
        listOf(
            KeyDef("mode_switch_abc", "ABC", 0, widthWeight = 1.4f),
            KeyDef("comma", ",", KEY_COMMA, widthWeight = 1.0f),
            KeyDef("mode_switch_1234", "1234", 0, widthWeight = 1.0f),
            KeyDef("space", " ", KEY_SPACE, widthWeight = 4.5f),
            KeyDef("dot", ".", KEY_DOT, widthWeight = 1.0f),
            KeyDef("enter", "Enter", KEY_ENTER, widthWeight = 1.4f),
        ),
    )

private fun symbols2Layout(): List<List<KeyDef>> =
    listOf(
        listOf(
            KeyDef("tilde", "~", KEY_GRAVE, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("backtick", "`", KEY_GRAVE),
            KeyDef("pipe", "|", KEY_BACKSLASH, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("bullet", "•", KEY_8, autoModifiers = listOf(KEY_LEFTSHIFT, KEY_RIGHTALT)),
            KeyDef("root", "√", 0),
            KeyDef("pi", "π", 0),
            KeyDef("div", "÷", KEY_SLASH),
            KeyDef("mul", "×", KEY_8, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("section", "§", 0),
            KeyDef("delta", "∆", 0),
        ),
        listOf(
            KeyDef("pound", "£", KEY_3, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("yen", "¥", 0),
            KeyDef("dollar", "$", KEY_4, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("cent", "¢", 0),
            KeyDef("caret", "^", KEY_6, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("deg", "°", 0),
            KeyDef("equal", "=", KEY_EQUAL),
            KeyDef("lbrace", "{", KEY_LEFTBRACE, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("rbrace", "}", KEY_RIGHTBRACE, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("bslash", "\\", KEY_BACKSLASH),
        ),
        listOf(
            KeyDef("mode_switch_1", "?123", 0, widthWeight = 1.3f),
            KeyDef("percent", "%", KEY_5, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("copyright", "©", 0),
            KeyDef("registered", "®", 0),
            KeyDef("trademark", "™", 0),
            KeyDef("checkmark", "✓", 0),
            KeyDef("lbracket", "[", KEY_LEFTBRACE),
            KeyDef("rbracket", "]", KEY_RIGHTBRACE),
            KeyDef("bksp", "⌫", KEY_BACKSPACE, widthWeight = 1.3f),
        ),
        listOf(
            KeyDef("mode_switch_abc", "ABC", 0, widthWeight = 1.4f),
            KeyDef("less", "<", KEY_COMMA, widthWeight = 1.0f, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("mode_switch_1234", "1234", 0, widthWeight = 1.0f),
            KeyDef("space", " ", KEY_SPACE, widthWeight = 4.5f),
            KeyDef("greater", ">", KEY_DOT, widthWeight = 1.0f, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("enter", "Enter", KEY_ENTER, widthWeight = 1.4f),
        ),
    )

private fun numericLayout(): List<List<KeyDef>> =
    listOf(
        // operator column keys + row 1 keys
        listOf(
            KeyDef("plus", "+", KEY_EQUAL, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("num_1", "1", KEY_1, widthWeight = 2.0f),
            KeyDef("num_2", "2", KEY_2, widthWeight = 2.0f),
            KeyDef("num_3", "3", KEY_3, widthWeight = 2.0f),
            KeyDef("percent", "%", KEY_5, widthWeight = 1.3f, autoModifiers = listOf(KEY_LEFTSHIFT)),
        ),
        // operators column minus + row 2 keys
        listOf(
            KeyDef("minus", "-", KEY_MINUS),
            KeyDef("num_4", "4", KEY_4, widthWeight = 2.0f),
            KeyDef("num_5", "5", KEY_5, widthWeight = 2.0f),
            KeyDef("num_6", "6", KEY_6, widthWeight = 2.0f),
            KeyDef("space_num", "␣", KEY_SPACE, widthWeight = 1.3f),
        ),
        // operators column asterisk + row 3 keys
        listOf(
            KeyDef("asterisk", "*", KEY_8, autoModifiers = listOf(KEY_LEFTSHIFT)),
            KeyDef("num_7", "7", KEY_7, widthWeight = 2.0f),
            KeyDef("num_8", "8", KEY_8, widthWeight = 2.0f),
            KeyDef("num_9", "9", KEY_9, widthWeight = 2.0f),
            KeyDef("bksp", "⌫", KEY_BACKSPACE, widthWeight = 1.3f),
        ),
        // operators column slash + row 4 bottom row keys
        listOf(
            KeyDef("slash", "/", KEY_SLASH),
            KeyDef("mode_switch_abc", "ABC", 0, widthWeight = 1.3f),
            KeyDef("comma", ",", KEY_COMMA, widthWeight = 1.0f),
            KeyDef("mode_switch", "!?#", 0, widthWeight = 1.0f),
            KeyDef("num_0", "0", KEY_0, widthWeight = 2.0f),
            KeyDef("equal", "=", KEY_EQUAL, widthWeight = 1.0f),
            KeyDef("dot", ".", KEY_DOT, widthWeight = 1.0f),
            KeyDef("enter", "Enter", KEY_ENTER, widthWeight = 1.3f),
        ),
    )

// ---------------------------------------------------------------------------
// Layout traversal helper
// ---------------------------------------------------------------------------

fun findKeyInLayout(
    layout: List<List<KeyDef>>,
    id: String,
): KeyDef? {
    for (row in layout) {
        for (key in row) {
            if (key.id == id) return key
        }
    }
    return null
}
