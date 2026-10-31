package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ui.PrimaryModalPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MacroPadNavState"

/**
 * Singleton state holder for the MacroPad Editor's navigation hierarchy.
 *
 * Preserves the active [EditorSection] and [MacroPadSubPage] navigation stack in memory
 * across composable remounting and modal suspensions (e.g. during physical/touch macro recording
 * or screen capture overlays).
 */
internal object MacroPadNavState {
    private val _selectedSection = MutableStateFlow(EditorSection.QUICK_ACTIONS)
    val selectedSection: StateFlow<EditorSection> = _selectedSection.asStateFlow()

    private val _subPageStack = MutableStateFlow<List<MacroPadSubPage>>(emptyList())
    val subPageStack: StateFlow<List<MacroPadSubPage>> = _subPageStack.asStateFlow()

    private val _macroTimelineFocusStepIndex = MutableStateFlow<Int?>(null)
    val macroTimelineFocusStepIndex: StateFlow<Int?> = _macroTimelineFocusStepIndex.asStateFlow()

    private val _savedFocusKeysByDepth = MutableStateFlow<Map<Int, Any>>(emptyMap())
    val savedFocusKeysByDepth: StateFlow<Map<Int, Any>> = _savedFocusKeysByDepth.asStateFlow()

    private val _buttonDraft = MutableStateFlow<PadButton?>(null)
    val buttonDraft: StateFlow<PadButton?> = _buttonDraft.asStateFlow()

    private val _awaitingButtonTapRecording = MutableStateFlow(false)
    val awaitingButtonTapRecording: StateFlow<Boolean> = _awaitingButtonTapRecording.asStateFlow()

    private val _macroDraft = MutableStateFlow<Macro?>(null)
    val macroDraft: StateFlow<Macro?> = _macroDraft.asStateFlow()

    fun setButtonDraft(draft: PadButton?) {
        AppLog.d(TAG, "setButtonDraft: id=${draft?.id}")
        _buttonDraft.value = draft
        if (draft == null) {
            _awaitingButtonTapRecording.value = false
        }
    }

    fun setAwaitingButtonTapRecording(awaiting: Boolean) {
        AppLog.d(TAG, "setAwaitingButtonTapRecording: awaiting=$awaiting")
        _awaitingButtonTapRecording.value = awaiting
    }

    fun setMacroDraft(draft: Macro?) {
        AppLog.d(TAG, "setMacroDraft: id=${draft?.id}")
        _macroDraft.value = draft
    }

    fun selectSection(section: EditorSection) {
        AppLog.d(TAG, "selectSection: section=$section current=${_selectedSection.value}")
        if (_selectedSection.value != section) {
            _selectedSection.value = section
            _subPageStack.value = emptyList()
            _savedFocusKeysByDepth.value = emptyMap()
            _buttonDraft.value = null
            _awaitingButtonTapRecording.value = false
            _macroDraft.value = null
        }
    }

    fun push(subPage: MacroPadSubPage) {
        AppLog.d(TAG, "push: subPage=${subPage::class.simpleName}")
        _subPageStack.value = _subPageStack.value + subPage
    }

    fun pop(): Boolean {
        val currentStack = _subPageStack.value
        if (currentStack.isEmpty()) return false
        val popped = currentStack.last()
        AppLog.d(TAG, "pop: popped=${popped::class.simpleName}")
        _subPageStack.value = currentStack.dropLast(1)
        return true
    }

    fun setStack(stack: List<MacroPadSubPage>) {
        AppLog.d(TAG, "setStack: size=${stack.size}")
        _subPageStack.value = stack
    }

    fun setMacroTimelineFocusStepIndex(index: Int?) {
        _macroTimelineFocusStepIndex.value = index
    }

    fun recordFocusedKey(
        depth: Int,
        key: Any,
    ) {
        _savedFocusKeysByDepth.value = _savedFocusKeysByDepth.value + (depth to key)
    }

    fun removeFocusedKey(depth: Int) {
        _savedFocusKeysByDepth.value = _savedFocusKeysByDepth.value - depth
    }

    fun clearFocusedKeys(minDepth: Int = 0) {
        _savedFocusKeysByDepth.value = _savedFocusKeysByDepth.value.filterKeys { it < minDepth }
    }

    /**
     * Resets the navigation state back to default (Quick Actions deck, empty subpage stack).
     */
    fun reset() {
        AppLog.d(TAG, "reset")
        _selectedSection.value = EditorSection.QUICK_ACTIONS
        _subPageStack.value = emptyList()
        _macroTimelineFocusStepIndex.value = null
        _savedFocusKeysByDepth.value = emptyMap()
        _buttonDraft.value = null
        _awaitingButtonTapRecording.value = false
        _macroDraft.value = null
    }

    /**
     * Applies an incoming [PrimaryModalPayload] deep link into the navigation state.
     */
    fun applyPrimaryModalPayload(
        payload: PrimaryModalPayload?,
        onSetActiveProfileId: (String) -> Unit = { MacroPadState.setActiveProfileId(it) },
    ) {
        if (payload == null) return
        AppLog.i(TAG, "applyPrimaryModalPayload: payload=${payload::class.simpleName}")
        when (payload) {
            is PrimaryModalPayload.MacroPad -> {
                val profId = payload.profileId
                val layId = payload.layoutId
                val macId = payload.macroId
                if (payload.newProfile) {
                    _selectedSection.value = EditorSection.PROFILES
                    _subPageStack.value =
                        listOf(
                            MacroPadSubPage.NewProfile(
                                presetName = payload.presetProfileName,
                                association = payload.profileAssociation,
                            ),
                        )
                } else if (profId != null) {
                    _selectedSection.value = payload.section
                    onSetActiveProfileId(profId)
                    _subPageStack.value = listOf(MacroPadSubPage.EditProfile(profId))
                } else if (layId != null) {
                    _selectedSection.value = EditorSection.LAYOUTS
                    _subPageStack.value = listOf(MacroPadSubPage.EditLayout(layId))
                } else if (macId != null) {
                    _selectedSection.value = EditorSection.MACROS
                    _subPageStack.value = listOf(MacroPadSubPage.MacroTimeline(macId))
                } else if (payload.editPositions) {
                    _selectedSection.value = EditorSection.BUTTONS
                    _subPageStack.value = listOf(MacroPadSubPage.EditButtonPositions)
                } else if (_subPageStack.value.isEmpty()) {
                    _selectedSection.value = payload.section
                }
                _macroTimelineFocusStepIndex.value = payload.focusStepIndex
            }

            is PrimaryModalPayload.ProfileSettings -> {
                _selectedSection.value = EditorSection.PROFILES
                val profId = payload.profileId
                if (payload.isNewProfile || profId == null) {
                    _subPageStack.value =
                        listOf(
                            MacroPadSubPage.NewProfile(
                                presetName = payload.presetName,
                                association = payload.association,
                            ),
                        )
                } else {
                    onSetActiveProfileId(profId)
                    _subPageStack.value = listOf(MacroPadSubPage.EditProfile(profId))
                }
            }

            else -> {}
        }
    }
}
