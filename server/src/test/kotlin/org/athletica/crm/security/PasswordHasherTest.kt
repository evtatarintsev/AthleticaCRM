package org.athletica.crm.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    private val hasher = PasswordHasher()

    @Test
    fun `hash returns PHC string format`() {
        val hash = hasher.hash("password123")
        assertTrue(hash.value.startsWith("\$argon2id\$v=19\$"))
    }

    @Test
    fun `hash produces unique hashes for same password`() {
        val hash1 = hasher.hash("password123")
        val hash2 = hasher.hash("password123")
        // разные соли → разные хеши
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verify returns true for correct password`() {
        val hash = hasher.hash("correct_password")
        assertTrue(hasher.verify("correct_password", hash))
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = hasher.hash("correct_password")
        assertFalse(hasher.verify("wrong_password", hash))
    }

    @Test
    fun `verify returns false for empty password against non-empty hash`() {
        val hash = hasher.hash("some_password")
        assertFalse(hasher.verify("", hash))
    }

    @Test
    fun `verify returns false for tampered hash`() {
        val hash = hasher.hash("password123")
        val tampered = org.athletica.crm.core.PasswordHash(hash.value.dropLast(5) + "AAAAA")
        assertFalse(hasher.verify("password123", tampered))
    }

    @Test
    fun `verify returns false for malformed hash`() {
        val malformed = org.athletica.crm.core.PasswordHash("not_a_valid_hash")
        assertFalse(hasher.verify("password", malformed))
    }

    @Test
    fun `custom config is applied to hash`() {
        val customHasher = PasswordHasher(PasswordConfig(memory = 8192, iterations = 1))
        val hash = customHasher.hash("password123")
        assertTrue(hash.value.contains("m=8192,t=1"))
    }

    @Test
    fun `hash from custom config can be verified`() {
        val customHasher = PasswordHasher(PasswordConfig(memory = 8192, iterations = 1))
        val hash = customHasher.hash("password123")
        // verify работает с любым набором параметров — читает их из PHC строки
        assertTrue(customHasher.verify("password123", hash))
    }

    @Test
    fun `hash contains all expected PHC segments`() {
        val hash = hasher.hash("password")
        // $argon2id $ v=19 $ m=...,t=...,p=... $ salt $ hash
        val parts = hash.value.split("$").filter { it.isNotEmpty() }
        assertEquals(5, parts.size)
        assertEquals("argon2id", parts[0])
        assertEquals("v=19", parts[1])
    }
}
