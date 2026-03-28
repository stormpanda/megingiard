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
)

enum class KeyType { NORMAL, MODIFIER, TRACKPOINT }

// ---------------------------------------------------------------------------
// Layout factories
// ---------------------------------------------------------------------------

fun qwertzLayout(): List<List<KeyDef>> = listOf(
    fRow(),
    numberRow(),
    topRowQwertz(),
    homeRowQwertz(),
    bottomRowQwertz(),
    bottomBarRow(),
)

fun qwertyLayout(): List<List<KeyDef>> = listOf(
    fRow(),
    numberRow(),
    topRowQwerty(),
    homeRowQwerty(),
    bottomRowQwerty(),
    bottomBarRow(),
)

// ---------------------------------------------------------------------------
// Shared rows
// ---------------------------------------------------------------------------

private fun fRow(): List<KeyDef> = listOf(
    KeyDef("esc",   "Esc",    KEY_ESC,   widthWeight = 1.0f),
    KeyDef("f1",    "F1",     KEY_F1,    widthWeight = 1.0f),
    KeyDef("f2",    "F2",     KEY_F2,    widthWeight = 1.0f),
    KeyDef("f3",    "F3",     KEY_F3,    widthWeight = 1.0f),
    KeyDef("f4",    "F4",     KEY_F4,    widthWeight = 1.0f),
    KeyDef("f5",    "F5",     KEY_F5,    widthWeight = 1.0f),
    KeyDef("f6",    "F6",     KEY_F6,    widthWeight = 1.0f),
    KeyDef("f7",    "F7",     KEY_F7,    widthWeight = 1.0f),
    KeyDef("f8",    "F8",     KEY_F8,    widthWeight = 1.0f),
    KeyDef("f9",    "F9",     KEY_F9,    widthWeight = 1.0f),
    KeyDef("f10",   "F10",    KEY_F10,   widthWeight = 1.0f),
    KeyDef("f11",   "F11",    KEY_F11,   widthWeight = 1.0f),
    KeyDef("f12",   "F12",    KEY_F12,   widthWeight = 1.0f),
    KeyDef("prtsc", "PrtSc",  KEY_SYSRQ, widthWeight = 1.0f),
    KeyDef("ins",   "Ins",    KEY_INSERT, widthWeight = 1.0f),
    KeyDef("del",   "Del",    KEY_DELETE, widthWeight = 1.0f),
)

private fun numberRow(): List<KeyDef> = listOf(
    KeyDef("grave", "`",  KEY_GRAVE,     shiftLabel = "~"),
    KeyDef("1",     "1",  KEY_1,         shiftLabel = "!"),
    KeyDef("2",     "2",  KEY_2,         shiftLabel = "@"),
    KeyDef("3",     "3",  KEY_3,         shiftLabel = "#"),
    KeyDef("4",     "4",  KEY_4,         shiftLabel = "\$"),
    KeyDef("5",     "5",  KEY_5,         shiftLabel = "%"),
    KeyDef("6",     "6",  KEY_6,         shiftLabel = "^"),
    KeyDef("7",     "7",  KEY_7,         shiftLabel = "&"),
    KeyDef("8",     "8",  KEY_8,         shiftLabel = "*"),
    KeyDef("9",     "9",  KEY_9,         shiftLabel = "("),
    KeyDef("0",     "0",  KEY_0,         shiftLabel = ")"),
    KeyDef("minus", "-",  KEY_MINUS,     shiftLabel = "_"),
    KeyDef("equal", "=",  KEY_EQUAL,     shiftLabel = "+"),
    KeyDef("bksp",  "⌫",  KEY_BACKSPACE, widthWeight = 2.0f),
)

private fun bottomBarRow(): List<KeyDef> = listOf(
    KeyDef("lctrl",  "Ctrl",  KEY_LEFTCTRL,  widthWeight = 1.3f, type = KeyType.MODIFIER),
    KeyDef("lmeta",  "Win",   KEY_LEFTMETA,  widthWeight = 1.2f, type = KeyType.MODIFIER),
    KeyDef("lalt",   "Alt",   KEY_LEFTALT,   widthWeight = 1.2f, type = KeyType.MODIFIER),
    KeyDef("space",  " ",     KEY_SPACE,     widthWeight = 5.5f),
    KeyDef("ralt",   "AltGr", KEY_RIGHTALT,  widthWeight = 1.3f, type = KeyType.MODIFIER),
    KeyDef("larrow", "←",     KEY_LEFT,      widthWeight = 1.7f),
    KeyDef("darrow", "↓",     KEY_DOWN,      widthWeight = 1.7f),
    KeyDef("rarrow", "→",     KEY_RIGHT,     widthWeight = 1.7f),
)

