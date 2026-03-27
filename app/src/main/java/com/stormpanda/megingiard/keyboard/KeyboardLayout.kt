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
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHTALT
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHTBRACE
import com.stormpanda.megingiard.keyboard.LinuxKeycodes.KEY_RIGHTCTRL
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
    KeyDef("grave", "`",  KEY_GRAVE,     widthWeight = 1.0f),
    KeyDef("1",     "1",  KEY_1,         widthWeight = 1.0f),
    KeyDef("2",     "2",  KEY_2,         widthWeight = 1.0f),
    KeyDef("3",     "3",  KEY_3,         widthWeight = 1.0f),
    KeyDef("4",     "4",  KEY_4,         widthWeight = 1.0f),
    KeyDef("5",     "5",  KEY_5,         widthWeight = 1.0f),
    KeyDef("6",     "6",  KEY_6,         widthWeight = 1.0f),
    KeyDef("7",     "7",  KEY_7,         widthWeight = 1.0f),
    KeyDef("8",     "8",  KEY_8,         widthWeight = 1.0f),
    KeyDef("9",     "9",  KEY_9,         widthWeight = 1.0f),
    KeyDef("0",     "0",  KEY_0,         widthWeight = 1.0f),
    KeyDef("minus", "-",  KEY_MINUS,     widthWeight = 1.0f),
    KeyDef("equal", "=",  KEY_EQUAL,     widthWeight = 1.0f),
    KeyDef("bksp",  "⌫",  KEY_BACKSPACE, widthWeight = 2.0f),
)

private fun bottomBarRow(): List<KeyDef> = listOf(
    KeyDef("lctrl",  "Ctrl",  KEY_LEFTCTRL,  widthWeight = 1.3f, type = KeyType.MODIFIER),
    KeyDef("lmeta",  "Win",   KEY_LEFTMETA,  widthWeight = 1.2f, type = KeyType.MODIFIER),
    KeyDef("lalt",   "Alt",   KEY_LEFTALT,   widthWeight = 1.2f, type = KeyType.MODIFIER),
    KeyDef("space",  " ",     KEY_SPACE,     widthWeight = 5.5f),
    KeyDef("ralt",   "AltGr", KEY_RIGHTALT,  widthWeight = 1.3f, type = KeyType.MODIFIER),
    KeyDef("fn",     "Fn",    0,             widthWeight = 1.0f, type = KeyType.MODIFIER),
    KeyDef("rctrl",  "Ctrl",  KEY_RIGHTCTRL, widthWeight = 1.3f, type = KeyType.MODIFIER),
    KeyDef("larrow", "←",     KEY_LEFT,      widthWeight = 1.0f),
    KeyDef("darrow", "↓",     KEY_DOWN,      widthWeight = 1.0f),
    KeyDef("rarrow", "→",     KEY_RIGHT,     widthWeight = 1.0f),
)

// ---------------------------------------------------------------------------
// QWERTZ-specific rows
// ---------------------------------------------------------------------------

private fun topRowQwertz(): List<KeyDef> = listOf(
    KeyDef("tab", "Tab",  KEY_TAB,        widthWeight = 1.5f),
    KeyDef("q",   "Q",    KEY_Q,          widthWeight = 1.0f),
    KeyDef("w",   "W",    KEY_W,          widthWeight = 1.0f),
    KeyDef("e",   "E",    KEY_E,          widthWeight = 1.0f),
    KeyDef("r",   "R",    KEY_R,          widthWeight = 1.0f),
    KeyDef("t",   "T",    KEY_T,          widthWeight = 1.0f),
    KeyDef("z",   "Z",    KEY_Y,          widthWeight = 1.0f), // Z on label, Y keycode
    KeyDef("u",   "U",    KEY_U,          widthWeight = 1.0f),
    KeyDef("i",   "I",    KEY_I,          widthWeight = 1.0f),
    KeyDef("o",   "O",    KEY_O,          widthWeight = 1.0f),
    KeyDef("p",   "P",    KEY_P,          widthWeight = 1.0f),
    KeyDef("lbrc","[",    KEY_LEFTBRACE,  widthWeight = 1.0f),
    KeyDef("rbrc","]",    KEY_RIGHTBRACE, widthWeight = 1.0f),
    KeyDef("bsls","\\",   KEY_BACKSLASH,  widthWeight = 1.5f),
)

private fun homeRowQwertz(): List<KeyDef> = listOf(
    KeyDef("caps",  "Caps", KEY_CAPSLOCK, widthWeight = 1.8f, type = KeyType.MODIFIER),
    KeyDef("a",     "A",    KEY_A,        widthWeight = 1.0f),
    KeyDef("s",     "S",    KEY_S,        widthWeight = 1.0f),
    KeyDef("d",     "D",    KEY_D,        widthWeight = 1.0f),
    KeyDef("f",     "F",    KEY_F,        widthWeight = 1.0f),
    KeyDef("g",     "G",    KEY_G,        widthWeight = 1.0f),
    KeyDef("tp",    "●",    0,            widthWeight = 0.6f, type = KeyType.TRACKPOINT),
    KeyDef("h",     "H",    KEY_H,        widthWeight = 1.0f),
    KeyDef("j",     "J",    KEY_J,        widthWeight = 1.0f),
    KeyDef("k",     "K",    KEY_K,        widthWeight = 1.0f),
    KeyDef("l",     "L",    KEY_L,        widthWeight = 1.0f),
    KeyDef("semi",  ";",    KEY_SEMICOLON, widthWeight = 1.0f),
    KeyDef("apos",  "'",    KEY_APOSTROPHE, widthWeight = 1.0f),
    KeyDef("enter", "Enter", KEY_ENTER,   widthWeight = 2.2f),
)

