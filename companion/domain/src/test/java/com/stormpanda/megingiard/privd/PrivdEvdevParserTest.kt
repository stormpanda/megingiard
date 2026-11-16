package com.stormpanda.megingiard.privd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivdEvdevParserTest {
    @Test
    fun `parseEvdevEventLine parses standard gamepad and touch events`() {
        assertEquals(
            EvdevEvent(type = 1, code = 304, value = 1),
            PrivdClient.parseEvdevEventLine("EVT 1 304 1", "EVT "),
        )
        assertEquals(
            EvdevEvent(type = 3, code = 53, value = 1080),
            PrivdClient.parseEvdevEventLine("EVT_TOUCH 3 53 1080", "EVT_TOUCH "),
        )
    }

    @Test
    fun `parseEvdevEventLine handles negative axis deflections and Int bounds`() {
        assertEquals(
            EvdevEvent(type = 3, code = 0, value = -32768),
            PrivdClient.parseEvdevEventLine("EVT 3 0 -32768", "EVT "),
        )
        assertEquals(
            EvdevEvent(type = Int.MIN_VALUE, code = 0, value = Int.MAX_VALUE),
            PrivdClient.parseEvdevEventLine("EVT ${Int.MIN_VALUE} 0 ${Int.MAX_VALUE}", "EVT "),
        )
    }

    @Test
    fun `parseEvdevEventLine rejects malformed tokens, extra spaces, and integer overflow`() {
        assertNull(PrivdClient.parseEvdevEventLine("", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT ", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 304", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 304 1 2", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT  1 304 1", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1  304 1", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 304 1 ", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT_TOUCH 1 304 1", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 abc 1", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 304 -", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 304 2147483648", "EVT "))
        assertNull(PrivdClient.parseEvdevEventLine("EVT 1 304 -2147483649", "EVT "))
    }
}