// ---------------------------------------------------------------------------
// QWERTZ-specific rows
// ---------------------------------------------------------------------------

private fun topRowQwertz(): List<KeyDef> = listOf(
    KeyDef("tab", "Tab",  KEY_TAB,        widthWeight = 1.5f),
    KeyDef("q",   "q",    KEY_Q,          shiftLabel = "Q"),
    KeyDef("w",   "w",    KEY_W,          shiftLabel = "W"),
    KeyDef("e",   "e",    KEY_E,          shiftLabel = "E"),
    KeyDef("r",   "r",    KEY_R,          shiftLabel = "R"),
    KeyDef("t",   "t",    KEY_T,          shiftLabel = "T"),
    KeyDef("z",   "z",    KEY_Z,          shiftLabel = "Z"), // QWERTZ: Z visual position in top row, but KEY_Z for correct output
    KeyDef("u",   "u",    KEY_U,          shiftLabel = "U"),
    KeyDef("i",   "i",    KEY_I,          shiftLabel = "I"),
    KeyDef("o",   "o",    KEY_O,          shiftLabel = "O"),
    KeyDef("p",   "p",    KEY_P,          shiftLabel = "P"),
    KeyDef("lbrc","[",    KEY_LEFTBRACE,  shiftLabel = "{"),
    KeyDef("rbrc","]",    KEY_RIGHTBRACE, shiftLabel = "}"),
    KeyDef("bsls","\\",   KEY_BACKSLASH,  widthWeight = 1.5f, shiftLabel = "|"),
)

private fun homeRowQwertz(): List<KeyDef> = listOf(
    KeyDef("caps",  "Caps", KEY_CAPSLOCK, widthWeight = 1.2f, type = KeyType.MODIFIER),
    KeyDef("a",     "a",    KEY_A,        shiftLabel = "A"),
    KeyDef("s",     "s",    KEY_S,        shiftLabel = "S"),
    KeyDef("d",     "d",    KEY_D,        shiftLabel = "D"),
    KeyDef("f",     "f",    KEY_F,        shiftLabel = "F"),
    KeyDef("g",     "g",    KEY_G,        shiftLabel = "G"),
    KeyDef("tp",    "●",    0,            widthWeight = 1.93f, type = KeyType.TRACKPOINT),
    KeyDef("h",     "h",    KEY_H,        shiftLabel = "H"),
    KeyDef("j",     "j",    KEY_J,        shiftLabel = "J"),
    KeyDef("k",     "k",    KEY_K,        shiftLabel = "K"),
    KeyDef("l",     "l",    KEY_L,        shiftLabel = "L"),
    KeyDef("semi",  ";",    KEY_SEMICOLON, shiftLabel = ":"),
    KeyDef("apos",  "'",    KEY_APOSTROPHE, shiftLabel = "\""),
    KeyDef("enter", "Enter", KEY_ENTER,   widthWeight = 1.47f),
)

private fun bottomRowQwertz(): List<KeyDef> = listOf(
    KeyDef("lshift", "Shift", KEY_LEFTSHIFT,  widthWeight = 2.3f, type = KeyType.MODIFIER),
    KeyDef("y",      "y",     KEY_Y,          shiftLabel = "Y"), // QWERTZ: Y visual position in bottom row, but KEY_Y for correct output
    KeyDef("x",      "x",     KEY_X,          shiftLabel = "X"),
    KeyDef("c",      "c",     KEY_C,          shiftLabel = "C"),
    KeyDef("v",      "v",     KEY_V,          shiftLabel = "V"),
    KeyDef("b",      "b",     KEY_B,          shiftLabel = "B"),
    KeyDef("n",      "n",     KEY_N,          shiftLabel = "N"),
    KeyDef("m",      "m",     KEY_M,          shiftLabel = "M"),
    KeyDef("comma",  ",",     KEY_COMMA,      shiftLabel = "<"),
    KeyDef("dot",    ".",     KEY_DOT,        shiftLabel = ">"),
    KeyDef("slash",  "/",     KEY_SLASH,      shiftLabel = "?"),
    KeyDef("uarrow", "↑",     KEY_UP,         widthWeight = 1.7f), // swapped with rshift, wider
    KeyDef("rshift", "Shift", KEY_RIGHTSHIFT, widthWeight = 1.5f, type = KeyType.MODIFIER),
)

