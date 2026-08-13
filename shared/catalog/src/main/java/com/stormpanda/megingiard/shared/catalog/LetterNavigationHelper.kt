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

    fun getUniqueStartingLetters(apps: List<InstalledAppInfo>): List<Char> {
        if (apps.isEmpty()) return emptyList()
        val set = LinkedHashSet<Char>()
        for (app in apps) {
            set.add(getStartingLetter(app.label))
        }
        return set.toList()
    }

    fun findFirstIndexOfLetter(
        apps: List<InstalledAppInfo>,
        targetLetter: Char,
    ): Int {
        val upperTarget = targetLetter.uppercaseChar()
        for (i in apps.indices) {
            if (getStartingLetter(apps[i].label) == upperTarget) {
                return i
            }
        }
        return 0
    }

    /**
     * Legacy direct-skip helper: finds the index of the first application starting with a different letter
     * after [currentIndex], wrapping around if necessary.
     */
    fun findNextLetterAppIndex(
        apps: List<InstalledAppInfo>,
        currentIndex: Int,
    ): Int {
        if (apps.isEmpty()) return 0
        if (apps.size == 1) return 0

        val safeIndex = currentIndex.coerceIn(0, apps.size - 1)
        val currentLetter = getStartingLetter(apps[safeIndex].label)

        // Search forward from (safeIndex + 1) to end of list
        for (i in (safeIndex + 1) until apps.size) {
            if (getStartingLetter(apps[i].label) != currentLetter) {
                AppLog.d(
                    TAG,
                    "findNextLetterAppIndex: current=$safeIndex ('$currentLetter') -> target=$i ('${getStartingLetter(apps[i].label)}')",
                )
                return i
            }
        }

        // Wrap around: search from start of list up to safeIndex
        for (i in 0 until safeIndex) {
            if (getStartingLetter(apps[i].label) != currentLetter) {
                AppLog.d(
                    TAG,
                    "findNextLetterAppIndex (wrap-around): current=$safeIndex ('$currentLetter') -> target=$i ('${getStartingLetter(
                        apps[i].label,
                    )}')",
                )
                return i
            }
        }

        AppLog.d(TAG, "findNextLetterAppIndex: no different starting letter found, keeping index $safeIndex")
        return safeIndex
    }

    /**
     * Legacy direct-skip helper: finds the index of the first application starting with a different letter
     * preceding [currentIndex] (or the start of current letter group), wrapping around if necessary.
     */
    fun findPreviousLetterAppIndex(
        apps: List<InstalledAppInfo>,
        currentIndex: Int,
    ): Int {
        if (apps.isEmpty()) return 0
        if (apps.size == 1) return 0

        val safeIndex = currentIndex.coerceIn(0, apps.size - 1)
        val currentLetter = getStartingLetter(apps[safeIndex].label)

        // Find the first index of current letter group
        var firstIndexOfCurrentGroup = safeIndex
        for (i in 0 until safeIndex) {
            if (getStartingLetter(apps[i].label) == currentLetter) {
                firstIndexOfCurrentGroup = i
                break
            }
        }

        // If currently inside group but not at the first item, jump to start of current letter group
        if (safeIndex > firstIndexOfCurrentGroup) {
            AppLog.d(
                TAG,
                "findPreviousLetterAppIndex: inside letter '$currentLetter' group ($safeIndex > $firstIndexOfCurrentGroup) -> target=$firstIndexOfCurrentGroup",
            )
            return firstIndexOfCurrentGroup
        }

        // Search backwards from (safeIndex - 1) down to 0 for a different letter
        for (i in (safeIndex - 1) downTo 0) {
            val letter = getStartingLetter(apps[i].label)
            if (letter != currentLetter) {
                val firstIndexOfPrevLetter = findFirstIndexOfLetter(apps, letter)
                AppLog.d(
                    TAG,
                    "findPreviousLetterAppIndex: current=$safeIndex ('$currentLetter') -> target=$firstIndexOfPrevLetter ('$letter')",
                )
                return firstIndexOfPrevLetter
            }
        }

        // Wrap around to start of the last letter group in list
        val lastLetter = getStartingLetter(apps.last().label)
        val firstIndexOfLastGroup = findFirstIndexOfLetter(apps, lastLetter)
        AppLog.d(
            TAG,
            "findPreviousLetterAppIndex (wrap-around): current=$safeIndex ('$currentLetter') -> target=$firstIndexOfLastGroup ('$lastLetter')",
        )
        return firstIndexOfLastGroup
    }
}
