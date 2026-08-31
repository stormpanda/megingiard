package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog

private const val TAG = "LetterNavigationHelper"

object LetterNavigationHelper {
    fun getStartingLetter(label: String): Char {
        val trimmed = label.trim()
        val firstChar =
            trimmed.firstOrNull { it.isLetterOrDigit() }
                ?: trimmed.firstOrNull()
                ?: '#'
        return firstChar.uppercaseChar()
    }

    fun getUniqueStartingLetters(apps: List<InstalledAppInfo>): List<Char> = apps.map { getStartingLetter(it.label) }.distinct()

    fun findFirstIndexOfLetter(
        apps: List<InstalledAppInfo>,
        targetLetter: Char,
    ): Int {
        val upperTarget = targetLetter.uppercaseChar()
        return apps.indexOfFirst { getStartingLetter(it.label) == upperTarget }.coerceAtLeast(0)
    }

    fun findNextLetterAppIndex(
        apps: List<InstalledAppInfo>,
        currentIndex: Int,
    ): Int {
        if (apps.size <= 1) return 0
        val safeIndex = currentIndex.coerceIn(0, apps.size - 1)
        val currentLetter = getStartingLetter(apps[safeIndex].label)
        val targetIndex =
            ((safeIndex + 1 until apps.size) + (0 until safeIndex))
                .firstOrNull { getStartingLetter(apps[it].label) != currentLetter }
                ?: safeIndex
        AppLog.d(TAG, "findNextLetterAppIndex: current=$safeIndex ('$currentLetter') -> target=$targetIndex")
        return targetIndex
    }

    fun findPreviousLetterAppIndex(
        apps: List<InstalledAppInfo>,
        currentIndex: Int,
    ): Int {
        if (apps.size <= 1) return 0
        val safeIndex = currentIndex.coerceIn(0, apps.size - 1)
        val currentLetter = getStartingLetter(apps[safeIndex].label)
        val firstIndexOfCurrentGroup = apps.indexOfFirst { getStartingLetter(it.label) == currentLetter }.coerceAtLeast(0)
        if (safeIndex > firstIndexOfCurrentGroup) {
            AppLog.d(
                TAG,
                "findPreviousLetterAppIndex: inside '$currentLetter' group ($safeIndex > $firstIndexOfCurrentGroup) -> target=$firstIndexOfCurrentGroup",
            )
            return firstIndexOfCurrentGroup
        }
        val prevLetter =
            ((safeIndex - 1 downTo 0) + (apps.indices.reversed()))
                .map { getStartingLetter(apps[it].label) }
                .firstOrNull { it != currentLetter }
                ?: currentLetter
        val targetIndex = findFirstIndexOfLetter(apps, prevLetter)
        AppLog.d(TAG, "findPreviousLetterAppIndex: current=$safeIndex ('$currentLetter') -> target=$targetIndex ('$prevLetter')")
        return targetIndex
    }
}
