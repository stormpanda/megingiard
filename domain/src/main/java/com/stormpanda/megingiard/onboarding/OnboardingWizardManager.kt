package com.stormpanda.megingiard.onboarding

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.AppStateManager
import com.stormpanda.megingiard.core.onboarding.OnboardingStepId
import com.stormpanda.megingiard.core.onboarding.OnboardingStepState
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

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _steps = MutableStateFlow<List<OnboardingStepState>>(emptyList())
    val steps: StateFlow<List<OnboardingStepState>> = _steps.asStateFlow()

    val orderedStepIds =
        listOf(
            OnboardingStepId.WELCOME,
            OnboardingStepId.QUICK_MENU,
            OnboardingStepId.THEME,
            OnboardingStepId.ACCESSIBILITY,
            OnboardingStepId.PRIVILEGED,
            OnboardingStepId.FINISHED,
        )

    fun shouldAutoStartWizard(): Boolean = SettingsManager.welcomeTourCompletedVersion.value < SettingsManager.CURRENT_WELCOME_TOUR_VERSION

    fun isStepFulfilled(id: OnboardingStepId): Boolean = false

    fun startWizard(
        context: Context,
        force: Boolean = false,
    ) {
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
            _isWizardActive.value = false
            return
        }

        AppStateManager.closeQuickMenu()
        AppStateManager.setGlobalSettingsOpen(false)

        AppLog.d(TAG, "Initializing onboarding wizard at step 0 (force=$force)")
        _activeStepIndex.value = 0
        updateStepStates(0)
        _isWizardActive.value = true
    }

    fun nextStep(context: Context? = null) {
        val currentIndex = _activeStepIndex.value
        if (currentIndex < 0 || currentIndex >= orderedStepIds.size - 1) return

        val nextIndex = currentIndex + 1
        AppLog.d(TAG, "Advancing wizard to step index: $nextIndex (${orderedStepIds[nextIndex]})")
        _activeStepIndex.value = nextIndex
        updateStepStates(nextIndex)
    }

    fun prevStep(context: Context? = null) {
        val currentIndex = _activeStepIndex.value
        if (currentIndex <= 0) return

        val prevIndex = currentIndex - 1
        AppLog.d(TAG, "Navigating back in wizard to step index: $prevIndex (${orderedStepIds[prevIndex]})")
        _activeStepIndex.value = prevIndex
        updateStepStates(prevIndex)
    }

    fun skipWizard() {
        AppLog.d(TAG, "Dismissing onboarding wizard without storing completion")
        _isWizardActive.value = false
    }

    fun finishWizard() {
        AppLog.d(TAG, "Finishing onboarding wizard via explicit Finish button")
        SettingsManager.setWelcomeTourCompletedVersion(SettingsManager.CURRENT_WELCOME_TOUR_VERSION)
        _isWizardActive.value = false
    }

    fun resetWizardForTest() {
        _activeStepIndex.value = 0
        _isWizardActive.value = false
        updateStepStates(0)
    }

    private fun updateStepStates(activeStepIdx: Int) {
        val updatedList =
            orderedStepIds.mapIndexed { index, id ->
                OnboardingStepState(
                    id = id,
                    isFulfilled = false,
                    isCompleted = index < activeStepIdx,
                    isCurrent = index == activeStepIdx,
                )
            }
        _steps.value = updatedList
    }
}
