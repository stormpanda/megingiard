package com.stormpanda.megingiard.onboarding

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "OnboardingWizardManager"

/**
 * Singleton state holder managing the multi-step onboarding welcome tour.
 * Evaluates tour versioning, manages sequential step navigation, and handles wizard completion.
 */
object OnboardingWizardManager {
    private val _isWizardActive = MutableStateFlow(false)
    val isWizardActive: StateFlow<Boolean> = _isWizardActive.asStateFlow()

    val orderedStepIds =
        listOf(
            OnboardingStepId.WELCOME,
            OnboardingStepId.QUICK_MENU,
            OnboardingStepId.THEME,
            OnboardingStepId.ACCESSIBILITY,
            OnboardingStepId.PRIVILEGED,
            OnboardingStepId.FINISHED,
        )

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _steps = MutableStateFlow(buildSteps(0))
    val steps: StateFlow<List<OnboardingStepState>> = _steps.asStateFlow()

    fun shouldAutoStartWizard(): Boolean = SettingsManager.welcomeTourCompletedVersion.value < SettingsManager.CURRENT_WELCOME_TOUR_VERSION

    fun startWizard(force: Boolean = false) {
        if (_isWizardActive.value && !force) {
            AppLog.d(TAG, "Wizard is already active. Ignoring non-forced start call.")
            return
        }

        if (force) {
            SettingsManager.setWelcomeTourCompletedVersion(0)
        }

        if (!force && !shouldAutoStartWizard()) {
            AppLog.d(
                TAG,
                "Welcome tour already completed for version ${SettingsManager.welcomeTourCompletedVersion.value}. Skipping auto-start.",
            )
            return
        }

        AppStateManager.closeQuickMenu()
        AppStateManager.setGlobalSettingsOpen(false)

        AppLog.d(TAG, "Initializing onboarding wizard at step 0 (force=$force)")
        setActiveStep(0)
        _isWizardActive.value = true
    }

    fun nextStep() {
        val currentIndex = _activeStepIndex.value
        if (currentIndex < 0 || currentIndex >= orderedStepIds.size - 1) return

        val nextIndex = currentIndex + 1
        AppLog.d(TAG, "Advancing wizard to step index: $nextIndex (${orderedStepIds[nextIndex]})")
        setActiveStep(nextIndex)
    }

    fun prevStep() {
        val currentIndex = _activeStepIndex.value
        if (currentIndex <= 0) return

        val prevIndex = currentIndex - 1
        AppLog.d(TAG, "Navigating back in wizard to step index: $prevIndex (${orderedStepIds[prevIndex]})")
        setActiveStep(prevIndex)
    }

    fun finishWizard() {
        AppLog.d(TAG, "Finishing onboarding wizard via explicit Finish button")
        SettingsManager.setWelcomeTourCompletedVersion(SettingsManager.CURRENT_WELCOME_TOUR_VERSION)
        _isWizardActive.value = false
    }

    fun resetWizardForTest() {
        _isWizardActive.value = false
        setActiveStep(0)
    }

    fun buildSteps(activeStepIdx: Int): List<OnboardingStepState> =
        orderedStepIds.mapIndexed { index, id ->
            OnboardingStepState(
                id = id,
                isCompleted = index < activeStepIdx,
                isCurrent = index == activeStepIdx,
            )
        }

    private fun setActiveStep(activeStepIdx: Int) {
        _activeStepIndex.value = activeStepIdx
        _steps.value = buildSteps(activeStepIdx)
    }
}
