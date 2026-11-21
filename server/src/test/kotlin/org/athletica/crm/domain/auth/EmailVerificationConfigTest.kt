package org.athletica.crm.domain.auth

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EmailVerificationConfigTest {
    @Test
    fun `application links default to dev nginx and respect the public URL override`() {
        val defaults = ConfigFactory.parseResources("application.conf")
        val options = ConfigResolveOptions.defaults().setUseSystemEnvironment(false)
        listOf(
            emptyMap<String, String>() to "https://app.athletica.crm",
            mapOf("CRM_PUBLIC_URL" to "https://crm.example.com") to "https://crm.example.com",
        ).forEach { (override, expected) ->
            val url = ConfigFactory.parseMap(override).withFallback(defaults).resolve(options).getString("emailVerification.publicUrl")
            assertEquals("$expected/reset-password#token=token", EmailVerificationConfig(url).passwordResetLink("token"))
        }
    }

    @Test
    fun `links use the configured origin and web base path`() {
        assertEquals("https://app.example.com/web/confirm-email#token=a_b-c", EmailVerificationConfig("https://app.example.com/web/").confirmationLink("a_b-c"))
        assertEquals("http://localhost:5173/confirm-email#token=token", EmailVerificationConfig("http://localhost:5173").confirmationLink("token"))
        assertEquals("https://app.example.com/web/reset-password#token=a_b-c", EmailVerificationConfig("https://app.example.com/web/").passwordResetLink("a_b-c"))
    }

    @Test
    fun `unsafe URLs and fragments are rejected`() {
        listOf("/web", "https://user:pass@app.example.com", "https://app.example.com?q=1", "https://app.example.com#token", "javascript:alert(1)", "http://app.example.com").forEach {
            assertFails { EmailVerificationConfig(it) }
        }
        assertFails { EmailVerificationConfig("https://app.example.com").confirmationLink("token&redirect=evil") }
        assertFails { EmailVerificationConfig("https://app.example.com").passwordResetLink("token&redirect=evil") }
    }
}
