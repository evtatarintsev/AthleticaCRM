package org.athletica.crm.admin

import org.athletica.crm.core.money.Currency
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/** Данные об организации для административных операций. */
data class OrgInfo(
    val id: Uuid,
    val name: String,
    val currency: Currency,
    val ownerLogin: String,
)

/**
 * Поиск организаций напрямую в БД без RequestContext.
 * Используется для cross-org запросов в admin-cli.
 */
class OrgSearch(private val database: Database) {
    /** Ищет организации по подстроке в названии или логине владельца. */
    suspend fun find(query: String): List<OrgInfo> =
        database.transaction {
            sql(
                """
                SELECT o.id, o.name, o.currency, u.login AS owner_login
                FROM organizations o
                JOIN employees e ON e.org_id = o.id AND e.is_owner = true AND e.is_active = true
                JOIN users u     ON u.id = e.user_id
                WHERE o.name ILIKE '%' || :query || '%'
                   OR u.login ILIKE '%' || :query || '%'
                ORDER BY o.name
                """.trimIndent(),
            )
                .bind("query", query)
                .list { row ->
                    OrgInfo(
                        id = row.asUuid("id"),
                        name = row.asString("name"),
                        currency = Currency.entries.first { it.code == row.asString("currency").trim().uppercase() },
                        ownerLogin = row.asString("owner_login"),
                    )
                }
        }

    /** Ищет организацию по точному UUID. */
    suspend fun findById(id: Uuid): OrgInfo? =
        database.transaction {
            sql(
                """
                SELECT o.id, o.name, o.currency, u.login AS owner_login
                FROM organizations o
                JOIN employees e ON e.org_id = o.id AND e.is_owner = true AND e.is_active = true
                JOIN users u     ON u.id = e.user_id
                WHERE o.id = :id
                """.trimIndent(),
            )
                .bind("id", id)
                .firstOrNull { row ->
                    OrgInfo(
                        id = row.asUuid("id"),
                        name = row.asString("name"),
                        currency = Currency.entries.first { it.code == row.asString("currency").trim().uppercase() },
                        ownerLogin = row.asString("owner_login"),
                    )
                }
        }

    /**
     * Разрешает строковый аргумент `--org` в [OrgInfo].
     * Если аргумент — корректный UUID, ищет по ID.
     * Иначе ищет по названию/логину. Завершает процесс, если результат неоднозначен.
     */
    suspend fun resolve(orgArg: String): OrgInfo? {
        val uuid = runCatching { Uuid.parse(orgArg) }.getOrNull()
        if (uuid != null) {
            return findById(uuid)
        }
        val results = find(orgArg)
        return when (results.size) {
            0 -> null
            1 -> results.first()
            else -> {
                println("Найдено несколько организаций. Укажите UUID из списка:")
                results.forEach { printOrgInfo(it) }
                null
            }
        }
    }
}

/** Выводит информацию об организации в одну строку. */
fun printOrgInfo(org: OrgInfo) {
    println("  ${org.id}  ${org.name}  (владелец: ${org.ownerLogin}, валюта: ${org.currency})")
}
