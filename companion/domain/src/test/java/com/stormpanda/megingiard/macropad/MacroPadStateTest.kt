package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.mirror.ScreenCutout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [MacroPadState] — specifically focusing on [MacroPadState.loadFrom]
 * bootstrap and default generation behaviors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MacroPadStateTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testButton(
        id: String = "b1",
        label: String = "B",
        action: PadAction = PadAction.KeyboardKey(65, "A"),
        posX: Float = 0.5f,
        posY: Float = 0.5f,
        buttonTextColor: ColorOption = ColorOption.Neutral,
        buttonBgColor: ColorOption = ColorOption.Neutral,
    ) = PadButton(
        id = id,
        label = label,
        posX = posX,
        posY = posY,
        action = action,
        buttonTextColor = buttonTextColor,
        buttonBgColor = buttonBgColor,
    )

    private fun testLayout(
        id: String = UUID.randomUUID().toString(),
        name: String = "L1",
        buttons: List<PadButton> = emptyList(),
        backgroundTouchpad: BackgroundTouchpadConfig = BackgroundTouchpadConfig(),
        mirrorCutouts: List<ScreenCutout> = emptyList(),
        backgroundImagePath: String? = null,
        useBackgroundImageAsMask: Boolean = false,
        backgroundImageDim: Float = 0f,
        ambientDim: Float = 0f,
        bgImageScale: Float = 1f,
        bgImageOffsetX: Float = 0f,
        bgImageOffsetY: Float = 0f,
        buttonTextColor: ColorOption = ColorOption.Neutral,
        buttonBgColor: ColorOption = ColorOption.Neutral,
        mirrorEdgeBlendWidth: Float = 0f,
        mirrorConfigured: Boolean = false,
    ) = PadLayout(
        id = id,
        name = name,
        buttons = buttons,
        backgroundTouchpad = backgroundTouchpad,
        mirrorCutouts = mirrorCutouts,
        backgroundImagePath = backgroundImagePath,
        useBackgroundImageAsMask = useBackgroundImageAsMask,
        backgroundImageDim = backgroundImageDim,
        ambientDim = ambientDim,
        bgImageScale = bgImageScale,
        bgImageOffsetX = bgImageOffsetX,
        bgImageOffsetY = bgImageOffsetY,
        buttonTextColor = buttonTextColor,
        buttonBgColor = buttonBgColor,
        mirrorEdgeBlendWidth = mirrorEdgeBlendWidth,
        mirrorConfigured = mirrorConfigured,
    )

    private fun testProfile(
        id: String = UUID.randomUUID().toString(),
        name: String = "P1",
        layouts: List<PadLayout> = listOf(testLayout()),
        activeLayoutId: String = layouts.firstOrNull()?.id ?: "",
        macros: List<Macro> = emptyList(),
        association: ProfileAssociation? = null,
        isDefault: Boolean = false,
    ) = PadProfile(
        id = id,
        name = name,
        layouts = layouts,
        activeLayoutId = activeLayoutId,
        macros = macros,
        association = association,
        isDefault = isDefault,
    )

    private fun loadProfiles(
        vararg profiles: PadProfile,
        activeId: String? = profiles.firstOrNull()?.id,
    ) {
        MacroPadState.loadFrom(profiles.toList(), activeId)
    }

    private fun assertFlags(
        kb: Boolean = false,
        gp: Boolean = false,
        ms: Boolean = false,
        touch: Boolean = false,
        profile: PadProfile = MacroPadState.activeProfile.value!!,
    ) {
        assertEquals(kb, profile.enableKeyboard)
        assertEquals(gp, profile.enableGamepad)
        assertEquals(ms, profile.enableMouse)
        assertEquals(touch, profile.enableTouch)
    }

    @Test
    fun `loadFrom with empty list generates default profile and layout`() {
        MacroPadState.loadFrom(emptyList(), null)

        val profiles = MacroPadState.profiles.value
        assertEquals(1, profiles.size)

        val defaultProfile = profiles.first()
        assertEquals("Default", defaultProfile.name)
        assertNotNull(defaultProfile.id)

        assertEquals(1, defaultProfile.layouts.size)
        val defaultLayout = defaultProfile.layouts.first()
        assertEquals("Default", defaultLayout.name)
        assertNotNull(defaultLayout.id)

        assertEquals(defaultProfile.id, MacroPadState.activeProfileId.value)
        assertEquals(defaultProfile, MacroPadState.activeProfile.value)
        assertEquals(defaultLayout, MacroPadState.activeLayout.value)
    }

    @Test
    fun `loadFrom with existing profiles preserves them`() {
        val profileId = UUID.randomUUID().toString()
        val layoutId = UUID.randomUUID().toString()
        val existingProfile =
            testProfile(
                id = profileId,
                name = "My Custom Profile",
                layouts = listOf(testLayout(id = layoutId, name = "My Layout")),
                activeLayoutId = layoutId,
            )

        loadProfiles(existingProfile, activeId = profileId)

        val profiles = MacroPadState.profiles.value
        assertEquals(1, profiles.size)
        assertEquals(existingProfile.id, profiles.first().id)
        assertEquals("My Custom Profile", profiles.first().name)
        assertEquals(profileId, MacroPadState.activeProfileId.value)
        assertEquals(layoutId, MacroPadState.activeLayout.value?.id)
    }

    @Test
    fun `loadFrom resolves null active ID to first profile`() {
        val p1 = testProfile(id = "p1", name = "P1", layouts = listOf(testLayout(id = "l1")))
        val p2 = testProfile(id = "p2", name = "P2", layouts = listOf(testLayout(id = "l2")))
        MacroPadState.loadFrom(listOf(p1, p2), null)
        assertEquals("p1", MacroPadState.activeProfileId.value)
    }

    @Test
    fun `loadFrom resolves mismatched active ID to first profile`() {
        val p1 = testProfile(id = "p1", name = "P1", layouts = listOf(testLayout(id = "l1")))
        MacroPadState.loadFrom(listOf(p1), "invalid-id")
        assertEquals("p1", MacroPadState.activeProfileId.value)
    }

    @Test
    fun `renameProfile updates name and package mapping`() {
        val p1 = testProfile(id = "p1", name = "P1", layouts = listOf(testLayout(id = "l1")))
        loadProfiles(p1)

        MacroPadState.renameProfile("p1", "New Name", ProfileAssociation(packageName = "com.example.app"))

        val profile = MacroPadState.profiles.value.first()
        assertEquals("New Name", profile.name)
        assertEquals("com.example.app", profile.association?.packageName)
    }

    @Test
    fun `renameProfile normalizes blank names and resolves duplicates`() {
        val p1 =
            testProfile(
                id = "p1",
                name = "Retro",
                layouts = listOf(testLayout(id = "l1")),
                association = ProfileAssociation(packageName = "com.retroarch"),
            )
        val p2 = testProfile(id = "p2", name = "Citra", layouts = listOf(testLayout(id = "l2")))
        loadProfiles(p1, p2, activeId = "p1")

        // Try to rename Retro to blank string -> should fallback to 'Profile' and preserve package
        MacroPadState.renameProfile("p1", "   ")
        val p1Profile = MacroPadState.profiles.value.first { it.id == "p1" }
        assertEquals("Profile", p1Profile.name)
        assertEquals("com.retroarch", p1Profile.association?.packageName)

        // Try to rename Retro (now Profile) to "Citra" (which already exists) -> should resolve to "Citra (2)"
        MacroPadState.renameProfile("p1", "Citra")
        assertEquals(
            "Citra (2)",
            MacroPadState.profiles.value
                .first { it.id == "p1" }
                .name,
        )
    }

    @Test
    fun `withSyncedDeviceFlags synchronization rules`() {
        val p1 = testProfile(id = "p1", layouts = listOf(testLayout(id = "l1", buttons = emptyList())), activeLayoutId = "l1")
        loadProfiles(p1)

        // 1. Empty button list -> all flags false
        assertFlags()

        fun updateWithAction(action: PadAction) {
            val cur = MacroPadState.activeProfile.value!!
            MacroPadState.updateLayout(cur.layouts.first().copy(buttons = listOf(testButton(action = action))))
        }

        // 2. Keyboard button
        updateWithAction(PadAction.KeyboardKey(65, "A"))
        assertFlags(kb = true)

        // 3. Gamepad button
        updateWithAction(PadAction.GamepadButton(96, "GP"))
        assertFlags(gp = true)

        // 4. Mouse button
        updateWithAction(PadAction.MouseButton(MouseButton.LEFT))
        assertFlags(ms = true)

        // 5. Trackpoint VIRTUAL_TOUCH button
        updateWithAction(PadAction.TrackpointMove(mode = TrackpointMode.VIRTUAL_TOUCH))
        assertFlags(touch = true)

        // 6. MirrorTouchProjection button -> all injector flags remain false
        updateWithAction(PadAction.MirrorTouchProjection)
        assertFlags()

        // 7. Macro button -> all flags force-enabled (true)
        updateWithAction(PadAction.Macro("macro-1"))
        assertFlags(kb = true, gp = true, ms = true, touch = true)

        // 8. Enable Background Touchpad -> enableMouse force-enabled
        val active = MacroPadState.activeProfile.value!!
        MacroPadState.updateLayout(
            active.layouts.first().copy(
                buttons = emptyList(),
                backgroundTouchpad = BackgroundTouchpadConfig(enabled = true),
            ),
        )
        assertFlags(ms = true)
    }

    @Test
    fun `fullscreen keyboard and mouse actions do not enable background injector flags`() {
        val p1 =
            testProfile(
                id = "p1",
                layouts =
                    listOf(
                        testLayout(
                            id = "l1",
                            buttons =
                                listOf(
                                    testButton(id = "b1", action = PadAction.FullScreenKeyboard()),
                                    testButton(id = "b2", action = PadAction.FullScreenMouse()),
                                ),
                        ),
                    ),
            )
        loadProfiles(p1)
        assertFlags()
    }

    @Test
    fun `copyMacroToProfile clones macro to target profile with new ID and name on collision`() {
        val m1 = Macro(id = "m1", name = "Combo", steps = emptyList())
        val p1 = testProfile(id = "p1", layouts = listOf(testLayout(id = "l1")), macros = listOf(m1))
        val p2 =
            testProfile(
                id = "p2",
                layouts = listOf(testLayout(id = "l2")),
                macros = listOf(Macro(id = "m2", name = "Combo", steps = emptyList())),
            )
        loadProfiles(p1, p2, activeId = "p1")

        MacroPadState.copyMacroToProfile(m1, "p2")

        val target = MacroPadState.profiles.value.first { it.id == "p2" }
        assertEquals(2, target.macros.size)
        val copied = target.macros.first { it.id != "m2" }
        assertEquals("Combo (2)", copied.name)
    }

    @Test
    fun `copyLayoutToProfile duplicates layout and maps referenced macros when cross-profile`() {
        val m1 = Macro(id = "macro-1", name = "Fire", steps = emptyList())
        val btn = testButton(id = "btn-1", action = PadAction.Macro("macro-1"))
        val l1 = testLayout(id = "layout-1", name = "Lay1", buttons = listOf(btn))
        val p1 = testProfile(id = "p1", layouts = listOf(l1), macros = listOf(m1))
        val p2 = testProfile(id = "p2", layouts = listOf(testLayout(id = "layout-2", name = "Lay2")))
        loadProfiles(p1, p2, activeId = "p1")

        MacroPadState.copyLayoutToProfile(l1, "p1", "p2")

        val targetProfile = MacroPadState.profiles.value.first { it.id == "p2" }
        assertEquals(2, targetProfile.layouts.size)
        val copiedLayout = targetProfile.layouts.first { it.id != "layout-2" }
        assertEquals("Lay1", copiedLayout.name)
        assertEquals(1, copiedLayout.buttons.size)

        assertEquals(1, targetProfile.macros.size)
        val copiedMacro = targetProfile.macros.first()
        assertEquals("Fire", copiedMacro.name)

        val copiedBtn = copiedLayout.buttons.first()
        val copiedBtnAction = copiedBtn.action as PadAction.Macro
        assertEquals(copiedMacro.id, copiedBtnAction.macroId)
    }

    @Test
    fun `copyButtonToLayout duplicates button and copies referenced macro when cross-profile`() {
        val m1 = Macro(id = "macro-1", name = "Punch", steps = emptyList())
        val btn = testButton(id = "btn-1", label = "B", action = PadAction.Macro("macro-1"))
        val p1 = testProfile(id = "p1", layouts = listOf(testLayout(id = "l1")), macros = listOf(m1))
        val p2 = testProfile(id = "p2", layouts = listOf(testLayout(id = "l2")))
        loadProfiles(p1, p2, activeId = "p1")

        MacroPadState.copyButtonToLayout(btn, "p1", "p2", "l2")

        val targetProfile = MacroPadState.profiles.value.first { it.id == "p2" }
        val targetLayout = targetProfile.layouts.first()
        assertEquals(1, targetLayout.buttons.size)

        val copiedBtn = targetLayout.buttons.first()
        assertEquals("B", copiedBtn.label)

        assertEquals(1, targetProfile.macros.size)
        val copiedMacro = targetProfile.macros.first()
        assertEquals("Punch", copiedMacro.name)
        assertEquals(copiedMacro.id, (copiedBtn.action as PadAction.Macro).macroId)
    }

    @Test
    fun `duplicateButtonInLayout duplicates button in place with coordinate offset`() {
        val btn = testButton(id = "btn-1", posX = 0.5f, posY = 0.5f, action = PadAction.KeyboardKey(65, "A"))
        val p1 = testProfile(id = "p1", layouts = listOf(testLayout(id = "l1", buttons = listOf(btn))))
        loadProfiles(p1)

        MacroPadState.duplicateButtonInLayout(btn, "l1")

        val targetLayout =
            MacroPadState.activeProfile.value!!
                .layouts
                .first()
        assertEquals(2, targetLayout.buttons.size)
        val copiedBtn = targetLayout.buttons.first { it.id != "btn-1" }
        assertEquals(0.55f, copiedBtn.posX, 0.001f)
        assertEquals(0.55f, copiedBtn.posY, 0.001f)
    }

    @Test
    fun `duplicateLayout duplicates active profile layout and resolves name collision`() {
        val btn = testButton(id = "btn-1", action = PadAction.KeyboardKey(65, "A"))
        val cutout =
            ScreenCutout(
                id = "cutout-1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                followTouch = true,
                touchProjectionEnabled = true,
                motionSmoothing = true,
                motionSmoothingStrength = 75,
            )
        val l1 =
            testLayout(
                id = "layout-1",
                name = "Lay1",
                buttons = listOf(btn),
                mirrorEdgeBlendWidth = 25f,
                mirrorCutouts = listOf(cutout),
            )
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "layout-1"))

        MacroPadState.duplicateLayout("layout-1")

        val profile = MacroPadState.activeProfile.value!!
        assertEquals(2, profile.layouts.size)
        val duplicated = profile.layouts.first { it.id != "layout-1" }
        assertEquals("Lay1 (2)", duplicated.name)
        assertEquals(1, duplicated.buttons.size)
        val dupBtn = duplicated.buttons.first()
        assertEquals("B", dupBtn.label)
        assertNotEquals("btn-1", dupBtn.id)

        assertEquals(25f, duplicated.mirrorEdgeBlendWidth)
        assertEquals(1, duplicated.mirrorCutouts.size)
        val dupCutout = duplicated.mirrorCutouts.first()
        assertNotEquals("cutout-1", dupCutout.id)
        assertTrue(dupCutout.followTouch)
        assertTrue(dupCutout.touchProjectionEnabled)
        assertTrue(dupCutout.motionSmoothing)
        assertEquals(75, dupCutout.motionSmoothingStrength)
    }

    @Test
    fun `duplicateProfile deep copies profile, layout buttons and macros`() {
        val m1 = Macro(id = "macro-1", name = "Slash", steps = emptyList())
        val btn = testButton(id = "btn-1", action = PadAction.Macro("macro-1"))
        val l1 = testLayout(id = "layout-1", name = "Lay1", buttons = listOf(btn))
        loadProfiles(testProfile(id = "p1", name = "P1", layouts = listOf(l1), macros = listOf(m1)))

        MacroPadState.duplicateProfile("p1")

        val profiles = MacroPadState.profiles.value
        assertEquals(2, profiles.size)
        val duplicatedProfile = profiles.first { it.id != "p1" }
        assertEquals("P1 (2)", duplicatedProfile.name)
        assertEquals(1, duplicatedProfile.layouts.size)
        assertEquals(1, duplicatedProfile.macros.size)

        val dupMacro = duplicatedProfile.macros.first()
        assertEquals("Slash", dupMacro.name)
        assertNotEquals("macro-1", dupMacro.id)

        val dupLayout = duplicatedProfile.layouts.first()
        assertEquals("Lay1", dupLayout.name)
        assertEquals(1, dupLayout.buttons.size)

        val dupBtn = dupLayout.buttons.first()
        assertNotEquals("btn-1", dupBtn.id)
        assertEquals(dupMacro.id, (dupBtn.action as PadAction.Macro).macroId)
    }

    @Test
    fun `updateLayout preserves and updates backgroundImagePath`() {
        val l1 = testLayout(id = "layout-1", name = "Lay1", backgroundImagePath = null)
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "layout-1"))

        assertEquals(null, MacroPadState.activeLayout.value?.backgroundImagePath)

        val updatedLayout = l1.copy(backgroundImagePath = "backgrounds/bg_layout-1")
        MacroPadState.updateLayout(updatedLayout)
        assertEquals("backgrounds/bg_layout-1", MacroPadState.activeLayout.value?.backgroundImagePath)
    }

    @Test
    fun `updateLayout preserves and updates useBackgroundImageAsMask`() {
        val l1 = testLayout(id = "layout-1", name = "Lay1", useBackgroundImageAsMask = false)
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "layout-1"))

        assertEquals(false, MacroPadState.activeLayout.value?.useBackgroundImageAsMask)

        MacroPadState.updateLayout(l1.copy(useBackgroundImageAsMask = true))
        assertEquals(true, MacroPadState.activeLayout.value?.useBackgroundImageAsMask)
    }

    @Test
    fun `updateLayout preserves and updates backgroundImageDim`() {
        val l1 = testLayout(id = "layout-1", name = "Lay1", backgroundImageDim = 0f)
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "layout-1"))

        assertEquals(0f, MacroPadState.activeLayout.value?.backgroundImageDim)

        MacroPadState.updateLayout(l1.copy(backgroundImageDim = 0.5f))
        assertEquals(0.5f, MacroPadState.activeLayout.value?.backgroundImageDim)
    }

    @Test
    fun `updateLayout preserves and updates ambientDim`() {
        val l1 = testLayout(id = "layout-1", name = "Lay1", ambientDim = 0f)
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "layout-1"))

        assertEquals(0f, MacroPadState.activeLayout.value?.ambientDim)

        MacroPadState.updateLayout(l1.copy(ambientDim = 0.4f))
        assertEquals(0.4f, MacroPadState.activeLayout.value?.ambientDim)
    }

    @Test
    fun `updateLayout preserves and updates bgImageScale and offsets`() {
        val l1 = testLayout(id = "layout-1", name = "Lay1", bgImageScale = 1f, bgImageOffsetX = 0f, bgImageOffsetY = 0f)
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "layout-1"))

        assertEquals(1f, MacroPadState.activeLayout.value?.bgImageScale)
        assertEquals(0f, MacroPadState.activeLayout.value?.bgImageOffsetX)
        assertEquals(0f, MacroPadState.activeLayout.value?.bgImageOffsetY)

        MacroPadState.updateLayout(l1.copy(bgImageScale = 2.5f, bgImageOffsetX = 0.2f, bgImageOffsetY = -0.1f))

        assertEquals(2.5f, MacroPadState.activeLayout.value?.bgImageScale)
        assertEquals(0.2f, MacroPadState.activeLayout.value?.bgImageOffsetX)
        assertEquals(-0.1f, MacroPadState.activeLayout.value?.bgImageOffsetY)
    }

    @Test
    fun `reorderProfiles updates profile order in state`() {
        val p1 = testProfile(id = "p1", name = "Profile 1", layouts = listOf(testLayout(id = "l1")))
        val p2 = testProfile(id = "p2", name = "Profile 2", layouts = listOf(testLayout(id = "l2")))
        val p3 = testProfile(id = "p3", name = "Profile 3", layouts = listOf(testLayout(id = "l3")))

        loadProfiles(p1, p2, p3, activeId = "p1")
        assertEquals(listOf("p1", "p2", "p3"), MacroPadState.profiles.value.map { it.id })

        MacroPadState.reorderProfiles(listOf(p3, p1, p2))
        assertEquals(listOf("p3", "p1", "p2"), MacroPadState.profiles.value.map { it.id })
    }

    @Test
    fun `reorderLayouts updates layout order in active profile`() {
        val l1 = testLayout(id = "l1", name = "Layout 1")
        val l2 = testLayout(id = "l2", name = "Layout 2")
        val l3 = testLayout(id = "l3", name = "Layout 3")
        val p1 = testProfile(id = "p1", layouts = listOf(l1, l2, l3), activeLayoutId = "l1")

        loadProfiles(p1, activeId = "p1")
        assertEquals(
            listOf("l1", "l2", "l3"),
            MacroPadState.activeProfile.value
                ?.layouts
                ?.map { it.id },
        )

        MacroPadState.reorderLayouts(listOf(l2, l3, l1))
        assertEquals(
            listOf("l2", "l3", "l1"),
            MacroPadState.activeProfile.value
                ?.layouts
                ?.map { it.id },
        )
    }

    @Test
    fun `isEditingButtonPositions defaults to false and updates correctly`() {
        assertEquals(false, MacroPadState.isEditingButtonPositions.value)
        MacroPadState.setEditingButtonPositions(true)
        assertEquals(true, MacroPadState.isEditingButtonPositions.value)
        MacroPadState.setEditingButtonPositions(false)
        assertEquals(false, MacroPadState.isEditingButtonPositions.value)
    }

    @Test
    fun `gridMode defaults to OFF and updates correctly`() {
        assertEquals(GridMode.OFF, MacroPadState.gridMode.value)
        MacroPadState.setGridMode(GridMode.RECTANGULAR)
        assertEquals(GridMode.RECTANGULAR, MacroPadState.gridMode.value)
        MacroPadState.setGridMode(GridMode.RADIAL)
        assertEquals(GridMode.RADIAL, MacroPadState.gridMode.value)
        MacroPadState.setGridMode(GridMode.OFF)
        assertEquals(GridMode.OFF, MacroPadState.gridMode.value)
    }

    @Test
    fun `setSelectedButtonId updates selectedButtonId and setEditingButtonPositions resets it`() {
        assertEquals(null, MacroPadState.selectedButtonId.value)
        MacroPadState.setSelectedButtonId("btn-123")
        assertEquals("btn-123", MacroPadState.selectedButtonId.value)
        MacroPadState.setEditingButtonPositions(false)
        assertEquals(null, MacroPadState.selectedButtonId.value)
    }

    @Test
    fun `setPreviewLayout and clearPreviewLayout manage in-flight layout preview`() {
        val savedLayout = testLayout(id = "l1", name = "Saved Layout", buttonTextColor = ColorOption.Neutral, mirrorConfigured = true)
        val p1 = testProfile(id = "p1", layouts = listOf(savedLayout), activeLayoutId = "l1")
        loadProfiles(p1)

        assertEquals(savedLayout, MacroPadState.activeLayout.value)
        assertEquals(null, MacroPadState.previewLayout.value)

        val previewLayout = savedLayout.copy(buttonTextColor = ColorOption.Accent)
        MacroPadState.setPreviewLayout(previewLayout)

        assertEquals(previewLayout, MacroPadState.previewLayout.value)
        assertEquals(previewLayout, MacroPadState.activeLayout.value)
        assertEquals(
            ColorOption.Neutral,
            MacroPadState.profiles.value
                .first()
                .layouts
                .first()
                .buttonTextColor,
        )

        MacroPadState.clearPreviewLayout()
        assertEquals(null, MacroPadState.previewLayout.value)
        assertEquals(savedLayout, MacroPadState.activeLayout.value)
    }

    @Test
    fun `setPreviewButton replaces existing button or appends new button in activeLayout preview`() {
        val b1 = testButton(id = "btn-1", label = "A", posX = 0.2f, posY = 0.2f, action = PadAction.KeyboardKey(65, "A"))
        val savedLayout = testLayout(id = "l1", name = "Saved Layout", buttons = listOf(b1), mirrorConfigured = true)
        val p1 = testProfile(id = "p1", layouts = listOf(savedLayout), activeLayoutId = "l1")
        loadProfiles(p1)

        // 1. Modify existing button in preview
        val modifiedB1 = b1.copy(buttonTextColor = ColorOption.Accent)
        MacroPadState.setPreviewButton(modifiedB1)

        val preview1 = MacroPadState.activeLayout.value
        assertNotNull(preview1)
        assertEquals(1, preview1!!.buttons.size)
        assertEquals(ColorOption.Accent, preview1.buttons.first().buttonTextColor)
        assertEquals(
            ColorOption.Neutral,
            MacroPadState.profiles.value
                .first()
                .layouts
                .first()
                .buttons
                .first()
                .buttonTextColor,
        )

        // 2. Add new button in preview
        val b2 =
            testButton(
                id = "btn-2",
                label = "B",
                posX = 0.4f,
                posY = 0.4f,
                action = PadAction.KeyboardKey(66, "B"),
                buttonTextColor = ColorOption.Custom(0xFF112233.toInt()),
            )
        MacroPadState.setPreviewButton(b2)

        val preview2 = MacroPadState.activeLayout.value
        assertNotNull(preview2)
        assertEquals(2, preview2!!.buttons.size)
        assertTrue(preview2.buttons.any { it.id == "btn-2" })

        // 3. Passing null clears preview
        MacroPadState.setPreviewButton(null)
        assertEquals(null, MacroPadState.previewLayout.value)
        assertEquals(savedLayout, MacroPadState.activeLayout.value)
    }

    @Test
    fun `loadFrom migrates legacy full opacity custom buttonBgColor on layout and buttons`() {
        val b1 =
            testButton(
                id = "btn-1",
                label = "Full Opacity Custom",
                posX = 0.1f,
                posY = 0.1f,
                action = PadAction.KeyboardKey(65, "A"),
                buttonBgColor = ColorOption.Custom(0xFFFF5500.toInt()),
            )
        val b2 =
            testButton(
                id = "btn-2",
                label = "Existing Custom Alpha",
                posX = 0.3f,
                posY = 0.3f,
                action = PadAction.KeyboardKey(66, "B"),
                buttonBgColor = ColorOption.Custom(0x80FF5500.toInt()),
            )
        val layout =
            testLayout(
                id = "l1",
                buttonBgColor = ColorOption.Custom(0xFF00FF00.toInt()),
                buttons = listOf(b1, b2),
                mirrorConfigured = true,
            )
        loadProfiles(testProfile(id = "p1", layouts = listOf(layout), activeLayoutId = "l1"))

        val loadedProfile = MacroPadState.profiles.value.first { it.id == "p1" }
        val loadedLayout = loadedProfile.layouts.first { it.id == "l1" }

        val layoutBg = loadedLayout.buttonBgColor as ColorOption.Custom
        assertEquals(0xB3, (layoutBg.argb ushr 24) and 0xFF)
        assertEquals(0x00FF00, layoutBg.argb and 0x00FFFFFF)

        val btn1Bg = loadedLayout.buttons.first { it.id == "btn-1" }.buttonBgColor as ColorOption.Custom
        assertEquals(0xB3, (btn1Bg.argb ushr 24) and 0xFF)
        assertEquals(0xFF5500, btn1Bg.argb and 0x00FFFFFF)

        val btn2Bg = loadedLayout.buttons.first { it.id == "btn-2" }.buttonBgColor as ColorOption.Custom
        assertEquals(0x80, (btn2Bg.argb ushr 24) and 0xFF)
        assertEquals(0xFF5500, btn2Bg.argb and 0x00FFFFFF)
    }

    @Test
    fun `setCroppingBackground updates isCroppingBackground state`() {
        assertEquals(false, MacroPadState.isCroppingBackground.value)
        MacroPadState.setCroppingBackground(true)
        assertEquals(true, MacroPadState.isCroppingBackground.value)
        MacroPadState.setCroppingBackground(false)
        assertEquals(false, MacroPadState.isCroppingBackground.value)
    }

    @Test
    fun `updatePreviewBackgroundCrop updates preview layout scale and offsets`() {
        val layout = testLayout(id = "l1", name = "Layout")
        loadProfiles(testProfile(id = "p1", layouts = listOf(layout), activeLayoutId = "l1"))

        MacroPadState.setPreviewLayout(layout)
        assertEquals(1.0f, MacroPadState.previewLayout.value?.bgImageScale)

        MacroPadState.updatePreviewBackgroundCrop(2.5f, 0.15f, -0.25f)

        assertEquals(2.5f, MacroPadState.previewLayout.value?.bgImageScale)
        assertEquals(0.15f, MacroPadState.previewLayout.value?.bgImageOffsetX)
        assertEquals(-0.25f, MacroPadState.previewLayout.value?.bgImageOffsetY)

        MacroPadState.setCroppingBackground(true)
        MacroPadState.clearPreviewLayout()
        assertEquals(null, MacroPadState.previewLayout.value)
        assertEquals(false, MacroPadState.isCroppingBackground.value)
    }

    @Test
    fun `deleteLayout with multiple layouts removes target layout and returns true`() {
        val layout1 = testLayout(id = "l1", name = "Layout 1")
        val layout2 = testLayout(id = "l2", name = "Layout 2")
        loadProfiles(testProfile(id = "p1", layouts = listOf(layout1, layout2), activeLayoutId = "l1"))

        assertTrue(MacroPadState.deleteLayout("l2"))
        val updatedProfile = MacroPadState.activeProfile.value
        assertNotNull(updatedProfile)
        assertEquals(1, updatedProfile!!.layouts.size)
        assertEquals("l1", updatedProfile.layouts.first().id)
    }

    @Test
    fun `deleteLayout when active layout is deleted switches activeLayoutId to remaining layout`() {
        val layout1 = testLayout(id = "l1", name = "Layout 1")
        val layout2 = testLayout(id = "l2", name = "Layout 2")
        loadProfiles(testProfile(id = "p1", layouts = listOf(layout1, layout2), activeLayoutId = "l1"))

        assertTrue(MacroPadState.deleteLayout("l1"))
        val updatedProfile = MacroPadState.activeProfile.value
        assertNotNull(updatedProfile)
        assertEquals(1, updatedProfile!!.layouts.size)
        assertEquals("l2", updatedProfile.layouts.first().id)
        assertEquals("l2", updatedProfile.activeLayoutId)
    }

    @Test
    fun `deleteLayout with single layout returns false and preserves layout`() {
        val layout1 = testLayout(id = "l1", name = "Only Layout")
        loadProfiles(testProfile(id = "p1", layouts = listOf(layout1), activeLayoutId = "l1"))

        assertFalse(MacroPadState.deleteLayout("l1"))
        val updatedProfile = MacroPadState.activeProfile.value
        assertNotNull(updatedProfile)
        assertEquals(1, updatedProfile!!.layouts.size)
        assertEquals("l1", updatedProfile.layouts.first().id)
    }

    @Test
    fun `deleteLayout with non-existent layout returns false`() {
        val layout1 = testLayout(id = "l1", name = "Only Layout")
        loadProfiles(testProfile(id = "p1", layouts = listOf(layout1), activeLayoutId = "l1"))

        assertFalse(MacroPadState.deleteLayout("non-existent-id"))
        val updatedProfile = MacroPadState.activeProfile.value
        assertNotNull(updatedProfile)
        assertEquals(1, updatedProfile!!.layouts.size)
        assertEquals("l1", updatedProfile.layouts.first().id)
    }

    @Test
    fun `nextLayout and previousLayout cycle through layouts correctly`() {
        val l1 = testLayout(id = "l1", name = "Layout 1")
        val l2 = testLayout(id = "l2", name = "Layout 2")
        val l3 = testLayout(id = "l3", name = "Layout 3")
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1, l2, l3), activeLayoutId = "l1"))

        assertEquals("l1", MacroPadState.activeLayout.value?.id)

        MacroPadState.nextLayout()
        assertEquals("l2", MacroPadState.activeLayout.value?.id)

        MacroPadState.nextLayout()
        assertEquals("l3", MacroPadState.activeLayout.value?.id)

        MacroPadState.nextLayout()
        assertEquals("l1", MacroPadState.activeLayout.value?.id)

        MacroPadState.previousLayout()
        assertEquals("l3", MacroPadState.activeLayout.value?.id)

        MacroPadState.previousLayout()
        assertEquals("l2", MacroPadState.activeLayout.value?.id)
    }

    @Test
    fun `reorderProfiles, reorderLayouts, and reorderMacros update state correctly`() {
        val m1 = Macro(id = "m1", name = "Macro 1")
        val m2 = Macro(id = "m2", name = "Macro 2")
        val l1 = testLayout(id = "l1", name = "Layout 1")
        val l2 = testLayout(id = "l2", name = "Layout 2")
        val p1 = testProfile(id = "p1", name = "P1", layouts = listOf(l1, l2), macros = listOf(m1, m2))
        val p2 = testProfile(id = "p2", name = "P2")
        loadProfiles(p1, p2)

        MacroPadState.reorderProfiles(listOf(p2, p1))
        assertEquals(listOf("p2", "p1"), MacroPadState.profiles.value.map { it.id })

        MacroPadState.reorderLayouts(listOf(l2, l1))
        assertEquals(
            listOf("l2", "l1"),
            MacroPadState.activeProfile.value
                ?.layouts
                ?.map { it.id },
        )

        MacroPadState.reorderMacros(listOf(m2, m1))
        assertEquals(
            listOf("m2", "m1"),
            MacroPadState.activeProfile.value
                ?.macros
                ?.map { it.id },
        )
    }

    @Test
    fun `copyMacroToActiveProfile and copyMacroToProfile duplicate macros with new IDs`() {
        val m1 = Macro(id = "m1", name = "MyMacro")
        val p1 = testProfile(id = "p1", macros = listOf(m1))
        val p2 = testProfile(id = "p2", macros = emptyList())
        loadProfiles(p1, p2)

        MacroPadState.setActiveProfileId("p1")
        MacroPadState.copyMacroToActiveProfile(m1)
        val p1Macros = MacroPadState.activeProfile.value?.macros ?: emptyList()
        assertEquals(2, p1Macros.size)
        assertTrue(p1Macros.any { it.name.startsWith("MyMacro") && it.id != "m1" })

        MacroPadState.copyMacroToProfile(m1, "p2")
        val p2Profile = MacroPadState.profiles.value.first { it.id == "p2" }
        assertEquals(1, p2Profile.macros.size)
        assertEquals("MyMacro", p2Profile.macros.first().name)
        assertNotEquals("m1", p2Profile.macros.first().id)
    }

    @Test
    fun `setPreviewButton adds new button or updates existing button in preview`() {
        val b1 = testButton(id = "b1", label = "B1")
        val l1 = testLayout(id = "l1", buttons = listOf(b1))
        loadProfiles(testProfile(id = "p1", layouts = listOf(l1), activeLayoutId = "l1"))

        // Update existing button
        val updatedB1 = b1.copy(label = "B1 Updated")
        MacroPadState.setPreviewButton(updatedB1)
        assertEquals(
            "B1 Updated",
            MacroPadState.previewLayout.value
                ?.buttons
                ?.first { it.id == "b1" }
                ?.label,
        )

        // Add new button
        val b2 = testButton(id = "b2", label = "B2")
        MacroPadState.setPreviewButton(b2)
        assertEquals(
            2,
            MacroPadState.previewLayout.value
                ?.buttons
                ?.size,
        )

        // Null button clears preview
        MacroPadState.setPreviewButton(null)
        assertEquals(null, MacroPadState.previewLayout.value)
    }

    @Test
    fun `editing mode and grid mode state setters`() {
        MacroPadState.setEditingButtonPositions(true)
        assertTrue(MacroPadState.isEditingButtonPositions.value)
        MacroPadState.setSelectedButtonId("btn-test")
        assertEquals("btn-test", MacroPadState.selectedButtonId.value)

        MacroPadState.setEditingButtonPositions(false)
        assertFalse(MacroPadState.isEditingButtonPositions.value)
        assertEquals(null, MacroPadState.selectedButtonId.value)

        MacroPadState.setGridMode(GridMode.RECTANGULAR)
        assertEquals(GridMode.RECTANGULAR, MacroPadState.gridMode.value)
        MacroPadState.setGridMode(GridMode.OFF)
        assertEquals(GridMode.OFF, MacroPadState.gridMode.value)
    }

    @Test
    fun `getDefaultOrFirstProfile returns profile marked isDefault`() {
        val p1 = testProfile(id = "p1", name = "First", isDefault = false)
        val p2 = testProfile(id = "p2", name = "Second", isDefault = true)
        loadProfiles(p1, p2)

        val result = MacroPadState.getDefaultOrFirstProfile()
        assertEquals(p2.id, result?.id)
    }

    @Test
    fun `getDefaultOrFirstProfile falls back to profile named Default when isDefault is false`() {
        val p1 = testProfile(id = "p1", name = "Custom Profile", isDefault = false)
        val p2 = testProfile(id = "p2", name = "Default", isDefault = false)
        loadProfiles(p1, p2)

        val result = MacroPadState.getDefaultOrFirstProfile()
        assertEquals(p2.id, result?.id)
    }

    @Test
    fun `getDefaultOrFirstProfile falls back to first profile when none isDefault or named Default`() {
        val p1 = testProfile(id = "p1", name = "Custom One", isDefault = false)
        val p2 = testProfile(id = "p2", name = "Custom Two", isDefault = false)
        loadProfiles(p1, p2)

        val result = MacroPadState.getDefaultOrFirstProfile()
        assertEquals(p1.id, result?.id)
    }
}
