package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.catalog.SafPathResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafPathResolverTest {
    @Test
    fun resolveFilePath_handlesNullOrEmpty() {
        assertNull(SafPathResolver.resolveFilePath(null))
        assertNull(SafPathResolver.resolveFilePath(""))
    }

    @Test
    fun resolveFilePath_safContentUri_decodesPath() {
        val rawUri =
            "content://com.android.externalstorage.documents/tree/6914%2D318F%3AROMs%2Fpsp/document/6914%2D318F%3AROMs%2Fpsp%2FVirtua%20Tennis%20%2D%20World%20Tour%20%28Europe%29%20%28En%2CFr%2CDe%2CEs%2CIt%29%2Eiso"
        val resolved = SafPathResolver.resolveFilePath(rawUri)
        assertEquals("/storage/6914-318F/ROMs/psp/Virtua Tennis - World Tour (Europe) (En,Fr,De,Es,It).iso", resolved)
    }

    @Test
    fun deriveGameTitle_handlesVariousExtensionsAndPathSeparators() {
        assertEquals("Tekken 6", SafPathResolver.deriveGameTitle("/storage/sdcard/roms/psp/Tekken 6.cso"))
        assertEquals(
            "Persona 3 Portable",
            SafPathResolver.deriveGameTitle("C:\\Emulators\\PSP\\ROMs\\Persona 3 Portable.iso"),
        )
        assertEquals(
            "Castlevania - Dracula X Chronicles",
            SafPathResolver.deriveGameTitle("/roms/Castlevania - Dracula X Chronicles.pbp"),
        )
        assertEquals("Homebrew", SafPathResolver.deriveGameTitle("/roms/Homebrew.elf"))
    }
}
