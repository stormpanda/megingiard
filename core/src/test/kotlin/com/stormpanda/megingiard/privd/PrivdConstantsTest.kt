package com.stormpanda.megingiard.privd

import org.junit.Assert.assertTrue
import org.junit.Test

class PrivdConstantsTest {
    @Test
    fun `daemon version is a positive integer`() {
        assertTrue(PrivdConstants.PRIVD_VERSION > 0)
    }
}
