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

    private val _pendingProfilePackage = MutableStateFlow<String?>(null)
    val pendingProfilePackage: StateFlow<String?> = _pendingProfilePackage.asStateFlow()

    private val _appearanceDraft = MutableStateFlow<PadLayout?>(null)
    val appearanceDraft: StateFlow<PadLayout?> = _appearanceDraft.asStateFlow()

    private val _savedFocusKeysByDepth = MutableStateFlow<Map<Int, Any>>(emptyMap())
    val savedFocusKeysByDepth: StateFlow<Map<Int, Any>> = _savedFocusKeysByDepth.asStateFlow()

    fun selectSection(section: EditorSection) {
        AppLog.d(TAG, "selectSection: section=$section current=${_selectedSection.value}")
        if (_selectedSection.value != section) {
            _selectedSection.value = section
            _subPageStack.value = emptyList()
            _savedFocusKeysByDepth.value = emptyMap()
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

    fun setPendingProfilePackage(packageName: String?) {
        _pendingProfilePackage.value = packageName
    }

    fun setAppearanceDraft(layout: PadLayout?) {
        _appearanceDraft.value = layout
    }

    /**
     * Updates an in-flight draft macro across the subpage stack to keep steps and properties
     * in sync across modal suspensions and remounts.
     */
    fun updateCurrentMacroTimelineDraft(updatedDraft: Macro) {
        AppLog.d(TAG, "updateCurrentMacroTimelineDraft: macroId=${updatedDraft.id} steps=${updatedDraft.steps.size}")
        _subPageStack.value =
            _subPageStack.value.map { subPage ->
                when (subPage) {
                    is MacroPadSubPage.MacroTimeline -> {
                        if (subPage.macroId == updatedDraft.id || subPage.draftMacro?.id == updatedDraft.id) {
                            subPage.copy(draftMacro = updatedDraft)
                        } else {
                            subPage
                        }
                    }

                    is MacroPadSubPage.ManualMacroSteps -> {
                        if (subPage.macroId == updatedDraft.id || subPage.draftMacro?.id == updatedDraft.id) {
                            subPage.copy(draftMacro = updatedDraft)
                        } else {
                            subPage
                        }
                    }

                    else -> {
                        subPage
                    }
                }
            }
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
        _pendingProfilePackage.value = null
        _appearanceDraft.value = null
        _savedFocusKeysByDepth.value = emptyMap()
    }

    /**
     * Applies an incoming [PrimaryModalPayload] deep link into the navigation state.
     */
    fun applyPrimaryModalPayload(
        payload: PrimaryModalPayload?,
        onSetActiveProfileId: (String) -> Unit = { MacroPadState.setActiveProfileId(it) },
        onSetSelectedButtonId: (String?) -> Unit = { MacroPadState.setSelectedButtonId(it) },
    ) {
        if (payload == null) return
        AppLog.i(TAG, "applyPrimaryModalPayload: payload=${payload::class.simpleName}")
        when (payload) {
            is PrimaryModalPayload.MacroPad -> {
                val profId = payload.profileId
                val layId = payload.layoutId
                val macId = payload.macroId
                if (profId != null) {
                    _selectedSection.value = payload.section
                    onSetActiveProfileId(profId)
                    _subPageStack.value = listOf(MacroPadSubPage.EditProfile(profId))
                } else if (layId != null) {
                    _selectedSection.value = EditorSection.LAYOUTS
                    _subPageStack.value = listOf(MacroPadSubPage.LayoutAppearance(layId))
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

            is PrimaryModalPayload.LayoutSettings -> {
                _selectedSection.value = EditorSection.LAYOUTS
                _subPageStack.value = listOf(MacroPadSubPage.LayoutAppearance(payload.layoutId))
            }

            is PrimaryModalPayload.ProfileSettings -> {
                _selectedSection.value = EditorSection.PROFILES
                val profId = payload.profileId
                onSetActiveProfileId(profId)
                _subPageStack.value = listOf(MacroPadSubPage.EditProfile(profId))
            }

            is PrimaryModalPayload.MacroTimeline -> {
                _selectedSection.value = EditorSection.MACROS
                val macId = payload.macroId
                if (macId != null) {
                    _subPageStack.value = listOf(MacroPadSubPage.MacroTimeline(macId))
                }
                _macroTimelineFocusStepIndex.value = payload.focusStepIndex
            }

            is PrimaryModalPayload.ButtonInspector -> {
                _selectedSection.value = EditorSection.BUTTONS
                onSetSelectedButtonId(payload.buttonId)
                _subPageStack.value = listOf(MacroPadSubPage.EditButtonPositions)
            }

            else -> {}
        }
    }
}
