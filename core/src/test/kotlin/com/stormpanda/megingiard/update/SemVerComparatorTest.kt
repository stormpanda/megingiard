package com.stormpanda.megingiard.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerComparatorTest {
    @Test
    fun `snapshot current version vs release tag returns true`() {
        assertTrue(SemVerComparator.isUpdateAvailable("0.8.0-SNAPSHOT", "v0.8.0"))
        assertTrue(SemVerComparator.isUpdateAvailable("0.8.0-SNAPSHOT", "0.8.0"))
    }

    @Test
    fun `older version vs newer release tag returns true`() {
        assertTrue(SemVerComparator.isUpdateAvailable("0.8.0-SNAPSHOT", "v0.8.1"))
        assertTrue(SemVerComparator.isUpdateAvailable("0.8.0", "v0.8.1"))
        assertTrue(SemVerComparator.isUpdateAvailable("0.8.0", "v1.0.0"))
        assertTrue(SemVerComparator.isUpdateAvailable("1.2.3", "v1.3.0"))
    }

    @Test
    fun `same version returns false`() {
        assertFalse(SemVerComparator.isUpdateAvailable("0.8.0", "v0.8.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("v0.8.0", "v0.8.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0", "1.0.0"))
    }

    @Test
    fun `newer current version returns false`() {
        assertFalse(SemVerComparator.isUpdateAvailable("0.8.1", "v0.8.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0", "v0.9.9"))
    }

    @Test
    fun `blank or empty inputs return false`() {
        assertFalse(SemVerComparator.isUpdateAvailable("", "v0.8.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("0.8.0", ""))
        assertFalse(SemVerComparator.isUpdateAvailable("  ", "  "))
    }
}
