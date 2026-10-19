# Feature: MacroPad — Background, Placement & Images

> Part 3 of 6 of the MacroPad feature spec. Start at [FEATURE.md](FEATURE.md) for the overview and document map.

---

## Functional Requirements (continued)

### FR-P9: Background Display

- **Theme-Invariant Pitch Black Plain Background:** The plain canvas background of a MacroPad (when no custom background image is configured) MUST always be pitch black (`Color.Black`), completely invariant to the active theme palette. Regardless of whether Dark, Dark OLED, Norse (`megingiard`), Mjolnir, Valkyrie, Bifrost, Yggdrasil, or Odin Mono is active, the MacroPad canvas base background does not adopt theme background colors (`appBackground`) and remains solid pitch black across Use Mode (`PadSurface`), Editor Mode (`PadCanvas`), and behind embedded mirror cutouts (`EmbeddedMirrorView`).
- An optional **Background Display** mode renders the Screen Mirror output behind the MacroPad buttons on the secondary display via `EmbeddedMirrorView`.
- Configured via the **Mirror** sidebar deck (`EditorSection.MIRROR`, `MirrorSettingsEditor.kt`).
- When Background Display is enabled and the user enters MacroPad mode, `ScreenCaptureService` is **automatically started** (identical to how Mirror mode auto-starts when that setting is active). The user is prompted for MediaProjection consent if not already capturing. Declining within a session is respected until the next mode entry.
- When background display is enabled and capturing is active, `MacroPadScreen` renders `EmbeddedMirrorView` at Layer 0 underneath `PadSurface(transparentBackground = true)`.
- In background display mode, the **Quick Menu Bar** MUST remain visible on the secondary display, and edge swipes from the configured bar edge MUST open/close the Quick Menu so users can always access navigation/actions even if no mirror-control button exists in the current layout.
- **Per-layout mirror & background settings:** Dimming parameters and mirror cutouts are stored **per layout** in `PadLayout` (not globally).
- **Mirror Deck (`MirrorSettingsEditor.kt`):** Configures the active layout's mirror cutouts, dimming parameters, edge blending (`GamepadSliderCard`), layout-wide touch tracking, and per-cutout smoothing settings.
- **Dimming** (0–90%, default 0%): Configured via a `GamepadSliderCard` that draws a semi-transparent black overlay on top of the mirror background.
- A special **Background Peek** action (`PadAction.BackgroundPeek`): toggles hiding all buttons and dimming.
- When the capture service is not running and ambient is enabled, the MacroPad falls back to its normal opaque rendering on the primary display.
- **Per-layout button color defaults** (`PadLayout.buttonTextColor` / `PadLayout.buttonBorderColor` / `PadLayout.buttonBgColor`): Each layout can independently configure the default colors for text/icon, border, and fading color.
  - Colors are stored using the `ColorOption` schema, which supports **Neutral Style** (neutral theme-independent palette), **Accent Color** (dynamically resolved system accent color), or a **Custom Color** (fixed ARGB).
  - Configured through dedicated sub-menus in the gamepad navigation hierarchy:
    - In **Layout Settings** (`EditLayoutSubPageContent`): 3 menu items (Text, Border, Background) lead to dedicated color selection sub-pages (`LayoutColorSubPageContent`) with live preview and color wheel integration, alongside Touchpad Settings navigation and a layout deletion card.
    - In **Button Settings** (`EditButtonSubPageContent`): 3 menu items (Text, Border, Background) lead to dedicated color selection sub-pages (`ButtonColorSubPageContent`) supporting Layout Default (`null`), Neutral, Accent, and Custom color options.
    - **Swords / Button Preview & Live Bottom-Screen In-Flight Preview**: Parent editor screens (`EditLayoutSubPageContent` and `EditButtonSubPageContent`) feature a dedicated `ColorPreviewInfoBox` above the save button displaying saved baseline style vs in-flight changes side-by-side with an arrow indicator. Color save sections use the unified two-step confirmation row (`GamepadSaveExitActionRow`) with a primary **Confirm** action and back-intercepted **Save & Exit** / **Discard & Exit** options. Furthermore, all in-flight button adjustments (size, shape, label, action, icon, visibility, and color options or slider drags in the Color Wheel) are streamed immediately to the active draft and the secondary bottom display (`PadCanvas`) in real time via transient preview state (`MacroPadState.previewLayout` / `setPreviewButton`), showing the exact visual result without requiring the user to save settings first.
    - **Streamlined Sub-Page Pickers (Zero Sub-Page Save Buttons) & Color Wheel Undo**: Child sub-pages (`ColorWheelSubPageContent`, `LayoutColorSubPageContent`, `ButtonColorSubPageContent`) act as direct color inspectors: selections and slider movements auto-apply directly into the active draft without intermediate confirm/save buttons. Inside the Color Wheel, an **"Undo Color Changes"** action card allows reverting Hue, Saturation, Brightness, and Opacity back to the initial color selected upon entering the sub-menu while staying on the screen. Pressing Back `(B)` pops cleanly to the parent editor, where the single master confirm/save action persists all draft changes to DataStore.
  - **Opacity Slider & Direct Resting Opacity**: The custom color picker wheel incorporates an opacity/alpha slider that operates in the 10% to 100% range (`0.1f..1.0f`). Any values parsed or configured are clamped to this range. User-defined opacity directly governs the outer edge peak opacity of the button radial gradient (`bgColor.alpha`) at rest, without artificial secondary resting dimming factors. Neutral and Accent background color styles default to 70% opacity (`0.70f`), matching the established resting appearance. Legacy profiles with pre-opacity full-opacity custom background colors (`0xFF` / `1.0f`) are automatically migrated on load to `70%` (`0xB3`) opacity to preserve visual consistency.
  - **Luminance-aware Apply Button**: The "Apply" button inside the color picker dynamically changes its text color to black or white based on the luminance and transparency of the currently picked color, ensuring maximum readability.
  - **Invisible Buttons**: Both the layout settings editor and the button editor include an "Invisible Buttons" switch toggle.
    - Layout-level `invisibleButtons` acts as a default template option: when active, newly created buttons in that layout will default to having their individual `invisible` property enabled.
    - Button-level `invisible` property controls the visibility of the button in Use Mode. When true, the button is completely hidden (visually transparent) but remains fully interactive under touch input. In editing mode, the button remains visible (with the button body rendered at 40% opacity and overlaid with a small crossed-out eye in the top right corner at 100% opacity for clear distinction) so it can be customized and repositioned.
  - **Button-level overrides**: Each button can override the layout-wide color defaults individually using the same `ColorOption` fields (`PadButton.buttonTextColor`, `PadButton.buttonBorderColor`, `PadButton.buttonBgColor` for fading color). A special `null` value (shown as **Layout Default**) reverts the button back to the layout-wide default behavior.
  - Color options survive profile imports/exports and migrate legacy button color formats (`buttonColorNoMirror` / `buttonColorMirror`) automatically.


