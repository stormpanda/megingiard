package com.stormpanda.megingiard.macropad

import com.stormpanda.megingiard.input.TouchAction
import com.stormpanda.megingiard.keyboard.LinuxKeycodes
import com.stormpanda.megingiard.mirror.ScreenshotTarget
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `LayoutCanvasSpec computes derived button sizes for Thor RP Duo and tiny canvas floor`() {
        assertEquals(1240, THOR_BOTTOM_CANVAS_SPEC.widthPx)
        assertEquals(1080, THOR_BOTTOM_CANVAS_SPEC.heightPx)
        assertEquals(1080, THOR_BOTTOM_CANVAS_SPEC.shortEdgePx)
        assertEquals(MP_THOR_MIN_BUTTON_SIZE_PX, THOR_BOTTOM_CANVAS_SPEC.minButtonSizePx, 0.001f)
        assertEquals(MP_THOR_DEFAULT_BUTTON_SIZE_PX, THOR_BOTTOM_CANVAS_SPEC.baseButtonSizePx, 0.001f)
        assertEquals(MP_THOR_LARGE_CONTROL_MIN_SIZE_PX, THOR_BOTTOM_CANVAS_SPEC.largeControlMinSizePx, 0.001f)

        val duoSpec = LayoutCanvasSpec(widthPx = 1280, heightPx = 960)
        assertEquals(960, duoSpec.shortEdgePx)
        assertEquals(60f, duoSpec.minButtonSizePx, 0.001f)
        assertEquals(120f, duoSpec.baseButtonSizePx, 0.001f)
        assertEquals(240f, duoSpec.largeControlMinSizePx, 0.001f)

        val tinySpec = LayoutCanvasSpec(widthPx = 100, heightPx = 80)
        assertEquals(MP_BUTTON_SERIALIZER_FLOOR_PX, tinySpec.minButtonSizePx, 0.001f)
    }

    @Test
    fun `modern PadButton with stepless pixel dimensions survives JSON round-trip`() {
        val button =
            PadButton(
                id = "btn-stepless-1",
                posX = 0.35f,
                posY = 0.65f,
                label = "Custom",
                action = PadAction.KeyboardKey(30, "A"),
                widthPx = 185f,
                heightPx = 240f,
            )

        val encoded = json.encodeToString(button)
        // Verify widthPx and heightPx are present, and legacy widthDp/heightDp/buttonSize are NOT present
        assertTrue(encoded.contains("\"widthPx\":185.0") || encoded.contains("\"widthPx\":185"))
        assertTrue(encoded.contains("\"heightPx\":240.0") || encoded.contains("\"heightPx\":240"))
        assertFalse(encoded.contains("\"widthDp\""))
        assertFalse(encoded.contains("\"heightDp\""))
        assertFalse(encoded.contains("\"buttonSize\""))

        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(button, decoded)

        // RP Duo minimum 60 px button survives raw JSON decode without being clamped up to Thor's 68 px
        val duoMinButton = button.copy(id = "btn-duo-min", widthPx = 60f, heightPx = 60f)
        val decodedDuoMin = json.decodeFromString<PadButton>(json.encodeToString(duoMinButton))
        assertEquals(60f, decodedDuoMin.widthPx, 0.001f)
        assertEquals(60f, decodedDuoMin.heightPx, 0.001f)
    }

    @Test
    fun `legacy widthDp and heightDp migrate via THOR_BOTTOM_DENSITY rounded to integer pixels`() {
        val cases =
            listOf(
                30f to 69f,
                60f to 138f,
                90f to 208f,
                120f to 277f,
                180f to 415f,
            )
        for ((dp, expectedPx) in cases) {
            val legacyJson =
                """
                {
                    "id": "btn-dp-$dp",
                    "posX": 0.5,
                    "posY": 0.5,
                    "label": "Legacy",
                    "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                    "widthDp": $dp,
                    "heightDp": $dp
                }
                """.trimIndent()
            val decoded = json.decodeFromString<PadButton>(legacyJson)
            assertEquals(expectedPx, decoded.widthPx, 0.001f)
            assertEquals(expectedPx, decoded.heightPx, 0.001f)
        }
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_2X1 deserializes to 277x138 px`() {
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
        assertEquals(277f, decoded.widthPx, 0.01f)
        assertEquals(138f, decoded.heightPx, 0.01f)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_1X2 deserializes to 138x277 px`() {
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
        assertEquals(138f, decoded.widthPx, 0.01f)
        assertEquals(277f, decoded.heightPx, 0.01f)
    }

    @Test
    fun `legacy JSON with ButtonSize SIZE_2X2 deserializes to 277x277 px`() {
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
        assertEquals(277f, decoded.widthPx, 0.01f)
        assertEquals(277f, decoded.heightPx, 0.01f)
    }

    @Test
    fun `legacy TrackpointMove action migrates size to widthPx heightPx and CIRCLE shape`() {
        val trackpointSmallJson =
            """
            {
                "id": "btn-trackpoint-small",
                "posX": 0.5,
                "posY": 0.5,
                "label": "TP Small",
                "buttonShape": "SQUARE",
                "action": {
                    "type": "trackpoint",
                    "size": "SMALL"
                }
            }
            """.trimIndent()

        val decodedSmall = json.decodeFromString<PadButton>(trackpointSmallJson)
        assertEquals(208f, decodedSmall.widthPx, 0.01f)
        assertEquals(208f, decodedSmall.heightPx, 0.01f)
        assertEquals(ButtonShape.CIRCLE, decodedSmall.buttonShape)

        val trackpointMediumJson =
            """
            {
                "id": "btn-trackpoint-medium",
                "posX": 0.5,
                "posY": 0.5,
                "label": "TP Medium",
                "action": {
                    "type": "trackpoint",
                    "size": "MEDIUM"
                }
            }
            """.trimIndent()

        val decodedMedium = json.decodeFromString<PadButton>(trackpointMediumJson)
        assertEquals(277f, decodedMedium.widthPx, 0.01f)
        assertEquals(277f, decodedMedium.heightPx, 0.01f)
        assertEquals(ButtonShape.CIRCLE, decodedMedium.buttonShape)

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
        assertEquals(415f, decodedLarge.widthPx, 0.01f)
        assertEquals(415f, decodedLarge.heightPx, 0.01f)
        assertEquals(ButtonShape.CIRCLE, decodedLarge.buttonShape)
    }

    @Test
    fun `legacy JSON without buttonSize or dimensions defaults to 138x138 px`() {
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
        assertEquals(138f, decoded.widthPx, 0.01f)
        assertEquals(138f, decoded.heightPx, 0.01f)
    }

    @Test
    fun `dimensions below floor or negative are clamped to MP_BUTTON_SERIALIZER_FLOOR_PX`() {
        val modernJsonStr =
            """
            {
                "id": "btn-too-small-px",
                "label": "Small Px",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                "widthPx": 8.0,
                "heightPx": -50.0
            }
            """.trimIndent()

        val decodedModern = json.decodeFromString<PadButton>(modernJsonStr)
        assertEquals(MP_BUTTON_SERIALIZER_FLOOR_PX, decodedModern.widthPx, 0.001f)
        assertEquals(MP_BUTTON_SERIALIZER_FLOOR_PX, decodedModern.heightPx, 0.001f)

        val legacyJsonStr =
            """
            {
                "id": "btn-too-small-dp",
                "label": "Small Dp",
                "posX": 0.5,
                "posY": 0.5,
                "action": { "type": "keyboard_key", "keycode": 30, "label": "A" },
                "widthDp": 2.0,
                "heightDp": -50.0
            }
            """.trimIndent()

        val decodedLegacy = json.decodeFromString<PadButton>(legacyJsonStr)
        assertEquals(MP_BUTTON_SERIALIZER_FLOOR_PX, decodedLegacy.widthPx, 0.001f)
        assertEquals(MP_BUTTON_SERIALIZER_FLOOR_PX, decodedLegacy.heightPx, 0.001f)
    }

    @Test
    fun `partial dimensions deserialize with missing dimension falling back to legacy base unit`() {
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
        assertEquals(208f, decoded.widthPx, 0.001f)
        assertEquals(138f, decoded.heightPx, 0.001f)
    }

    @Test
    fun `trackpoint button normalizes non-square dimensions to equal max size and CIRCLE shape`() {
        val jsonStr =
            """
            {
                "id": "btn-trackpoint-nonsquare",
                "label": "TP",
                "posX": 0.5,
                "posY": 0.5,
                "buttonShape": "SQUARE",
                "action": {
                    "type": "trackpoint",
                    "mode": "virtual_touch"
                },
                "widthPx": 200.0,
                "heightPx": 300.0
            }
            """.trimIndent()

        val decoded = json.decodeFromString<PadButton>(jsonStr)
        assertEquals(300f, decoded.widthPx, 0.001f)
        assertEquals(300f, decoded.heightPx, 0.001f)
        assertEquals(ButtonShape.CIRCLE, decoded.buttonShape)
    }

    @Test
    fun `dimensions with Infinity or NaN deserialize to legacy fallback 138 px`() {
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
                widthPx = Float.POSITIVE_INFINITY,
                heightPx = Float.NEGATIVE_INFINITY,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val encoded = specialJson.encodeToString(button)
        val decoded = specialJson.decodeFromString<PadButton>(encoded)
        assertEquals(138f, decoded.widthPx, 0.001f)
        assertEquals(138f, decoded.heightPx, 0.001f)

        val nanButton =
            PadButton(
                id = "btn-nan",
                label = "NaN",
                posX = 0.5f,
                posY = 0.5f,
                widthPx = Float.NaN,
                heightPx = Float.NaN,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val nanEncoded = specialJson.encodeToString(nanButton)
        val nanDecoded = specialJson.decodeFromString<PadButton>(nanEncoded)
        assertEquals(138f, nanDecoded.widthPx, 0.001f)
        assertEquals(138f, nanDecoded.heightPx, 0.001f)
    }

    @Test
    fun `withActionSizing for ScrollWheel enforces canvas largeControlMinSizePx on Thor and RP Duo`() {
        val button =
            PadButton(
                id = "btn-sw",
                label = "SW",
                posX = 0.5f,
                posY = 0.5f,
                widthPx = 136f,
                heightPx = 136f,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val updatedThor = button.withActionSizing(PadAction.ScrollWheel, THOR_BOTTOM_CANVAS_SPEC)
        assertEquals(136f, updatedThor.widthPx, 0.001f)
        assertEquals(MP_THOR_LARGE_CONTROL_MIN_SIZE_PX, updatedThor.heightPx, 0.001f)

        val duoSpec = LayoutCanvasSpec(1280, 960)
        val duoButton = button.copy(widthPx = 120f, heightPx = 120f)
        val updatedDuo = duoButton.withActionSizing(PadAction.ScrollWheel, duoSpec)
        assertEquals(120f, updatedDuo.widthPx, 0.001f)
        assertEquals(240f, updatedDuo.heightPx, 0.001f)

        // Preserves taller height if already >= largeControlMinSizePx
        val tallButton = button.copy(heightPx = 400f)
        val updatedTall = tallButton.withActionSizing(PadAction.ScrollWheel, THOR_BOTTOM_CANVAS_SPEC)
        assertEquals(400f, updatedTall.heightPx, 0.001f)
    }

    @Test
    fun `withActionSizing for TrackpointMove enforces 1 to 1 aspect ratio CIRCLE shape and canvas largeControlMinSizePx`() {
        val button =
            PadButton(
                id = "btn-tp",
                label = "TP",
                posX = 0.5f,
                posY = 0.5f,
                widthPx = 136f,
                heightPx = 180f,
                buttonShape = ButtonShape.SQUARE,
                action = PadAction.KeyboardKey(30, "A"),
            )
        val updatedThor = button.withActionSizing(PadAction.TrackpointMove(), THOR_BOTTOM_CANVAS_SPEC)
        assertEquals(MP_THOR_LARGE_CONTROL_MIN_SIZE_PX, updatedThor.widthPx, 0.001f)
        assertEquals(MP_THOR_LARGE_CONTROL_MIN_SIZE_PX, updatedThor.heightPx, 0.001f)
        assertEquals(ButtonShape.CIRCLE, updatedThor.buttonShape)

        val duoSpec = LayoutCanvasSpec(1280, 960)
        val duoButton = button.copy(widthPx = 120f, heightPx = 160f)
        val updatedDuo = duoButton.withActionSizing(PadAction.TrackpointMove(), duoSpec)
        assertEquals(240f, updatedDuo.widthPx, 0.001f)
        assertEquals(240f, updatedDuo.heightPx, 0.001f)
        assertEquals(ButtonShape.CIRCLE, updatedDuo.buttonShape)

        // Expands both dimensions to the larger one if > largeControlMinSizePx
        val largeButton = button.copy(widthPx = 360f, heightPx = 200f)
        val updatedLarge = largeButton.withActionSizing(PadAction.TrackpointMove(), THOR_BOTTOM_CANVAS_SPEC)
        assertEquals(360f, updatedLarge.widthPx, 0.001f)
        assertEquals(360f, updatedLarge.heightPx, 0.001f)
    }

    @Test
    fun `withActionSizing for KeyboardKey leaves existing dimensions and shape unchanged`() {
        val button =
            PadButton(
                id = "btn-norm",
                label = "K",
                posX = 0.5f,
                posY = 0.5f,
                widthPx = 175f,
                heightPx = 215f,
                buttonShape = ButtonShape.ICON_ONLY,
                action = PadAction.ScrollWheel,
            )
        val updated = button.withActionSizing(PadAction.KeyboardKey(30, "A"))
        assertEquals(175f, updated.widthPx, 0.001f)
        assertEquals(215f, updated.heightPx, 0.001f)
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

        assertNull(PadAction.SwitchToLayout("layout-1", holdToShift = false).defaultIconName())
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
    fun `TouchTap action serializes and deserializes with label-first null defaultIconName and legacy backward compatibility`() {
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
        assertTrue((decoded.action as PadAction.TouchTap).isRecorded)
        assertNull(PadAction.TouchTap(0.25f, 0.75f).defaultIconName())

        // Legacy saved payload without isRecorded must decode with isRecorded = true
        val legacyPayload =
            """{"id":"btn-legacy-tap","label":"Legacy Tap","posX":0.5,"posY":0.5,"widthDp":60.0,"heightDp":60.0,"action":{"type":"touch_tap","normX":0.4,"normY":0.6}}"""
        val decodedLegacy = json.decodeFromString<PadButton>(legacyPayload)
        assertEquals(PadAction.TouchTap(normX = 0.4f, normY = 0.6f, isRecorded = true), decodedLegacy.action)

        // Unrecorded draft round-trips isRecorded = false
        val unrecordedButton = tapButton.copy(action = PadAction.TouchTap(isRecorded = false))
        val decodedUnrecorded = json.decodeFromString<PadButton>(json.encodeToString(unrecordedButton))
        assertEquals(unrecordedButton, decodedUnrecorded)
    }

    @Test
    fun `SingleTouchGesture action serializes multi-finger samples with label-first null defaultIconName`() {
        val samples =
            listOf(
                TouchSample(offsetMs = 0L, pointerId = 0, action = TouchAction.DOWN, normX = 0.2f, normY = 0.3f),
                TouchSample(offsetMs = 15L, pointerId = 1, action = TouchAction.DOWN, normX = 0.8f, normY = 0.3f),
                TouchSample(offsetMs = 50L, pointerId = 0, action = TouchAction.MOVE, normX = 0.3f, normY = 0.4f),
                TouchSample(offsetMs = 50L, pointerId = 1, action = TouchAction.MOVE, normX = 0.7f, normY = 0.4f),
                TouchSample(offsetMs = 90L, pointerId = 0, action = TouchAction.UP, normX = 0.3f, normY = 0.4f),
                TouchSample(offsetMs = 100L, pointerId = 1, action = TouchAction.UP, normX = 0.7f, normY = 0.4f),
            )
        val gestureButton =
            PadButton(
                id = "btn-single-gesture",
                label = "Pinch",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.SingleTouchGesture(samples = samples),
            )
        val encoded = json.encodeToString(gestureButton)
        assertTrue(encoded.contains("\"type\":\"single_touch_gesture\""))
        val decoded = json.decodeFromString<PadButton>(encoded)
        assertEquals(gestureButton, decoded)
        assertNull(PadAction.SingleTouchGesture(samples = samples).defaultIconName())

        val emptyDefaultPayload =
            """{"id":"btn-empty-gesture","label":"Gesture","posX":0.5,"posY":0.5,"widthDp":60.0,"heightDp":60.0,"action":{"type":"single_touch_gesture"}}"""
        val decodedDefault = json.decodeFromString<PadButton>(emptyDefaultPayload)
        assertEquals(PadAction.SingleTouchGesture(samples = emptyList()), decodedDefault.action)
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
    fun `SwitchToProfile action serializes and deserializes with label-first null defaultIconName`() {
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

        assertNull(PadAction.SwitchToProfile("profile-1").defaultIconName())
    }

    @Test
    fun `non-fixed MouseButton GamepadButton and KeyboardKey actions map unambiguous default icons and null chords`() {
        // MouseButton: Left & Right click get dedicated glyphs; Middle/M4/M5 remain text-first
        assertEquals("left_click", PadAction.MouseButton(MouseButton.LEFT).defaultIconName())
        assertEquals("right_click", PadAction.MouseButton(MouseButton.RIGHT).defaultIconName())
        assertNull(PadAction.MouseButton(MouseButton.MIDDLE).defaultIconName())
        assertNull(PadAction.MouseButton(MouseButton.MOUSE4).defaultIconName())
        assertNull(PadAction.MouseButton(MouseButton.MOUSE5).defaultIconName())

        // GamepadButton: Home & 8-way D-Pad get glyphs only when extraBtnCodes is empty
        assertEquals("home", PadAction.GamepadButton(GamepadKeycodes.BTN_MODE, "Home").defaultIconName())
        assertEquals("arrow_upward", PadAction.GamepadButton(GamepadKeycodes.BTN_DPAD_UP, "D-Up").defaultIconName())
        assertEquals("arrow_downward", PadAction.GamepadButton(GamepadKeycodes.BTN_DPAD_DOWN, "D-Down").defaultIconName())
        assertEquals("arrow_back", PadAction.GamepadButton(GamepadKeycodes.BTN_DPAD_LEFT, "D-Left").defaultIconName())
        assertEquals("arrow_forward", PadAction.GamepadButton(GamepadKeycodes.BTN_DPAD_RIGHT, "D-Right").defaultIconName())
        assertEquals("north_west", PadAction.GamepadButton(GamepadKeycodes.CODE_DPAD_UP_LEFT, "D-UL").defaultIconName())
        assertEquals("north_east", PadAction.GamepadButton(GamepadKeycodes.CODE_DPAD_UP_RIGHT, "D-UR").defaultIconName())
        assertEquals("south_west", PadAction.GamepadButton(GamepadKeycodes.CODE_DPAD_DOWN_LEFT, "D-DL").defaultIconName())
        assertEquals("south_east", PadAction.GamepadButton(GamepadKeycodes.CODE_DPAD_DOWN_RIGHT, "D-DR").defaultIconName())
        assertNull(
            PadAction
                .GamepadButton(
                    btnCode = GamepadKeycodes.BTN_DPAD_UP,
                    label = "D-Up",
                    extraBtnCodes = listOf(GamepadKeycodes.BTN_SOUTH),
                ).defaultIconName(),
        )
        assertNull(PadAction.GamepadButton(GamepadKeycodes.BTN_SOUTH, "A").defaultIconName())
        assertNull(PadAction.GamepadButton(GamepadKeycodes.CODE_LS_UP, "LS Up").defaultIconName())

        // KeyboardKey: Single special/nav keys get glyphs; chords and character keys stay null
        assertEquals("keyboard_return", PadAction.KeyboardKey(LinuxKeycodes.KEY_ENTER, "Enter").defaultIconName())
        assertEquals("backspace", PadAction.KeyboardKey(LinuxKeycodes.KEY_BACKSPACE, "⌫").defaultIconName())
        assertEquals("keyboard_tab", PadAction.KeyboardKey(LinuxKeycodes.KEY_TAB, "Tab").defaultIconName())
        assertEquals("space_bar", PadAction.KeyboardKey(LinuxKeycodes.KEY_SPACE, "Space").defaultIconName())
        assertEquals("keyboard_capslock", PadAction.KeyboardKey(LinuxKeycodes.KEY_CAPSLOCK, "Caps").defaultIconName())
        assertEquals("keyboard_arrow_up", PadAction.KeyboardKey(LinuxKeycodes.KEY_UP, "↑").defaultIconName())
        assertEquals("keyboard_arrow_down", PadAction.KeyboardKey(LinuxKeycodes.KEY_DOWN, "↓").defaultIconName())
        assertEquals("keyboard_arrow_left", PadAction.KeyboardKey(LinuxKeycodes.KEY_LEFT, "←").defaultIconName())
        assertEquals("keyboard_arrow_right", PadAction.KeyboardKey(LinuxKeycodes.KEY_RIGHT, "→").defaultIconName())
        assertEquals("keyboard_double_arrow_up", PadAction.KeyboardKey(LinuxKeycodes.KEY_PAGEUP, "PgUp").defaultIconName())
        assertEquals("keyboard_double_arrow_down", PadAction.KeyboardKey(LinuxKeycodes.KEY_PAGEDOWN, "PgDn").defaultIconName())
        assertNull(
            PadAction
                .KeyboardKey(
                    keycode = LinuxKeycodes.KEY_SPACE,
                    label = "Space",
                    modifiers = listOf(LinuxKeycodes.KEY_LEFTCTRL),
                ).defaultIconName(),
        )
        assertNull(PadAction.KeyboardKey(LinuxKeycodes.KEY_W, "W").defaultIconName())
        assertNull(PadAction.KeyboardKey(LinuxKeycodes.KEY_F5, "F5").defaultIconName())
        assertNull(PadAction.Macro("m-1").defaultIconName())
    }

    @Test
    fun `supportsCustomIcon and withActionSizing clear iconName on fixed-face actions`() {
        val base =
            PadButton(
                id = "btn-icon",
                label = "Test",
                iconName = "star",
                posX = 0.5f,
                posY = 0.5f,
                action = PadAction.LayoutNext,
            )

        assertFalse(PadAction.ScrollWheel.supportsCustomIcon())
        assertFalse(PadAction.TrackpointMove().supportsCustomIcon())
        assertFalse(PadAction.BackgroundPeek.supportsCustomIcon())
        assertFalse(PadAction.AppLauncher("com.example").supportsCustomIcon())
        assertTrue(PadAction.LayoutNext.supportsCustomIcon())
        assertTrue(PadAction.SwitchToProfile("p1").supportsCustomIcon())

        assertNull(base.withActionSizing(PadAction.ScrollWheel).iconName)
        assertNull(base.withActionSizing(PadAction.TrackpointMove()).iconName)
        assertNull(base.withActionSizing(PadAction.BackgroundPeek).iconName)
        assertNull(base.withActionSizing(PadAction.AppLauncher("com.example")).iconName)
        assertEquals("star", base.withActionSizing(PadAction.SwitchToHub).iconName)
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

    @Test
    fun `defaultSwitchTargetId prefers first non-active entity and falls back cleanly`() {
        val p1 = PadProfile(id = "p-1", name = "One")
        val p2 = PadProfile(id = "p-2", name = "Two")
        val p3 = PadProfile(id = "p-3", name = "Three")

        assertEquals("p-2", listOf(p1, p2, p3).defaultSwitchTargetId(activeProfileId = "p-1"))
        assertEquals("p-1", listOf(p1, p2, p3).defaultSwitchTargetId(activeProfileId = "p-2"))
        assertEquals("p-1", listOf(p1, p2, p3).defaultSwitchTargetId(activeProfileId = null))
        assertEquals("p-1", listOf(p1).defaultSwitchTargetId(activeProfileId = "p-1"))
        assertEquals("", emptyList<PadProfile>().defaultSwitchTargetId(activeProfileId = "p-1"))

        val l1 = PadLayout(id = "l-1", name = "Base")
        val l2 = PadLayout(id = "l-2", name = "Alt")
        assertEquals("l-2", listOf(l1, l2).defaultSwitchTargetId(activeLayoutId = "l-1"))
        assertEquals("l-1", listOf(l1, l2).defaultSwitchTargetId(activeLayoutId = "l-2"))
        assertEquals("l-1", listOf(l1).defaultSwitchTargetId(activeLayoutId = "l-1"))
        assertEquals("", emptyList<PadLayout>().defaultSwitchTargetId(activeLayoutId = "l-1"))
    }
}
