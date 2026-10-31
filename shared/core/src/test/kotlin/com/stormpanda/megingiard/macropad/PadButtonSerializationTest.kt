package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.mirror.ScreenshotTarget
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JSON serialization and backward-compatible deserialization for [PadButton].
 */
class PadButtonSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `modern PadButton with stepless dimensions survives JSON round-trip`() {
        val button =
            PadButton(
                id = "btn-stepless-1",
                posX = 0.35f,
                posY = 0.65f,
                label = "Custom",
                action = PadAction.KeyboardKey(30, "A"),
                widthDp = 95f,
                heightDp = 140f,
            )

        val encoded = json.encodeToString(button)
        // Verify widthDp and heightDp are present, and buttonSize is NOT present
        assertTrue(encoded.contains("\"widthDp\":95.0") || encoded.contains("\"widthDp\":95"))
        assertTrue(encoded.contains("\"heightDp\":140.0") || encoded.contains("\"heightDp\":140"))
        assertFalse(encoded.contains("\"buttonSize\""))

        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(button, decoded)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_2X1 deserializes to 120x60 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-1",
                "posX": 0.5,
                "posY": 0.5,
                "label": "Wide",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 30,
                    "label": "A"
                },
                "buttonSize": "SIZE_2X1"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(120f, decoded.widthDp, 0.01f)
        assertEquals(60f, decoded.heightDp, 0.01f)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_1X2 deserializes to 60x120 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-2",
                "posX": 0.2,
                "posY": 0.3,
                "label": "Tall",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 31,
                    "label": "S"
                },
                "buttonSize": "SIZE_1X2"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(60f, decoded.widthDp, 0.01f)
        assertEquals(120f, decoded.heightDp, 0.01f)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_2X2 deserializes to 120x120 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-3",
                "posX": 0.4,
                "posY": 0.4,
                "label": "Quad",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 32,
                    "label": "D"
                },
                "buttonSize": "SIZE_2X2"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(120f, decoded.widthDp, 0.01f)
        assertEquals(120f, decoded.heightDp, 0.01f)
    }

    @Test
    fun `legacy TrackpointMove action migrates size to widthDp and heightDp`() {
        val trackpointSmallJson =
            """
            {
                "id": "btn-trackpoint-small",
                "posX": 0.5,
                "posY": 0.5,
                "label": "TP Small",
                "action": {
                    "type": "trackpoint",
                    "size": "SMALL"
                }
            }
            """.trimIndent()

        val decodedSmall = json.decodeFromString<PadButton>(trackpointSmallJson)
        assertEquals(90f, decodedSmall.widthDp, 0.01f)
        assertEquals(90f, decodedSmall.heightDp, 0.01f)

        val trackpointLargeJson =
            """
            {
                "id": "btn-trackpoint-large",
                "posX": 0.5,
                "posY": 0.5,
                "label": "TP Large",
                "action": {
                    "type": "trackpoint",
                    "size": "LARGE"
                }
            }
            """.trimIndent()

        val decodedLarge = json.decodeFromString<PadButton>(trackpointLargeJson)
        assertEquals(180f, decodedLarge.widthDp, 0.01f)
        assertEquals(180f, decodedLarge.heightDp, 0.01f)
    }

    @Test
    fun `legacy JSON without buttonSize or dimensions defaults to 60x60 dp`() {
        val legacyJson =
            """
            {
                "id": "btn-legacy-default",
                "posX": 0.5,
                "posY": 0.5,
                "label": "Default",
                "action": {
                    "type": "keyboard_key",
                    "keycode": 33,
                    "label": "F"
                }
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(legacyJson)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.widthDp, 0.01f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.heightDp, 0.01f)
    }

    @Test
    fun `dimensions below minimum or negative are clamped to MP_BUTTON_MIN_SIZE_DP`() {
        val jsonStr =
            """
            {
                "id": "btn-too-small",
                "label": "Small",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                "widthDp": 10.0,
                "heightDp": -50.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(MP_BUTTON_MIN_SIZE_DP, decoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_MIN_SIZE_DP, decoded.heightDp, 0.001f)
    }

    @Test
    fun `partial dimensions deserialize with missing dimension falling back to base unit`() {
        val jsonStr =
            """
            {
                "id": "btn-partial",
                "label": "Partial",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                "widthDp": 90.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(90f, decoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.heightDp, 0.001f)
    }

    @Test
    fun `trackpoint button normalizes non-square dimensions to equal max size`() {
        val jsonStr =
            """
            {
                "id": "btn-trackpoint-nonsquare",
                "label": "TP",
                "posX": 0.5,
                "posY": 0.5,
                "action": {
                    "type": "trackpoint",
                    "mode": "virtual_touch"
                },
                "widthDp": 100.0,
                "heightDp": 150.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(150f, decoded.widthDp, 0.001f)
        assertEquals(150f, decoded.heightDp, 0.001f)
    }

    @Test
    fun `dimensions with Infinity or NaN deserialize clamped to base unit`() {
        val specialJson =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                allowSpecialFloatingPointValues = true
            }
        val button =
            PadButton(
                id = "btn-inf",
                label = "Inf",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = Float.POSITIVE_INFINITY,
                heightDp = Float.NEGATIVE_INFINITY,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val encoded = specialJson.encodeToString(button)
        val decoded = specialJson.decodeFromString<PadButton>(encoded)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, decoded.heightDp, 0.001f)

        val nanButton =
            PadButton(
                id = "btn-nan",
                label = "NaN",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = Float.NaN,
                heightDp = Float.NaN,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val nanEncoded = specialJson.encodeToString(nanButton)
        val nanDecoded = specialJson.decodeFromString<PadButton>(nanEncoded)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, nanDecoded.widthDp, 0.001f)
        assertEquals(MP_BUTTON_BASE_UNIT_DP, nanDecoded.heightDp, 0.001f)
    }

    @Test
    fun `withActionSizing for ScrollWheel enforces minimum height of 120 dp`() {
        val button =
            PadButton(
                id = "btn-sw",
                label = "SW",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = 60f,
                heightDp = 60f,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val updated = button.withActionSizing(PadAction.ScrollWheel)
        assertEquals(60f, updated.widthDp, 0.001f)
        assertEquals(MP_SCROLL_WHEEL_MIN_HEIGHT_DP, updated.heightDp, 0.001f)

        // Preserves taller height if already >= 120 dp
        val tallButton = button.copy(heightDp = 180f)
        val updatedTall = tallButton.withActionSizing(PadAction.ScrollWheel)
        assertEquals(180f, updatedTall.heightDp, 0.001f)
    }

    @Test
    fun `withActionSizing for TrackpointMove enforces 1 to 1 aspect ratio, CIRCLE shape, and minimum 120 dp`() {
        val button =
            PadButton(
                id = "btn-tp",
                label = "TP",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = 60f,
                heightDp = 80f,
                buttonShape = ButtonShape.SQUARE,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val updated = button.withActionSizing(PadAction.TrackpointMove())
        assertEquals(MP_TRACKPOINT_DEFAULT_SIZE_DP, updated.widthDp, 0.001f)
        assertEquals(MP_TRACKPOINT_DEFAULT_SIZE_DP, updated.heightDp, 0.001f)
        assertEquals(ButtonShape.CIRCLE, updated.buttonShape)

        // Expands both dimensions to the larger one if > 120 dp
        val largeButton = button.copy(widthDp = 160f, heightDp = 100f)
        val updatedLarge = largeButton.withActionSizing(PadAction.TrackpointMove())
        assertEquals(160f, updatedLarge.widthDp, 0.001f)
        assertEquals(160f, updatedLarge.heightDp, 0.001f)
    }

    @Test
    fun `withActionSizing for KeyboardKey leaves existing dimensions and shape unchanged`() {
        val button =
            PadButton(
                id = "btn-norm",
                label = "K",
                posX = 0.5f,
                posY = 0.5f,
                widthDp = 75f,
                heightDp = 95f,
                buttonShape = ButtonShape.ICON_ONLY,
                action = PadAction.ScrollWheel,
            )
        val updated = button.withActionSizing(PadAction.KeyboardKey(30, "A"))
        assertEquals(75f, updated.widthDp, 0.001f)
        assertEquals(95f, updated.heightDp, 0.001f)
        assertEquals(ButtonShape.ICON_ONLY, updated.buttonShape)
    }

    @Test
    fun `Screenshot action serializes and deserializes across all targets and defaults`() {
        for (target in ScreenshotTarget.entries) {
            val button =
                PadButton(
                    id = "btn-screenshot-${target.name}",
                    label = "Shot ${target.name}",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.Screenshot(target),
                )
            val encoded = json.encodeToString(button)
            assertTrue(encoded.contains("\"type\":\"screenshot\""))
            val decoded = json.decodeFromString<PadButton>(encoded)
            assertEquals(button, decoded)
        }

        val defaultTargetJson =
            """
            {
                "id": "btn-screenshot-default",
                "label": "Shot Default",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "screenshot" }
            }
            """.trimIndent()
        val decodedDefault = json.decodeFromString<PadButton>(defaultTargetJson)
        assertEquals(PadAction.Screenshot(ScreenshotTarget.TOP), decodedDefault.action)
    }

    @Test
    fun `Screenshot action defaultIconName returns matching splitscreen symbol for each target`() {
        assertEquals("splitscreen_bottom", PadAction.Screenshot(ScreenshotTarget.TOP).defaultIconName())
        assertEquals("splitscreen_top", PadAction.Screenshot(ScreenshotTarget.BOTTOM).defaultIconName())
        assertEquals("splitscreen", PadAction.Screenshot(ScreenshotTarget.BOTH).defaultIconName())
    }

    @Test
    fun `SwitchToLayout and SwitchToHub actions serialize and deserialize with defaultIconName`() {
        for (hold in listOf(false, true)) {
            val button =
                PadButton(
                    id = "btn-switch-layout-$hold",
                    label = "Switch Layout",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.SwitchToLayout(layoutId = "layout-target-1", holdToShift = hold),
                )
            val encoded = json.encodeToString(button)
            assertTrue(encoded.contains("\"type\":\"switch_to_layout\""))
            val decoded = json.decodeFromString<PadButton>(encoded)
            assertEquals(button, decoded)
        }

        val hubButton =
            PadButton(
                id = "btn-switch-hub",
                label = "Hub",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.SwitchToHub,
            )
        val encodedHub = json.encodeToString(hubButton)
        assertTrue(encodedHub.contains("\"type\":\"switch_to_hub\""))
        val decodedHub = json.decodeFromString<PadButton>(encodedHub)
        assertEquals(hubButton, decodedHub)

        assertEquals("view_quilt", PadAction.SwitchToLayout("layout-1", holdToShift = false).defaultIconName())
        assertEquals("dashboard", PadAction.SwitchToHub.defaultIconName())
    }

    @Test
    fun `StopAllMacros action serializes and deserializes with defaultIconName`() {
        val stopButton =
            PadButton(
                id = "btn-stop-macros",
                label = "Stop Macros",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.StopAllMacros,
            )
        val encoded = json.encodeToString(stopButton)
        assertTrue(encoded.contains("\"type\":\"stop_all_macros\""))
        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(stopButton, decoded)
        assertEquals("stop_circle", PadAction.StopAllMacros.defaultIconName())
    }

    @Test
    fun `TouchTap action serializes and deserializes with defaultIconName`() {
        val tapButton =
            PadButton(
                id = "btn-touch-tap",
                label = "Tap",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.TouchTap(normX = 0.25f, normY = 0.75f),
            )
        val encoded = json.encodeToString(tapButton)
        assertTrue(encoded.contains("\"type\":\"touch_tap\""))
        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(tapButton, decoded)
        assertEquals("ads_click", PadAction.TouchTap(0.25f, 0.75f).defaultIconName())
    }

    @Test
    fun `MediaControl action serializes and deserializes across all actions and defaultIconName`() {
        for (mediaAction in MediaControlAction.entries) {
            val button =
                PadButton(
                    id = "btn-media-${mediaAction.name}",
                    label = mediaAction.name,
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.MediaControl(mediaAction),
                )
            val encoded = json.encodeToString(button)
            assertTrue(encoded.contains("\"type\":\"media_control\""))
            val decoded = json.decodeFromString<PadButton>(encoded)
            assertEquals(button, decoded)
        }

        assertEquals("play_pause", PadAction.MediaControl(MediaControlAction.PLAY_PAUSE).defaultIconName())
        assertEquals("skip_next", PadAction.MediaControl(MediaControlAction.NEXT_TRACK).defaultIconName())
        assertEquals("skip_previous", PadAction.MediaControl(MediaControlAction.PREVIOUS_TRACK).defaultIconName())
        assertEquals("stop", PadAction.MediaControl(MediaControlAction.STOP).defaultIconName())
        assertEquals("volume_off", PadAction.MediaControl(MediaControlAction.MUTE).defaultIconName())
    }

    @Test
    fun `SwitchToProfile action serializes and deserializes with defaultIconName`() {
        val button =
            PadButton(
                id = "btn-switch-profile",
                label = "Switch Profile",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.SwitchToProfile(profileId = "profile-target-1"),
            )
        val encoded = json.encodeToString(button)
        assertTrue(encoded.contains("\"type\":\"switch_to_profile\""))
        assertTrue(encoded.contains("\"profileId\":\"profile-target-1\""))
        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(button, decoded)

        val defaultPayload = """{"id":"btn-default-prof","label":"Prof","posX":0.5,"posY":0.5,"widthDp":60.0,"heightDp":60.0,"action":{"type":"switch_to_profile"}}"""
        val decodedDefault = json.decodeFromString<PadButton>(defaultPayload)
        assertEquals(PadAction.SwitchToProfile(profileId = ""), decodedDefault.action)

        assertEquals("folder", PadAction.SwitchToProfile("profile-1").defaultIconName())
    }

    @Test
    fun `remapTargetIds remaps Macro SwitchToLayout and SwitchToProfile and preserves unmapped actions`() {
        val macroMap = mapOf("m-old" to "m-new")
        val layoutMap = mapOf("l-old" to "l-new")
        val profileMap = mapOf("p-old" to "p-new")

        assertEquals(
            PadAction.Macro("m-new"),
            PadAction.Macro("m-old").remapTargetIds(macroIdMap = macroMap),
        )
        assertEquals(
            PadAction.Macro("m-unmapped"),
            PadAction.Macro("m-unmapped").remapTargetIds(macroIdMap = macroMap),
        )
        assertEquals(
            PadAction.SwitchToLayout(layoutId = "l-new", holdToShift = true),
            PadAction.SwitchToLayout(layoutId = "l-old", holdToShift = true).remapTargetIds(
                layoutIdMap = layoutMap,
                fallbackLayoutId = "l-fallback",
            ),
        )
        assertEquals(
            PadAction.SwitchToLayout(layoutId = "l-fallback", holdToShift = false),
            PadAction.SwitchToLayout(layoutId = "l-missing", holdToShift = false).remapTargetIds(
                layoutIdMap = layoutMap,
                fallbackLayoutId = "l-fallback",
            ),
        )
        assertEquals(
            PadAction.SwitchToLayout(layoutId = "l-missing", holdToShift = false),
            PadAction.SwitchToLayout(layoutId = "l-missing", holdToShift = false).remapTargetIds(
                layoutIdMap = layoutMap,
                fallbackLayoutId = null,
            ),
        )
        assertEquals(
            PadAction.SwitchToProfile(profileId = "p-new"),
            PadAction.SwitchToProfile(profileId = "p-old").remapTargetIds(profileIdMap = profileMap),
        )
        assertEquals(
            PadAction.SwitchToProfile(profileId = "p-external"),
            PadAction.SwitchToProfile(profileId = "p-external").remapTargetIds(profileIdMap = profileMap),
        )
        assertEquals(
            PadAction.SwitchToHub,
            PadAction.SwitchToHub.remapTargetIds(
                macroIdMap = macroMap,
                layoutIdMap = layoutMap,
                fallbackLayoutId = "l-fallback",
                profileIdMap = profileMap,
            ),
        )
    }

    @Test
    fun `hasMissingTarget detects orphaned SwitchToProfile SwitchToLayout and Macro references`() {
        val activeProfile =
            PadProfile(
                id = "p-active",
                name = "Active",
                layouts = listOf(PadLayout(id = "l-1", name = "Main")),
                macros = listOf(Macro(id = "m-1", name = "Combo")),
                activeLayoutId = "l-1",
            )
        val otherProfile =
            PadProfile(
                id = "p-other",
                name = "Other",
                layouts = listOf(PadLayout(id = "l-2", name = "Secondary")),
                activeLayoutId = "l-2",
            )
        val allProfiles = listOf(activeProfile, otherProfile)

        assertFalse(PadAction.SwitchToProfile("p-other").hasMissingTarget(allProfiles, activeProfile))
        assertTrue(PadAction.SwitchToProfile("p-deleted").hasMissingTarget(allProfiles, activeProfile))
        assertTrue(PadAction.SwitchToProfile("").hasMissingTarget(allProfiles, activeProfile))

        assertFalse(PadAction.SwitchToLayout("l-1").hasMissingTarget(allProfiles, activeProfile))
        assertTrue(PadAction.SwitchToLayout("l-deleted").hasMissingTarget(allProfiles, activeProfile))
        assertTrue(PadAction.SwitchToLayout("").hasMissingTarget(allProfiles, activeProfile))
        assertFalse(PadAction.SwitchToLayout("l-deleted").hasMissingTarget(allProfiles, activeProfile = null))

        assertFalse(PadAction.Macro("m-1").hasMissingTarget(allProfiles, activeProfile))
        assertTrue(PadAction.Macro("m-deleted").hasMissingTarget(allProfiles, activeProfile))
        assertTrue(PadAction.Macro("").hasMissingTarget(allProfiles, activeProfile))
        assertFalse(PadAction.Macro("m-deleted").hasMissingTarget(allProfiles, activeProfile = null))

        assertFalse(PadAction.LayoutNext.hasMissingTarget(allProfiles, activeProfile))
        assertFalse(PadAction.SwitchToHub.hasMissingTarget(allProfiles, activeProfile))
    }
}