### FR-P8b: Position & Size Buttons & Precision Movement & Resizing

- **Sub-Menu Navigation:** Accessible from the **Position & Size Buttons** menu item in the Buttons category deck (`MacroPadSubPage.EditButtonPositions`).
- **Live Canvas Unlocking:** Entering the sub-menu automatically activates `MacroPadState.isEditingButtonPositions = true`, rendering the active 5dp-rounded highlight border around the bottom-screen `PadCanvas` and unlocking touch dragging and resizing. Leaving the sub-menu or closing the editor restores locked mode (`false`).
- **Instructional Info Banner:** A non-highlightable info box at the top of the sub-menu informs the user that buttons can be dragged or resized directly on the bottom display via touch or nudged with pixel precision via D-pad or Left Stick (holding R2 to resize, holding L2 for fine 1px / 1dp steps).
- **Button Selection & Dual-Screen Sync:** The sub-menu lists all buttons with their live dimensions as integer dp values (e.g. `60×60`, `61×60`, `75×120`). Focusing or moving a button sets `MacroPadState.selectedButtonId`, which immediately renders 4 directional edge resize handles (TOP, BOTTOM, LEFT, RIGHT) on the corresponding button on the bottom display. Touching any button or edge handle directly on the bottom canvas sets `MacroPadState.selectedButtonId`, which automatically focuses and brings that button into view in the top screen's list.
- **Stepless Touch Resizing & 1:1 Aspect Ratio Magnetic Snapping:**
  - Dragging the button's interior surface moves the button freely across the canvas; its center coordinate is clamped to the full canvas range `[0.0, 1.0]` (`BUTTON_CANVAS_EDGE_MARGIN = 0f`), allowing buttons to hang up to 50% off-screen on any edge.
  - Dragging any of the 4 edge resize handles adjusts that dimension while anchoring the opposite edge (e.g. dragging the RIGHT handle extends width rightward while keeping the left edge fixed; dragging the TOP handle extends height upward while keeping the bottom edge fixed).
  - Both dimensions are clamped to a minimum size of 30 dp (`MP_BUTTON_MIN_SIZE_DP = 30f`) and maximum canvas bounds, snapping to integer dp increments. When a canvas bound is hit, the dimension is floored (never rounded up) so the button can never exceed the canvas, and the button center is derived from the final integer dp value so the anchored edge stays exactly fixed. For buttons positioned partially off-screen, resizing handles and aspect-locked adjustments preserve the off-screen overhang without forcibly snapping or shrinking the button back on screen. Center clamping is range-safe (`[0, canvasSize]`).
  - **1:1 Aspect Ratio Snap:** While dragging an edge handle, when the dimension comes within 4 dp (`BUTTON_ASPECT_SNAP_THRESHOLD_DP = 4f`) of the opposing dimension (width == height), it magnetically locks to a perfect 1:1 aspect ratio. When first locking into the 1:1 snap, the system fires a subtle haptic tick via `LocalHapticFeedback`.
  - Buttons with `ButtonShape.CIRCLE` render as a circle when width equals height, and automatically morph into a smooth pill shape (`PBF_PILL_SHAPE`) when width differs from height. Buttons with `ButtonShape.ROUNDED_SQUARE` use a 25% corner radius (`PBF_ROUNDED_SQUARE_SHAPE`), and polygonal button shapes (`HEXAGON_1`, `HEXAGON_2`, `PENTAGON_1`, `PENTAGON_2`, `OCTAGON`, `DIAMOND`, `TRIANGLE_UP`, `TRIANGLE_DOWN`, `TRIANGLE_LEFT`, `TRIANGLE_RIGHT`) scale their normalized vertices across `widthDp × heightDp`.
  - Text labels scale with button width (11 sp per 60 dp) but are capped by button height (`MP_BTN_LABEL_MAX_HEIGHT_RATIO = 0.4` sp per dp) and clamped to 8–32 sp, so wide/flat buttons never clip their label. Legacy sizes render unchanged (1×1 → 11 sp, 2×1 → 22 sp).
