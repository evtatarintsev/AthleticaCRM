package org.athletica.crm.security

import kotlinx.coroutines.test.runTest
import org.athletica.crm.createDatabase
import org.athletica.crm.runMigrations
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserServiceTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:18")
        private lateinit var userService: UserService
        private val hasher = PasswordHasher()

        @BeforeClass
        @JvmStatic
        fun setup() {
            postgres.start()
            runMigrations(postgres.jdbcUrl, postgres.username, postgres.password)
            userService = UserService(
                db = createDatabase(postgres.jdbcUrl, postgres.username, postgres.password),
                passwordHasher = hasher,
            )
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            postgres.stop()
        }
    }

    /** Вставляет пользователя напрямую через JDBC, возвращает его UUID. */
    private fun insertUser(login: String, password: String): UUID {
        val hash = hasher.hash(password).value
        val sql = "INSERT INTO users (login, name, password_hash) VALUES (?, ?, ?) RETURNING id"
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, login)
                stmt.setString(2, login)
                stmt.setString(3, hash)
                val rs = stmt.executeQuery()
                rs.next()
                return rs.getObject("id", UUID::class.java)
            }
        }
    }

    @Test
    fun `findById returns user when exists`() = runTest {
        val id = insertUser("find_by_id_user", "pass")
        val user = userService.findById(id.toKotlinUuid())
        assertNotNull(user)
        assertEquals("find_by_id_user", user.username)
    }

    @Test
    fun `findById returns null when user does not exist`() = runTest {
        val user = userService.findById(UUID.randomUUID().toKotlinUuid())
        assertNull(user)
    }

    @Test
    fun `findByCredentials returns user for correct credentials`() = runTest {
        insertUser("credentials_user", "secret123")
        val user = userService.findByCredentials("credentials_user", "secret123")
        assertNotNull(user)
        assertEquals("credentials_user", user.username)
    }

    @Test
    fun `findByCredentials returns null for wrong password`() = runTest {
        insertUser("wrong_pass_user", "correct_password")
        val user = userService.findByCredentials("wrong_pass_user", "wrong_password")
        assertNull(user)
    }

    @Test
    fun `findByCredentials returns null for non-existent user`() = runTest {
        val user = userService.findByCredentials("ghost_user", "any_password")
        assertNull(user)
    }
}

private fun UUID.toKotlinUuid() = kotlin.uuid.Uuid.parse(this.toString())
