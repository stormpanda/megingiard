package com.stormpanda.megingiard.onboarding

import android.content.Context
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.core.onboarding.OnboardingStepId
import com.stormpanda.megingiard.core.onboarding.OnboardingStepState
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "OnboardingWizardManager"

/**
 * Singleton state holder managing the multi-step onboarding welcome tour.
 * Evaluates tour versioning, step fulfillment, handles automatic step skipping, and coordinates tour navigation.
 */
object OnboardingWizardManager {
    private val _isWizardActive = MutableStateFlow(false)
    val isWizardActive: StateFlow<Boolean> = _isWizardActive.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _steps = MutableStateFlow<List<OnboardingStepState>>(emptyList())
    val steps: StateFlow<List<OnboardingStepState>> = _steps.asStateFlow()

    private val completedStepIds = mutableSetOf<OnboardingStepId>()

    val orderedStepIds =
        listOf(
            OnboardingStepId.WELCOME,
            OnboardingStepId.QUICK_MENU,
            OnboardingStepId.ACCESSIBILITY,
            OnboardingStepId.PRIVILEGED_MODE,
        )

    fun shouldAutoStartWizard(): Boolean = SettingsManager.welcomeTourCompletedVersion.value < SettingsManager.CURRENT_WELCOME_TOUR_VERSION

    fun isStepFulfilled(id: OnboardingStepId): Boolean =
        when (id) {
            OnboardingStepId.WELCOME -> false
            OnboardingStepId.QUICK_MENU -> false
            OnboardingStepId.ACCESSIBILITY -> false
            OnboardingStepId.PRIVILEGED_MODE -> false
        }

    fun startWizard(
        context: Context,
        force: Boolean = false,
    ) {
        if (!force && !shouldAutoStartWizard()) {
            AppLog.d(
                TAG,
                "Welcome tour already completed for version ${SettingsManager.welcomeTourCompletedVersion.value}. Skipping auto-start.",
            )
            _isWizardActive.value = false
            return
        }

        if (force) {
            completedStepIds.clear()
        }

        AppLog.d(TAG, "Initializing onboarding wizard (force=$force)")
        reevaluateSteps(context)

        val firstPendingIndex = _steps.value.indexOfFirst { !it.isCompleted }
        if (firstPendingIndex == -1) {
            AppLog.d(TAG, "All onboarding steps are already fulfilled/completed. Skipping wizard.")
            _isWizardActive.value = false
        } else {
            AppLog.d(TAG, "Starting wizard at step index: $firstPendingIndex (${orderedStepIds[firstPendingIndex]})")
            _activeStepIndex.value = firstPendingIndex
            _isWizardActive.value = true
            updateCurrentFlags(firstPendingIndex)
        }
    }

    fun nextStep(context: Context) {
        val currentIndex = _activeStepIndex.value
        if (currentIndex < 0 || currentIndex >= orderedStepIds.size) return

        val currentId = orderedStepIds[currentIndex]
        completedStepIds.add(currentId)

        when (currentId) {
            OnboardingStepId.WELCOME -> {
                SettingsManager.setShowWelcomeTutorial(false)
            }

            OnboardingStepId.QUICK_MENU -> {
                SettingsManager.setShowQuickMenuTutorial(false)
            }

            else -> {}
        }

        reevaluateSteps(context)

        val nextPendingIndex = _steps.value.indices.firstOrNull { i -> i > currentIndex && !_steps.value[i].isCompleted }

        if (nextPendingIndex != null) {
            AppLog.d(TAG, "Advancing wizard to step index: $nextPendingIndex (${orderedStepIds[nextPendingIndex]})")
            _activeStepIndex.value = nextPendingIndex
            updateCurrentFlags(nextPendingIndex)
        } else {
            AppLog.d(TAG, "Reached end of onboarding tour. Finishing wizard.")
            finishWizard()
        }
    }

    fun prevStep(context: Context) {
        val currentIndex = _activeStepIndex.value
        if (currentIndex <= 0) return

        val prevIndex = currentIndex - 1
        AppLog.d(TAG, "Navigating back in wizard to step index: $prevIndex (${orderedStepIds[prevIndex]})")
        _activeStepIndex.value = prevIndex
        reevaluateSteps(context)
        updateCurrentFlags(prevIndex)
    }

    fun skipWizard() {
        AppLog.d(TAG, "Skipping onboarding wizard")
        completedStepIds.addAll(orderedStepIds)
        SettingsManager.setWelcomeTourCompletedVersion(SettingsManager.CURRENT_WELCOME_TOUR_VERSION)
        SettingsManager.setShowWelcomeTutorial(false)
        SettingsManager.setShowQuickMenuTutorial(false)
        _isWizardActive.value = false
    }

    fun finishWizard() {
        AppLog.d(TAG, "Finishing onboarding wizard")
        completedStepIds.addAll(orderedStepIds)
        SettingsManager.setWelcomeTourCompletedVersion(SettingsManager.CURRENT_WELCOME_TOUR_VERSION)
        SettingsManager.setShowWelcomeTutorial(false)
        SettingsManager.setShowQuickMenuTutorial(false)
        _isWizardActive.value = false
    }

    fun resetWizardForTest() {
        completedStepIds.clear()
        _activeStepIndex.value = 0
        _isWizardActive.value = false
    }

    private fun reevaluateSteps(context: Context) {
        val updatedList =
            orderedStepIds.mapIndexed { index, id ->
                val fulfilled = isStepFulfilled(id)
                val completed = fulfilled || completedStepIds.contains(id)
                OnboardingStepState(
                    id = id,
                    isFulfilled = fulfilled,
                    isCompleted = completed,
                    isCurrent = index == _activeStepIndex.value,
                )
            }
        _steps.value = updatedList
    }

    private fun updateCurrentFlags(currentIndex: Int) {
        _steps.value =
            _steps.value.mapIndexed { i, state ->
                state.copy(isCurrent = i == currentIndex)
            }
    }
}
