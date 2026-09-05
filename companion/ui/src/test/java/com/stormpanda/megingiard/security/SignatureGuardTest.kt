package com.stormpanda.megingiard.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignatureGuardTest {
    @Test
    fun testVerify_returnsValidResult() {
        val context = RuntimeEnvironment.getApplication()
        val result = SignatureGuard.verify(context)
        assertTrue(result is SignatureGuard.Result)
    }

    @Test
    fun testResultTypes() {
        val ok = SignatureGuard.Result.Ok
        val skipped = SignatureGuard.Result.Skipped
        val tampered = SignatureGuard.Result.Tampered(expected = "abc", actual = listOf("def"))
        val error = SignatureGuard.Result.Error("some error")

        assertEquals("abc", tampered.expected)
        assertEquals(listOf("def"), tampered.actual)
        assertEquals("some error", error.message)
        assertTrue(ok is SignatureGuard.Result)
        assertTrue(skipped is SignatureGuard.Result)
    }
}
