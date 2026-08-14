package com.stormpanda.megingiard.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PpssppLogcatParserTest {
    @Test
    fun parseLatestBootedSession_emptyLogcat_returnsNull() {
        assertNull(PpssppLogcatParser.parseLatestBootedSession("org.ppsspp.ppsspp", ""))
        assertNull(PpssppLogcatParser.parseLatestBootedSession("org.ppsspp.ppsspp", "08-14 14:00:00.000 I/PPSSPP: [BOOT] PPSSPP v1.20"))
    }

    @Test
    fun parseLatestBootedSession_validBootLogLine_extractsActiveSession() {
        val logcat =
            """
            08-14 14:28:14.604 I/PPSSPP  (14877): [BOOT] PPSSPP v1.20.4
            08-14 14:28:15.050 I/PPSSPP  (14877): [G3D] ShaderCache: Loaded 2 vertex
            08-14 14:28:15.153 I/PPSSPP  (14877): [BOOT] Booted content://com.android.externalstorage.documents/tree/6914%2D318F%3AROMs%2Fpsp/document/6914%2D318F%3AROMs%2Fpsp%2FGod%20of%20War%20%2D%20Chains%20of%20Olympus%20%28Europe%2C%20Australia%29%20%28En%2CFr%2CDe%2CEs%2CIt%29%2Eiso...
            08-14 14:28:15.382 E/PPSSPP  (14877): [FILESYS] Can't open file
            """.trimIndent()

        val session = PpssppLogcatParser.parseLatestBootedSession("org.ppsspp.ppsspp", logcat)

        assertNotNull(session)
        assertEquals("org.ppsspp.ppsspp", session?.packageName)
        assertEquals("God of War - Chains of Olympus (Europe, Australia) (En,Fr,De,Es,It)", session?.gameTitle)
        assertEquals("psp", session?.systemId)
        assertEquals("PPSSPP", session?.coreOrBackend)
        assertEquals(
            "/storage/6914-318F/ROMs/psp/God of War - Chains of Olympus (Europe, Australia) (En,Fr,De,Es,It).iso",
            session?.romPath,
        )
    }

    @Test
    fun parseLatestBootedSession_multipleBoots_returnsMostRecentBootedGame() {
        val logcat =
            """
            08-14 14:00:00.000 I/PPSSPP (1000): [BOOT] Booted /storage/emulated/0/ROMs/psp/Tekken 6.cso...
            08-14 14:10:00.000 I/PPSSPP (1000): [BOOT] Booted /storage/emulated/0/ROMs/psp/Crisis Core.iso...
            """.trimIndent()

        val session = PpssppLogcatParser.parseLatestBootedSession("org.ppsspp.ppssppgold", logcat)

        assertNotNull(session)
        assertEquals("org.ppsspp.ppssppgold", session?.packageName)
        assertEquals("Crisis Core", session?.gameTitle)
        assertEquals("/storage/emulated/0/ROMs/psp/Crisis Core.iso", session?.romPath)
    }
}