- **Floating Dimension HUD (`ButtonDimensionHud`):**
  - While any button is actively being moved, resized via touch edge handles, or adjusted via gamepad, a semi-transparent floating HUD badge displaying live dimensions (`80×60` or `81×60`, localized via `macropad_button_dimension_format`) renders centered above the active button chip on the bottom canvas (`PadCanvas`).
  - The HUD is also shown on the locked canvas while a button is adjusted via the **Adjust Position & Size** card in `PadButtonEditDialog` (`MacroPadState.isAdjustingButtonViaGamepad`); in that mode it is offset past the highlight pointers so they never overlap.
  - If the button is positioned close to the top boundary of the canvas, the HUD automatically flips below the button to prevent edge clipping.
- **Gamepad Movement, Resizing & Dynamic Pill:**
  - Pressing `(A)` or clicking on any button in the list activates **Movement Mode** (`isMoving = true`).
  - While active, the item trailing pill dynamically displays **Resizing** when holding **R2** (`KEYCODE_BUTTON_R2`) versus **Moving** when holding D-pad directions.
  - Directional inputs (`D-Pad Up/Down/Left/Right` or `Left Stick`) move the button in default **10 px steps** (`MPE_NORMAL_STEP_PX = 10f`) for rapid positioning.
  - Holding **R2** (`KEYCODE_BUTTON_R2`) switches to **Resizing Mode**: D-pad directions scale width and height symmetrically around the button's center in default **5 dp steps** (`MPE_RESIZE_NORMAL_STEP_DP = 5f`) for rapid sizing; center coordinates are preserved when resizing partially off-screen buttons without jumping back onto the canvas.
  - Holding **L2** (`KEYCODE_BUTTON_L2`) switches to fine-tuned precision steps: **1 px steps** (`MPE_FINE_STEP_PX = 1f`) for movement and **1 dp steps** (`MPE_RESIZE_FINE_STEP_DP = 1f`) for resizing.
  - Holding a direction down continuously accelerates the tick frequency (starting at 250 ms initial delay, ramping down to 16 ms intervals / ~60 Hz) for smooth, high-precision positioning and sizing.
  - **Crossing Alignment Snap**: When "Snap to Alignment" is enabled and stepping at 10 px, if moving would jump past another button's center coordinate on the active axis, it snaps precisely to that alignment coordinate first. A subsequent press in the same direction steps off the snapped position.
  - Pressing `(B)`, `(A)`, `Enter`, or back navigation deactivates movement mode, returning control to standard card navigation without bubbling back to the parent screen.
