package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivdConstantsTest {
    @Test
    fun `daemon version is a positive integer`() {
        assertTrue(PrivdConstants.PRIVD_VERSION > 0)
    }

    @Test
    fun `daemon version matches C source PRIVD_VERSION macro`() {
        val candidates =
            listOf(
                File("../../companion/ui/src/main/cpp/megingiard_privd.c"),
                File("companion/ui/src/main/cpp/megingiard_privd.c"),
            )
        val cSource = candidates.firstOrNull { it.isFile }
        assertNotNull("Expected megingiard_privd.c to be readable from unit tests", cSource)
        val match =
            Regex("""(?m)^#define\s+PRIVD_VERSION\s+(\d+)\b""")
                .find(cSource!!.readText())
        assertNotNull("Expected #define PRIVD_VERSION <N> in megingiard_privd.c", match)
        assertEquals(match!!.groupValues[1].toInt(), PrivdConstants.PRIVD_VERSION)
    }
}
