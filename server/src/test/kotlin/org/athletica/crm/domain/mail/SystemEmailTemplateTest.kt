package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.Lang
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class SystemEmailTemplateTest {
    private val address = EmailAddress("owner@example.com")
    private val link = "https://app.example.com/confirm-email#token=abc_xyz-123"

    @Test
    fun `template escapes inserts and omits absent action and greeting`() {
        val email = systemEmail(address, Lang.RU, "Info", "<title>", listOf("<script>\"&'"), "Footer")
        assertContains(email.html.value, "&lt;script&gt;&quot;&amp;&#39;")
        assertFalse(email.html.value.contains("<a "))
        assertFalse(email.html.value.contains("null"))
        assertContains(email.text.value, "<script>\"&'")
        assertFails { systemEmail(address, Lang.RU, "Info\r\nBcc: other@example.com", "Info", emptyList(), "Footer") }
        assertFails { systemEmail(address, Lang.RU, "Info", "Info", emptyList(), "Footer", action = EmailAction("Go", "javascript:alert(1)")) }
        val action = systemEmail(address, Lang.EN, "Info", "Info", emptyList(), "Footer", action = EmailAction("Go", "https://app.example.com/?a=1&b=2"))
        assertContains(action.html.value, "?a=1&amp;b=2")
        assertContains(action.text.value, "?a=1&b=2")
    }

    @Test
    fun `registration produces consistent localized multipart content and previews`() {
        for (lang in Lang.entries) {
            val email = registrationEmail(address, "<Owner>", lang, link)
            assertEquals(listOf(address), email.to)
            assertContains(email.html.value, "&lt;Owner&gt;")
            listOf(email.html.value, email.text.value).forEach {
                assertContains(it, link)
                assertContains(it, "24")
                assertContains(it, if (lang == Lang.EN) "Confirm email" else "Подтвердить email")
                assertFalse(it.contains("password123"))
            }
            assertEquals(if (lang == Lang.EN) "Welcome to AthleticaCRM" else "Добро пожаловать в AthleticaCRM", email.subject.value)
            val dir = Files.createDirectories(Path.of("build/mail-preview"))
            Files.writeString(dir.resolve("registration-${lang.code}.html"), email.html.value)
            Files.writeString(dir.resolve("registration-${lang.code}.txt"), email.text.value)
        }
        assertEquals(Lang.RU, Lang.default())
    }

    @Test
    fun `password reset email is localized and carries the full link and lifetime in both versions`() {
        val resetLink = "https://app.example.com/reset-password#token=abc_xyz-123"
        for (lang in Lang.entries) {
            val email = passwordResetEmail(address, "<Owner>", lang, resetLink)
            assertEquals(listOf(address), email.to)
            assertContains(email.html.value, "&lt;Owner&gt;")
            listOf(email.html.value, email.text.value).forEach {
                assertContains(it, resetLink)
                assertContains(it, if (lang == Lang.EN) "1 hour" else "1 час")
                assertContains(it, if (lang == Lang.EN) "Set a new password" else "Задать новый пароль")
            }
            assertEquals(if (lang == Lang.EN) "AthleticaCRM password reset" else "Сброс пароля AthleticaCRM", email.subject.value)
        }
    }
}
