package org.athletica.crm.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EmailVerificationConfigTest {
    @Test
    fun `links use the configured origin and web base path`() {
        assertEquals("https://app.example.com/web/confirm-email#token=a_b-c", EmailVerificationConfig("https://app.example.com/web/").confirmationLink("a_b-c"))
        assertEquals("http://localhost:5173/confirm-email#token=token", EmailVerificationConfig("http://localhost:5173").confirmationLink("token"))
    }

    @Test
    fun `unsafe URLs and fragments are rejected`() {
        listOf("/web", "https://user:pass@app.example.com", "https://app.example.com?q=1", "https://app.example.com#token", "javascript:alert(1)", "http://app.example.com").forEach {
            assertFails { EmailVerificationConfig(it) }
        }
        assertFails { EmailVerificationConfig("https://app.example.com").confirmationLink("token&redirect=evil") }
    }
}
