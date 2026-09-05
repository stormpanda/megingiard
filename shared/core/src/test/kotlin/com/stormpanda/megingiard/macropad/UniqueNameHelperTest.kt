package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueNameHelperTest {
    @Test
    fun `nextUniqueName returns original name when no collision`() {
        val existing = listOf("Profile A", "Profile B")
        assertEquals("Profile C", existing.nextUniqueName("Profile C"))
    }

    @Test
    fun `nextUniqueName trims whitespace`() {
        val existing = listOf("Profile A")
        assertEquals("Profile B", existing.nextUniqueName("  Profile B  "))
    }

    @Test
    fun `nextUniqueName appends 2 on first collision`() {
        val existing = listOf("Profile A", "Profile B")
        assertEquals("Profile A (2)", existing.nextUniqueName("Profile A"))
    }

    @Test
    fun `nextUniqueName increments index on consecutive collisions`() {
        val existing = listOf("Profile A", "Profile A (2)", "Profile A (3)")
        assertEquals("Profile A (4)", existing.nextUniqueName("Profile A"))
    }

    @Test
    fun `nextUniqueName handles non-consecutive collisions correctly`() {
        val existing = listOf("Profile A", "Profile A (3)")
        assertEquals("Profile A (2)", existing.nextUniqueName("Profile A"))
    }

    @Test
    fun `nextUniqueName matches case-insensitively`() {
        val existing = listOf("profile a")
        assertEquals("Profile A (2)", existing.nextUniqueName("Profile A"))
    }

    @Test
    fun `nextUniqueName uses fallback when baseName is blank`() {
        val existing = listOf("Default")
        assertEquals("Default (2)", existing.nextUniqueName("   ", fallback = "Default"))
    }
}
