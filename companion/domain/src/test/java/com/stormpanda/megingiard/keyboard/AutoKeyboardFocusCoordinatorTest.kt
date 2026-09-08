package com.stormpanda.megingiard.keyboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.KeyboardSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoKeyboardFocusCoordinatorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val dummyDataStore =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = emptyFlow()

                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = emptyPreferences()
            }
        KeyboardSettings.init(dummyDataStore, CoroutineScope(testDispatcher))
        AutoKeyboardFocusCoordinator.reset()
        AppStateManager.setFullscreenKeyboardActive(false)
    }

    @After
    fun tearDown() {
        AutoKeyboardFocusCoordinator.reset()
        AppStateManager.setFullscreenKeyboardActive(false)
        Dispatchers.resetMain()
    }

    @Test
    fun testFocusIgnoredWhenDisabled() =
        runTest(testDispatcher) {
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = false,
                autoOpenEnabled = false,
            )

            assertFalse(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertFalse(AppStateManager.isFullscreenKeyboardActive.value)
        }

    @Test
    fun testFocusOpensKeyboard() =
        runTest(testDispatcher) {
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = false,
                autoOpenEnabled = true,
            )

            assertTrue(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertTrue(AppStateManager.isFullscreenKeyboardActive.value)

            // Shift focus to a non-editable element
            AutoKeyboardFocusCoordinator.onNonEditableFocused()

            assertFalse(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertFalse(AppStateManager.isFullscreenKeyboardActive.value)
        }

    @Test
    fun testWindowStateChangeClosesKeyboard() =
        runTest(testDispatcher) {
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = true,
                autoOpenEnabled = true,
            )

            assertTrue(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertTrue(AppStateManager.isFullscreenKeyboardActive.value)

            // Change window
            AutoKeyboardFocusCoordinator.onWindowStateChanged()

            assertFalse(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertFalse(AppStateManager.isFullscreenKeyboardActive.value)
        }

    @Test
    fun testManualDismissalHysteresis() =
        runTest(testDispatcher) {
            // 1. Auto-open
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = false,
                autoOpenEnabled = true,
            )
            assertTrue(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)

            // 2. User manually dismisses keyboard
            AppStateManager.setFullscreenKeyboardActive(false)
            AutoKeyboardFocusCoordinator.onKeyboardVisibilityChanged(false)
            assertFalse(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)

            // 3. Passive focus event for same field does not re-open
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = false,
                autoOpenEnabled = true,
            )
            assertFalse(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertFalse(AppStateManager.isFullscreenKeyboardActive.value)

            // 4. Explicit click re-opens
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = true,
                autoOpenEnabled = true,
            )
            assertTrue(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertTrue(AppStateManager.isFullscreenKeyboardActive.value)
        }

    @Test
    fun testFocusNewFieldClearsPreviousDismissal() =
        runTest(testDispatcher) {
            // 1. Auto-open on field1
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field1",
                isClicked = false,
                autoOpenEnabled = true,
            )
            // 2. Dismiss field1
            AppStateManager.setFullscreenKeyboardActive(false)
            AutoKeyboardFocusCoordinator.onKeyboardVisibilityChanged(false)

            // 3. Focus field2 opens keyboard
            AutoKeyboardFocusCoordinator.onTextFieldFocused(
                fieldId = "field2",
                isClicked = false,
                autoOpenEnabled = true,
            )
            assertTrue(AutoKeyboardFocusCoordinator.isKeyboardAutoOpened.value)
            assertTrue(AppStateManager.isFullscreenKeyboardActive.value)
        }
}
