package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.mirror.ScreenshotTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadActionDefaultsTest {
    private val macroCombo = Macro(id = "macro-1", name = "Hadouken")
    private val layoutCombat = PadLayout(id = "layout-1", name = "Combat")
    private val activeProfile =
        PadProfile(
            id = "profile-1",
            name = "RetroArch",
            layouts = listOf(layoutCombat),
            macros = listOf(macroCombo),
            activeLayoutId = "layout-1",
        )
    private val secondaryProfile =
        PadProfile(
            id = "profile-2",
            name = "Dolphin",
            layouts = listOf(PadLayout(id = "layout-2", name = "Wii")),
            activeLayoutId = "layout-2",
        )
    private val allProfiles = listOf(activeProfile, secondaryProfile)

    @Test
    fun `every non-null defaultIconName exists in ALL_ROUNDED_ICON_NAMES`() {
        val iconSet = ALL_ROUNDED_ICON_NAMES.toSet()
        val actionsToVerify =
            buildList {
                add(PadAction.LayoutNext)
                add(PadAction.LayoutPrevious)
                add(PadAction.ProfileSwitcher)
                add(PadAction.SwitchToHub)
                add(PadAction.MirrorPlayStop)
                add(PadAction.MirrorFreeze)
                add(PadAction.MirrorViewportEdit)
                add(PadAction.MirrorTouchProjection)
                add(PadAction.FullScreenMouse())
                add(PadAction.FullScreenKeyboard())
                add(PadAction.StopAllMacros)
                ScreenshotTarget.entries.forEach { add(PadAction.Screenshot(it)) }
                MediaControlAction.entries.forEach { add(PadAction.MediaControl(it)) }
                MouseButton.entries.forEach { add(PadAction.MouseButton(it)) }
                GamepadKeycodes.PRESETS.forEach { add(PadAction.GamepadButton(it.code, it.shortLabel)) }
                listOf(
                    LinuxKeycodes.KEY_ENTER,
                    LinuxKeycodes.KEY_BACKSPACE,
                    LinuxKeycodes.KEY_TAB,
                    LinuxKeycodes.KEY_SPACE,
                    LinuxKeycodes.KEY_CAPSLOCK,
                    LinuxKeycodes.KEY_UP,
                    LinuxKeycodes.KEY_DOWN,
                    LinuxKeycodes.KEY_LEFT,
                    LinuxKeycodes.KEY_RIGHT,
                    LinuxKeycodes.KEY_PAGEUP,
                    LinuxKeycodes.KEY_PAGEDOWN,
                ).forEach { add(PadAction.KeyboardKey(it, "Key")) }
            }

        for (action in actionsToVerify) {
            val icon = action.defaultIconName()
            if (icon != null) {
                assertTrue(
                    "Default icon '$icon' for $action is missing from ALL_ROUNDED_ICON_NAMES",
                    icon in iconSet,
                )
            }
        }
    }

    @Test
    fun `resolveDataDrivenDefaultLabel formats keys chords gamepad swap home and target names`() {
        assertEquals(
            "Space",
            PadAction
                .KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )
        assertEquals(
            "Ctrl L+Shift L+S",
            PadAction
                .KeyboardKey(
                    keycode = LinuxKeycodes.KEY_S,
                    label = "S",
                    modifiers = listOf(LinuxKeycodes.KEY_LEFTCTRL, LinuxKeycodes.KEY_LEFTSHIFT),
                ).resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )

        assertEquals(
            "A",
            PadAction
                .GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )
        assertEquals(
            "B",
            PadAction
                .GamepadButton(GamepadKeycodes.BTN_SOUTH, "A")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = true, homeButtonLabel = "Home"),
        )
        assertEquals(
            "Home",
            PadAction
                .GamepadButton(GamepadKeycodes.BTN_MODE, "🏠")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )
        assertEquals(
            "L1+R1",
            PadAction
                .GamepadButton(
                    btnCode = GamepadKeycodes.BTN_TL,
                    label = "L1",
                    extraBtnCodes = listOf(GamepadKeycodes.BTN_TR),
                ).resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )

        assertEquals(
            "Hadouken",
            PadAction
                .Macro("macro-1")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )
        assertNull(
            PadAction
                .Macro("missing")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )

        assertEquals(
            "Combat",
            PadAction
                .SwitchToLayout("layout-1")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )
        assertEquals(
            "Dolphin",
            PadAction
                .SwitchToProfile("profile-2")
                .resolveDataDrivenDefaultLabel(allProfiles, activeProfile, swapFaceButtons = false, homeButtonLabel = "Home"),
        )
    }

    @Test
    fun `withActionDefaultsResolved updates untouched default labels and icons across transitions`() {
        val initialAction = PadAction.MediaControl(MediaControlAction.PLAY_PAUSE)
        val initial =
            PadButton(
                id = "btn-1",
                label = "New Button",
                iconName = initialAction.defaultIconName(),
                posX = 0.5f,
                posY = 0.5f,
                action = initialAction,
            )

        val switchedMedia =
            initial.withActionDefaultsResolved(
                newAction = PadAction.MediaControl(MediaControlAction.NEXT_TRACK),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Play / Pause",
                newDefaultLabel = "Next Track",
            )
        assertEquals("Next Track", switchedMedia.label)
        assertEquals("skip_next", switchedMedia.iconName)

        // Switching from a default-icon action to a target-bound action clears default icon so target name renders
        val switchedToProfile =
            switchedMedia.withActionDefaultsResolved(
                newAction = PadAction.SwitchToProfile("profile-2"),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Next Track",
                newDefaultLabel = "Dolphin",
            )
        assertEquals("Dolphin", switchedToProfile.label)
        assertNull(switchedToProfile.iconName)
    }

    @Test
    fun `withActionDefaultsResolved clears icon on keyboard modifier chord and restores on single key`() {
        val singleSpace = PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space")
        val chordSpace = PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space", listOf(LinuxKeycodes.KEY_LEFTCTRL))

        val base =
            PadButton(
                id = "btn-space",
                label = "Space",
                iconName = "space_bar",
                posX = 0.5f,
                posY = 0.5f,
                action = singleSpace,
            )

        val withModifier =
            base.withActionDefaultsResolved(
                newAction = chordSpace,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Space",
                newDefaultLabel = "Ctrl L+Space",
            )
        assertEquals("Ctrl L+Space", withModifier.label)
        assertNull(withModifier.iconName)

        val modifierCleared =
            withModifier.withActionDefaultsResolved(
                newAction = singleSpace,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Ctrl L+Space",
                newDefaultLabel = "Space",
            )
        assertEquals("Space", modifierCleared.label)
        assertEquals("space_bar", modifierCleared.iconName)
    }

    @Test
    fun `withActionDefaultsResolved preserves custom user labels and custom or cleared icons`() {
        val playPause = PadAction.MediaControl(MediaControlAction.PLAY_PAUSE)
        val nextTrack = PadAction.MediaControl(MediaControlAction.NEXT_TRACK)

        // User typed custom label + picked custom icon "music_note"
        val customized =
            PadButton(
                id = "btn-custom",
                label = "My Tunes",
                iconName = "music_note",
                posX = 0.5f,
                posY = 0.5f,
                action = playPause,
            )
        val afterSwitch =
            customized.withActionDefaultsResolved(
                newAction = nextTrack,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Play / Pause",
                newDefaultLabel = "Next Track",
            )
        assertEquals("My Tunes", afterSwitch.label)
        assertEquals("music_note", afterSwitch.iconName)

        // User manually cleared the icon on Play/Pause (iconName = null != "play_pause")
        val clearedIcon = customized.copy(iconName = null)
        val afterClearedSwitch =
            clearedIcon.withActionDefaultsResolved(
                newAction = nextTrack,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Play / Pause",
                newDefaultLabel = "Next Track",
            )
        assertNull(afterClearedSwitch.iconName)

        // Switching to a fixed-face action (ScrollWheel) always forces iconName = null
        val toScroll =
            customized.withActionDefaultsResolved(
                newAction = PadAction.ScrollWheel,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Play / Pause",
                newDefaultLabel = "Scroll Wheel",
            )
        assertEquals("My Tunes", toScroll.label)
        assertNull(toScroll.iconName)
    }

    @Test
    fun `withActionDefaultsResolved applies design-canvas pixel sizing for ScrollWheel and TrackpointMove`() {
        val rpDuoCanvas = LayoutCanvasSpec(widthPx = 1280, heightPx = 960)
        val initial =
            PadButton(
                id = "btn-rp",
                label = "New Button",
                posX = 0.5f,
                posY = 0.5f,
                widthPx = rpDuoCanvas.baseButtonSizePx,
                heightPx = rpDuoCanvas.baseButtonSizePx,
                action = PadAction.MouseButton(MouseButton.LEFT),
            )
        assertEquals(120f, initial.widthPx, 0.001f)
        assertEquals(120f, initial.heightPx, 0.001f)

        val scrollWheel =
            initial.withActionDefaultsResolved(
                newAction = PadAction.ScrollWheel,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Mouse Left",
                newDefaultLabel = "Scroll Wheel",
                canvas = rpDuoCanvas,
            )
        assertEquals(120f, scrollWheel.widthPx, 0.001f)
        assertEquals(240f, scrollWheel.heightPx, 0.001f)

        val trackpoint =
            initial.withActionDefaultsResolved(
                newAction = PadAction.TrackpointMove(),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Mouse Left",
                newDefaultLabel = "TrackPoint",
                canvas = rpDuoCanvas,
            )
        assertEquals(240f, trackpoint.widthPx, 0.001f)
        assertEquals(240f, trackpoint.heightPx, 0.001f)
        assertEquals(ButtonShape.CIRCLE, trackpoint.buttonShape)

        val analogStick =
            initial.copy(iconName = "gamepad").withActionDefaultsResolved(
                newAction = PadAction.AnalogStick(stick = JoystickStick.RIGHT),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Mouse Left",
                newDefaultLabel = "Right Stick (Emulated)",
                canvas = rpDuoCanvas,
            )
        assertEquals(240f, analogStick.widthPx, 0.001f)
        assertEquals(240f, analogStick.heightPx, 0.001f)
        assertEquals(ButtonShape.CIRCLE, analogStick.buttonShape)
        assertNull(analogStick.iconName)
        assertEquals("Right Stick (Emulated)", analogStick.label)
    }

    @Test
    fun `resolveInitialEditButtonSizing sizes null button and preserves resized AnalogStick on existing draft`() {
        val rpDuoCanvas = LayoutCanvasSpec(widthPx = 1280, heightPx = 960)
        val createdStick =
            resolveInitialEditButtonSizing(
                button = null,
                stableButtonId = "btn-new-stick",
                initLabel = "Left Stick (Emulated)",
                initIconName = null,
                initAction = PadAction.AnalogStick(stick = JoystickStick.LEFT),
                designCanvas = rpDuoCanvas,
            )
        assertEquals(240f, createdStick.widthPx, 0.001f)
        assertEquals(240f, createdStick.heightPx, 0.001f)
        assertEquals(ButtonShape.CIRCLE, createdStick.buttonShape)

        val userResizedSmallerStick =
            createdStick.copy(
                widthPx = 180f,
                heightPx = 180f,
            )
        val preserved =
            resolveInitialEditButtonSizing(
                button = userResizedSmallerStick,
                stableButtonId = userResizedSmallerStick.id,
                initLabel = userResizedSmallerStick.label,
                initIconName = userResizedSmallerStick.iconName,
                initAction = userResizedSmallerStick.action,
                designCanvas = rpDuoCanvas,
            )
        assertEquals(180f, preserved.widthPx, 0.001f)
        assertEquals(180f, preserved.heightPx, 0.001f)
    }

    @Test
    fun `withActionDefaultsResolved resets untouched stick trackpoint and scroll wheel default sizes back to baseButtonSizePx`() {
        val rpDuoCanvas = LayoutCanvasSpec(widthPx = 1280, heightPx = 960)
        val initialGamepad =
            PadButton(
                id = "btn-cycle",
                label = "A",
                posX = 0.5f,
                posY = 0.5f,
                widthPx = rpDuoCanvas.baseButtonSizePx,
                heightPx = rpDuoCanvas.baseButtonSizePx,
                action = PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A"),
            )

        val leftStick =
            initialGamepad.withActionDefaultsResolved(
                newAction = PadAction.AnalogStick(stick = JoystickStick.LEFT),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "A",
                newDefaultLabel = "Left Stick (Emulated)",
                canvas = rpDuoCanvas,
            )
        assertEquals(240f, leftStick.widthPx, 0.001f)
        assertEquals(240f, leftStick.heightPx, 0.001f)

        val rightStick =
            leftStick.withActionDefaultsResolved(
                newAction = PadAction.AnalogStick(stick = JoystickStick.RIGHT),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Left Stick (Emulated)",
                newDefaultLabel = "Right Stick (Emulated)",
                canvas = rpDuoCanvas,
            )
        assertEquals(240f, rightStick.widthPx, 0.001f)
        assertEquals(240f, rightStick.heightPx, 0.001f)

        val backToGamepad =
            rightStick.withActionDefaultsResolved(
                newAction = PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A"),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Right Stick (Emulated)",
                newDefaultLabel = "A",
                canvas = rpDuoCanvas,
            )
        assertEquals(120f, backToGamepad.widthPx, 0.001f)
        assertEquals(120f, backToGamepad.heightPx, 0.001f)
        assertEquals("A", backToGamepad.label)

        val scrollWheel =
            backToGamepad.withActionDefaultsResolved(
                newAction = PadAction.ScrollWheel,
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "A",
                newDefaultLabel = "Scroll Wheel",
                canvas = rpDuoCanvas,
            )
        assertEquals(120f, scrollWheel.widthPx, 0.001f)
        assertEquals(240f, scrollWheel.heightPx, 0.001f)

        val fromScrollToMouse =
            scrollWheel.withActionDefaultsResolved(
                newAction = PadAction.MouseButton(MouseButton.LEFT),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Scroll Wheel",
                newDefaultLabel = "Mouse Left",
                canvas = rpDuoCanvas,
            )
        assertEquals(120f, fromScrollToMouse.widthPx, 0.001f)
        assertEquals(120f, fromScrollToMouse.heightPx, 0.001f)

        val customResizedStick = leftStick.copy(widthPx = 360f, heightPx = 360f)
        val fromCustomStick =
            customResizedStick.withActionDefaultsResolved(
                newAction = PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A"),
                newButtonPlaceholder = "New Button",
                oldDefaultLabel = "Left Stick (Emulated)",
                newDefaultLabel = "A",
                canvas = rpDuoCanvas,
            )
        assertEquals(360f, fromCustomStick.widthPx, 0.001f)
        assertEquals(360f, fromCustomStick.heightPx, 0.001f)
    }
}