- **Direct Repositioning & Sizing in Button Config (`PadButtonEditDialog`):**
  - Inside the button edit dialog, the "Shape & Size" section features an **Adjust Position & Size** card (`GamepadFocusCard`).
  - Tapping `(A)` activates live adjustment mode, highlighting the card and setting `MacroPadState.isAdjustingButtonViaGamepad`.
  - The card displays live dimensions and normalized coordinates (`e.g. 60×60 • X: 50% Y: 50%` or `61×60 • X: 50% Y: 50%`) with live D-pad move/resize (and L2/R2 modifiers) updating the bottom-screen preview in real time.

### FR-P9a: Custom Background Image

- Users can choose a **custom background image** to be displayed behind the MacroPad buttons and underneath screen mirror cutouts.
- The configuration is situated in the **Background** section of the layout editor (`LayoutBackgroundSubPageContent`).
- Tapping "Browse Local" opens the system document picker (`image/*`), while "Search SteamGridDB" allows scraping game artwork directly.
- **Dual-Screen Live In-Flight Preview:** Choosing a background image, adjusting the dimming slider, clearing/deleting the image, or adjusting crop pan/zoom transformations streams in-flight preview state (`MacroPadState.previewLayout`) immediately to the secondary bottom display (`PadCanvas`) in real time, allowing users to see the exact composition before saving.
- **In-Place Crop Toggle & Bottom-Screen Gestures:** Tapping the "Crop" toggle card activates cropping mode directly (`MacroPadState.isCroppingBackground = true`). While active:
  - The bottom screen (`PadCanvas`) is highlighted with an accent color border (matching button move mode).
  - Button dragging/interaction is disabled on the bottom screen so users can freely pinch to zoom (100%–500%) and drag to pan the background image directly on the secondary display.
  - Scale and offset transformations update `MacroPadState.previewLayout` in real time, clamping translations within maximum aspect-fill pan bounds via `ViewportMath.getMaxOffsets`.
  - Toggling crop mode off or exiting preserves the adjusted crop values in the in-flight draft until confirmed or discarded.
- **In-Flight Exit Confirmation:** `LayoutBackgroundSubPageContent` uses `rememberSaveExitPromptState` and `GamepadSaveExitActionRow`. When unsaved adjustments exist and the user attempts to exit (via Gamepad B button, Escape, or system Back gesture), navigation is intercepted: the Save button splits dynamically into side-by-side **Save & Exit** (accent highlighted) and **Discard & Exit** (destructive) buttons with autofocus.
- To prevent permissions from expiring and keep layouts self-contained, the chosen image is copied to the app's internal files directory as `backgrounds/bg_<layoutId>`.
- The `backgroundImagePath` parameter in `PadLayout` stores a relative path (e.g. `backgrounds/bg_<layoutId>`) along with `bgImageScale`, `bgImageOffsetX`, and `bgImageOffsetY` crop properties to maintain portability and compatibility with Megingiard's profile import/export features.
- In **Use Mode** (`PadSurface`), **Layout Editor** (`PadCanvas`), and **Embedded Mirror** (`EmbeddedMirrorView`), the image is loaded asynchronously and rendered applying the layout's background image crop settings (scale and translations) behind the buttons.
- When a layout is deleted or its background image is removed/cleared, the associated image file on disk is deleted.

### FR-P9b: Custom Mask Overlay Image

