package com.stormpanda.megingiard.viewmodel

import app.cash.turbine.test
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.macropad.HapticStrength
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TAG = "MacroPadViewModelTest"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MacroPadViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up MacroPadViewModelTest environment")
        Dispatchers.setMain(testDispatcher)
        AppStateManager.closeQuickMenu()
    }

    @After
    fun tearDown() {
        AppStateManager.closeQuickMenu()
        Dispatchers.resetMain()
    }

    @Test
    fun testQuickMenuStateFlowWithTurbine() =
        runTest {
            val app = RuntimeEnvironment.getApplication()
            val vm = MacroPadViewModel(app)

            vm.isQuickMenuOpen.test {
                assertFalse(awaitItem())

                AppStateManager.openQuickMenu()
                assertTrue(awaitItem())

                AppStateManager.closeQuickMenu()
                assertFalse(awaitItem())
            }
        }

    @Test
    fun testActiveProfileAndLayoutObservation() =
        runTest {
            val app = RuntimeEnvironment.getApplication()
            val vm = MacroPadViewModel(app)

            val testLayout = PadLayout(id = "test_layout", name = "Test Layout")
            val testProfile =
                PadProfile(
                    id = "test_profile",
                    name = "Test Profile",
                    layouts = listOf(testLayout),
                    activeLayoutId = "test_layout",
                )

            vm.activeProfile.test {
                val initial = awaitItem()
                MacroPadState.loadFrom(listOf(testProfile), testProfile.id)
                val updated = awaitItem()
                assertEquals("test_profile", updated?.id)
            }
        }

    @Test
    fun testCreateHitTestEngine() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MacroPadViewModel(app)

        var hapticTriggered = false
        val engine =
            vm.createHitTestEngine(
                buttonUnitDpToPx = { dp -> dp * 2f },
                onHapticFeedback = { _, _, _, _, _ ->
                    hapticTriggered = true
                },
            )
        assertNotNull(engine)
    }

    @Test
    fun testStopInjectorsAndLifecycleWatch() {
        val app = RuntimeEnvironment.getApplication()
        val vm = MacroPadViewModel(app)

        vm.watchInjectorLifecycle(app)
        vm.stopInjectors()
    }
}
