package com.stormpanda.megingiard.keyboard

data class PopupState(
    val keyDef: KeyDef,
    val options: List<String>,
    val selectedIndex: Int,
    val keyBounds: KeyBounds,
    val isLongPress: Boolean,
    val pointerId: Long,
)