- Users can configure a **custom mask overlay image** to be displayed *on top* of below-mask screen mirror cutouts but *below* above-mask cutouts and MacroPad buttons.
- A layout can simultaneously configure both an independent background image (`backgroundImagePath`) AND a mask image (`maskImagePath`).
- The configuration is situated in a dedicated **Mask** section of the layout editor (`EditorSection.MASK`, `LayoutMaskSubPageContent`).
- **Options & Controls:**
  - **Browse Local Images:** Opens the system document picker (`image/*`). (SteamGridDB scraping is omitted for masks).
  - **Scaling Mode:** Configurable as `Fill`, `Fit`, or `Stretch`.
  - **In-Place Crop Toggle & Bottom-Screen Gestures:** Tapping "Crop" activates cropping mode (`MacroPadState.isCroppingMask = true`), enabling interactive pinch-to-zoom (100%–500%) and drag-to-pan gestures on the bottom display (`PadCanvas`).
  - **Dimming Slider:** Independent dimming control (`0%` to `90%`).
  - **Remove Mask Image:** Two-step inline confirmation card to remove the mask.
  - **Save & Exit Confirmation:** Supports live draft preview and back-intercepted Save & Exit / Discard & Exit confirmation.
- The chosen mask is saved to the internal files directory as `masks/mask_<layoutId>`.
- Legacy layout migration: on load or import, layouts with `useBackgroundImageAsMask = true` are automatically migrated to `maskImagePath = "masks/mask_<layoutId>"` and `maskImage*` properties, clearing `backgroundImagePath` and setting `useBackgroundImageAsMask = false`.
- When a layout is deleted or its mask image is removed, the associated mask file on disk is deleted.

### FR-P9c: Per-Layout Background Touchpad

- Users can configure the open canvas space of any layout to act as a **relative mouse touchpad**, behaving similarly to the Fullscreen Virtual Touchpad.
- **Decoupled Configuration:** Background touchpad settings are stored **per layout** in `PadLayout.backgroundTouchpad` (`BackgroundTouchpadConfig`) and are independent of global touchpad settings.
- **Configurable Options:**
  - **Master Enable Switch:** Toggle background touchpad functionality for open canvas space.
  - **Pointer Sensitivity:** Pointer speed multiplier (0.1x to 3.0x).
  - **Gestures:** Toggles for Tap-to-Click, 2-Finger Tap (Right Click), 3-Finger Tap (Middle Click), and Tap-and-Drag.
  - **Scrolling:** Toggles for Two-Finger Scroll, Natural Scroll Direction, and Scroll Speed multiplier (0.5x to 3.0x).
  - **Haptics:** Toggle for haptic vibration on taps and gestures.
- **Dedicated In-Deck Sub-Page:** Accessible from the **Background Touchpad** action card in the **Layouts Deck** (`MacroPadSubPage.LayoutTouchpad` / `LayoutTouchpadSubPageContent` in `BackgroundTouchpadSettingsEditor.kt`).
- **Mutual Exclusion with Touch Projection:**
  - Background Touchpad and Touch Projection (screen cutout touch injection) are mutually exclusive.
  - Enabling Touch Projection on a layout displays a warning and confirmation dialog offering to turn OFF the Background Touchpad.
  - Enabling Background Touchpad when Touch Projection is active on any cutout displays a warning and confirmation dialog offering to turn OFF Touch Projection for all cutouts of that layout.

- **Two-Step In-Place Confirmation**: Deleting or removing profiles, layouts, pad buttons, macros, background artwork, and app associations uses an inline two-step confirmation card (`GamepadTwoStepConfirmCard`). In the Background settings editor, the "Remove Background Image" card remains visible in the deck even when no background image is currently set, rendering in a disabled state to prevent focus loss when an image is deleted. Activating an active delete action changes the card headline to a confirmation prompt (e.g. *"Really delete 'Profile'?"*, *"Really delete this button?"*, *"Really delete 'Macro'?"*) with a *"Confirm"* badge. Activating again executes the action, shifts focus appropriately, and displays a toast confirmation. Pressing Button B or navigating away cancels the prompt.
- When a profile is deleted, all background image files for its layouts are deleted.
- Creating a layout from a template layout (deep copy) automatically duplicates the background image file under the new layout's ID, ensuring that layouts do not share dependencies on the same file.

