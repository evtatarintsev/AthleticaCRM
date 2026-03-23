package org.athletica.crm.db

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle

/**
 * Обёртка над R2DBC [ConnectionPool] с fluent DSL.
 *
 * Использование:
 * ```
 * db.sql("SELECT * FROM users WHERE id = :id")
 *     .bind("id", uuid)
 *     .firstOrNull { row, _ -> row.toUser() }
 * ```
 *
 * @param pool пул соединений с базой данных
 */
class Database(private val pool: ConnectionPool) {
    /** Начинает построение запроса с заданным SQL. */
    fun sql(sql: String): QueryBuilder = QueryBuilder(pool, sql)
}

/**
 * Построитель SQL-запроса с поддержкой именованных параметров (`:name`).
 * При выполнении `:name` заменяются на `$1`, `$2`, ... в порядке появления в SQL.
 */
class QueryBuilder(
    private val pool: ConnectionPool,
    private val sql: String,
) {
    private val bindings = mutableListOf<Pair<String, Any?>>()

    /** Привязывает именованный параметр к значению. */
    fun bind(
        name: String,
        value: Any?,
    ): QueryBuilder {
        bindings.add(name to value)
        return this
    }

    /**
     * Выполняет запрос и возвращает первый результат или `null`.
     *
     * @param mapper функция преобразования строки результата в доменный объект
     */
    suspend fun <T : Any> firstOrNull(mapper: (Row, RowMetadata) -> T): T? = execute(mapper).firstOrNull()

    /**
     * Выполняет запрос и возвращает список результатов.
     *
     * @param mapper функция преобразования строки результата в доменный объект
     */
    suspend fun <T : Any> list(mapper: (Row, RowMetadata) -> T): List<T> = execute(mapper)

    private suspend fun <T : Any> execute(mapper: (Row, RowMetadata) -> T): List<T> {
        var paramIndex = 1
        val paramOrder = mutableListOf<String>()
        val processedSql =
            sql.replace(Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")) { match ->
                paramOrder.add(match.groupValues[1])
                "\$${paramIndex++}"
            }

        val connection = pool.create().awaitSingle()
        return try {
            val statement = connection.createStatement(processedSql)
            paramOrder.forEachIndexed { i, name ->
                val value = bindings.find { it.first == name }?.second
                if (value == null) {
                    statement.bindNull(i, Any::class.java)
                } else {
                    statement.bind(i, value)
                }
            }
            statement
                .execute()
                .awaitSingle()
                .map(mapper)
                .asFlow()
                .toList()
        } finally {
            connection.close().awaitFirstOrNull()
        }
    }
}
