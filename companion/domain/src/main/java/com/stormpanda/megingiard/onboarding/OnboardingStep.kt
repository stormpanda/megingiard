package com.stormpanda.megingiard.onboarding

/**
 * Unique identifiers for each step in the onboarding welcome tour.
 */
enum class OnboardingStepId {
    WELCOME,
    QUICK_MENU,
    THEME,
    ACCESSIBILITY,
    PRIVILEGED,
    FINISHED,
}

/**
 * Transient UI state model representing a single step in the onboarding wizard.
 */
data class OnboardingStepState(
    val id: OnboardingStepId,
    val isFulfilled: Boolean,
    val isCompleted: Boolean,
    val isCurrent: Boolean,
)