// ---------------------------------------------------------------------------
// QWERTY-specific rows (only Z/Y swapped)
// ---------------------------------------------------------------------------

private fun topRowQwerty(): List<KeyDef> = listOf(
    KeyDef("tab", "Tab",  KEY_TAB,        widthWeight = 1.5f),
    KeyDef("q",   "q",    KEY_Q,          shiftLabel = "Q"),
    KeyDef("w",   "w",    KEY_W,          shiftLabel = "W"),
    KeyDef("e",   "e",    KEY_E,          shiftLabel = "E"),
    KeyDef("r",   "r",    KEY_R,          shiftLabel = "R"),
    KeyDef("t",   "t",    KEY_T,          shiftLabel = "T"),
    KeyDef("y",   "y",    KEY_Y,          shiftLabel = "Y"),
    KeyDef("u",   "u",    KEY_U,          shiftLabel = "U"),
    KeyDef("i",   "i",    KEY_I,          shiftLabel = "I"),
    KeyDef("o",   "o",    KEY_O,          shiftLabel = "O"),
    KeyDef("p",   "p",    KEY_P,          shiftLabel = "P"),
    KeyDef("lbrc","[",    KEY_LEFTBRACE,  shiftLabel = "{"),
    KeyDef("rbrc","]",    KEY_RIGHTBRACE, shiftLabel = "}"),
    KeyDef("bsls","\\",   KEY_BACKSLASH,  widthWeight = 1.5f, shiftLabel = "|"),
)

private fun homeRowQwerty(): List<KeyDef> = listOf(
    KeyDef("caps",  "Caps", KEY_CAPSLOCK, widthWeight = 1.2f, type = KeyType.MODIFIER),
    KeyDef("a",     "a",    KEY_A,        shiftLabel = "A"),
    KeyDef("s",     "s",    KEY_S,        shiftLabel = "S"),
    KeyDef("d",     "d",    KEY_D,        shiftLabel = "D"),
    KeyDef("f",     "f",    KEY_F,        shiftLabel = "F"),
    KeyDef("g",     "g",    KEY_G,        shiftLabel = "G"),
    KeyDef("tp",    "●",    0,            widthWeight = 1.93f, type = KeyType.TRACKPOINT),
    KeyDef("h",     "h",    KEY_H,        shiftLabel = "H"),
    KeyDef("j",     "j",    KEY_J,        shiftLabel = "J"),
    KeyDef("k",     "k",    KEY_K,        shiftLabel = "K"),
    KeyDef("l",     "l",    KEY_L,        shiftLabel = "L"),
    KeyDef("semi",  ";",    KEY_SEMICOLON, shiftLabel = ":"),
    KeyDef("apos",  "'",    KEY_APOSTROPHE, shiftLabel = "\""),
    KeyDef("enter", "Enter", KEY_ENTER,   widthWeight = 1.47f),
)

private fun bottomRowQwerty(): List<KeyDef> = listOf(
    KeyDef("lshift", "Shift", KEY_LEFTSHIFT,  widthWeight = 2.3f, type = KeyType.MODIFIER),
    KeyDef("z",      "z",     KEY_Z,          shiftLabel = "Z"),
    KeyDef("x",      "x",     KEY_X,          shiftLabel = "X"),
    KeyDef("c",      "c",     KEY_C,          shiftLabel = "C"),
    KeyDef("v",      "v",     KEY_V,          shiftLabel = "V"),
    KeyDef("b",      "b",     KEY_B,          shiftLabel = "B"),
    KeyDef("n",      "n",     KEY_N,          shiftLabel = "N"),
    KeyDef("m",      "m",     KEY_M,          shiftLabel = "M"),
    KeyDef("comma",  ",",     KEY_COMMA,      shiftLabel = "<"),
    KeyDef("dot",    ".",     KEY_DOT,        shiftLabel = ">"),
    KeyDef("slash",  "/",     KEY_SLASH,      shiftLabel = "?"),
    KeyDef("uarrow", "↑",     KEY_UP,         widthWeight = 1.7f), // swapped with rshift, wider
    KeyDef("rshift", "Shift", KEY_RIGHTSHIFT, widthWeight = 1.5f, type = KeyType.MODIFIER),
)