### FR-P9d: SteamGridDB Image Scraper

- Users can scrape background images directly from [SteamGridDB](https://www.steamgriddb.com/) using their API v2.
- A **"Scrape from SteamGridDB"** button is provided next to the **"Browse local images"** button in the Background Settings Editor.
- When clicked, Megingiard checks if a SteamGridDB API key is configured.
  - If **no key is configured**, a warning dialog appears directing the user to create a token on SteamGridDB and providing a button that opens the **Global Settings Screen** directly.
  - If a key is configured, the **SteamGridDB Scraper** subpage opens in the editor deck.
- The scraper subpage automatically performs an autocomplete search for games matching the profile's name as the initial query.
- The user can modify the search query, search for games manually, select a matching game, and switch between four asset types: **Grid**, **Hero**, **Logo**, and **Icon**.
- Tapping or selecting a preview thumbnail enables the **"Apply Artwork"** action row (`GamepadSaveExitActionRow`) at position 4. Confirming downloads the chosen image asynchronously, copies it to the internal files folder, and sets it as the layout's background image.
- **In-Flight Exit Confirmation**: When an artwork thumbnail is selected, pressing Button B, Escape, or system Back intercepts navigation via `rememberSaveExitPromptState`. The action row splits into side-by-side **Save & Exit** and **Discard & Exit** options, preventing accidental exit without deciding whether to keep or discard the chosen artwork.
- **Scraper Error Handling**: If any network request to SteamGridDB fails while the scraping dialog is open (searching, loading images, or downloading a selected image), a dismissible `AlertDialog` is displayed to the user explaining the specific error:
  - **Being offline**: Informs the user they are offline and should check their connection.
  - **Being rate limited**: Informs the user that the rate limit was exceeded.
  - **SteamGridDB unreachable**: Informs the user that the service is down or unreachable.
  - **Other errors**: Handled with a generic error message.
- A **SteamGridDB API-Token** field is provided in the **Global Settings Screen** under the Scraping section to allow users to input and persist their API key via DataStore.


### FR-P9e: Background & Mask Image Dimming

- Users can dim the background image or mask overlay image using a `GamepadSliderCard` in their respective layout settings editors (`LayoutBackgroundSubPageContent`, `LayoutMaskSubPageContent`).
- Dimming ranges from **0%** (no dimming) up to **95%** (maximum dimming) in **5%** steps to prevent complete obscurity.
- Transparent and semi-transparent PNG images are fully supported: dimming is applied using a `SrcAtop` blending tint color filter. This ensures that only the non-transparent/colored pixels of the image are dimmed, and the transparent background/cutout regions remain completely unaffected.
- Real-time dimming is visible within the **Layout Settings background preview thumbnail**, the **Layout Editor Canvas** (`PadCanvas`), and the active **MacroPad Screen** (`MacroPadScreen`).

### FR-P9f: Background & Mask Image Scaling Modes

- Users can select how the background image and mask overlay image scale to fit the secondary display's aspect ratio via a `GamepadChoiceCard` in the respective layout settings editors (`LayoutBackgroundSubPageContent`, `LayoutMaskSubPageContent`).
- Three scaling modes are supported:
  - **Fill** (`BackgroundScaleMode.FILL`): Scales proportionally to cover the entire viewport without letterboxing/pillarboxing, cropping excess edges (default behavior, fully backward compatible with older layout configurations).
  - **Fit** (`BackgroundScaleMode.FIT`): Scales proportionally so the full image is visible, leaving black letterbox bars (top/bottom) or pillarbox bars (left/right) if aspect ratios differ.
  - **Stretch** (`BackgroundScaleMode.STRETCH`): Stretches the image non-proportionally to exactly fill screen width and height.
- **Cropping & Positioning Interaction**:
  - When **Stretch** scaling is selected, the **Crop** toggle card is disabled (`enabled = false`), its description states that cropping and positioning are disabled when stretch scaling is active, and any active crop mode is automatically deactivated.
  - While **Fill** or **Fit** is selected, user-adjusted pinch-to-zoom and pan gestures function relative to the mode's base proportional scale.
- Persisted in `PadLayout.bgScaleMode` and `PadLayout.maskScaleMode` (`BackgroundScaleMode`), defaulting to `FILL` for seamless backward compatibility.