private fun bottomRowQwertz(): List<KeyDef> = listOf(
    KeyDef("lshift", "Shift", KEY_LEFTSHIFT,  widthWeight = 2.3f, type = KeyType.MODIFIER),
    KeyDef("y",      "Y",     KEY_Z,          widthWeight = 1.0f), // Y on label, Z keycode
    KeyDef("x",      "X",     KEY_X,          widthWeight = 1.0f),
    KeyDef("c",      "C",     KEY_C,          widthWeight = 1.0f),
    KeyDef("v",      "V",     KEY_V,          widthWeight = 1.0f),
    KeyDef("b",      "B",     KEY_B,          widthWeight = 1.0f),
    KeyDef("n",      "N",     KEY_N,          widthWeight = 1.0f),
    KeyDef("m",      "M",     KEY_M,          widthWeight = 1.0f),
    KeyDef("comma",  ",",     KEY_COMMA,      widthWeight = 1.0f),
    KeyDef("dot",    ".",     KEY_DOT,        widthWeight = 1.0f),
    KeyDef("slash",  "/",     KEY_SLASH,      widthWeight = 1.0f),
    KeyDef("rshift", "Shift", KEY_RIGHTSHIFT, widthWeight = 1.7f, type = KeyType.MODIFIER),
    KeyDef("uarrow", "↑",     KEY_UP,         widthWeight = 1.0f),
)

// ---------------------------------------------------------------------------
// QWERTY-specific rows (only Z/Y swapped)
// ---------------------------------------------------------------------------

private fun topRowQwerty(): List<KeyDef> = listOf(
    KeyDef("tab", "Tab",  KEY_TAB,        widthWeight = 1.5f),
    KeyDef("q",   "Q",    KEY_Q,          widthWeight = 1.0f),
    KeyDef("w",   "W",    KEY_W,          widthWeight = 1.0f),
    KeyDef("e",   "E",    KEY_E,          widthWeight = 1.0f),
    KeyDef("r",   "R",    KEY_R,          widthWeight = 1.0f),
    KeyDef("t",   "T",    KEY_T,          widthWeight = 1.0f),
    KeyDef("y",   "Y",    KEY_Y,          widthWeight = 1.0f),
    KeyDef("u",   "U",    KEY_U,          widthWeight = 1.0f),
    KeyDef("i",   "I",    KEY_I,          widthWeight = 1.0f),
    KeyDef("o",   "O",    KEY_O,          widthWeight = 1.0f),
    KeyDef("p",   "P",    KEY_P,          widthWeight = 1.0f),
    KeyDef("lbrc","[",    KEY_LEFTBRACE,  widthWeight = 1.0f),
    KeyDef("rbrc","]",    KEY_RIGHTBRACE, widthWeight = 1.0f),
    KeyDef("bsls","\\",   KEY_BACKSLASH,  widthWeight = 1.5f),
)

private fun homeRowQwerty(): List<KeyDef> = listOf(
    KeyDef("caps",  "Caps", KEY_CAPSLOCK, widthWeight = 1.8f, type = KeyType.MODIFIER),
    KeyDef("a",     "A",    KEY_A,        widthWeight = 1.0f),
    KeyDef("s",     "S",    KEY_S,        widthWeight = 1.0f),
    KeyDef("d",     "D",    KEY_D,        widthWeight = 1.0f),
    KeyDef("f",     "F",    KEY_F,        widthWeight = 1.0f),
    KeyDef("g",     "G",    KEY_G,        widthWeight = 1.0f),
    KeyDef("tp",    "●",    0,            widthWeight = 0.6f, type = KeyType.TRACKPOINT),
    KeyDef("h",     "H",    KEY_H,        widthWeight = 1.0f),
    KeyDef("j",     "J",    KEY_J,        widthWeight = 1.0f),
    KeyDef("k",     "K",    KEY_K,        widthWeight = 1.0f),
    KeyDef("l",     "L",    KEY_L,        widthWeight = 1.0f),
    KeyDef("semi",  ";",    KEY_SEMICOLON, widthWeight = 1.0f),
    KeyDef("apos",  "'",    KEY_APOSTROPHE, widthWeight = 1.0f),
    KeyDef("enter", "Enter", KEY_ENTER,   widthWeight = 2.2f),
)

private fun bottomRowQwerty(): List<KeyDef> = listOf(
    KeyDef("lshift", "Shift", KEY_LEFTSHIFT,  widthWeight = 2.3f, type = KeyType.MODIFIER),
    KeyDef("z",      "Z",     KEY_Z,          widthWeight = 1.0f),
    KeyDef("x",      "X",     KEY_X,          widthWeight = 1.0f),
    KeyDef("c",      "C",     KEY_C,          widthWeight = 1.0f),
    KeyDef("v",      "V",     KEY_V,          widthWeight = 1.0f),
    KeyDef("b",      "B",     KEY_B,          widthWeight = 1.0f),
    KeyDef("n",      "N",     KEY_N,          widthWeight = 1.0f),
    KeyDef("m",      "M",     KEY_M,          widthWeight = 1.0f),
    KeyDef("comma",  ",",     KEY_COMMA,      widthWeight = 1.0f),
    KeyDef("dot",    ".",     KEY_DOT,        widthWeight = 1.0f),
    KeyDef("slash",  "/",     KEY_SLASH,      widthWeight = 1.0f),
    KeyDef("rshift", "Shift", KEY_RIGHTSHIFT, widthWeight = 1.7f, type = KeyType.MODIFIER),
    KeyDef("uarrow", "↑",     KEY_UP,         widthWeight = 1.0f),
)
