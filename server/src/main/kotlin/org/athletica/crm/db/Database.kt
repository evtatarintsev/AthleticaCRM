package org.athletica.crm.db

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
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

    /**
     * Выполняет [block] в рамках одной транзакции.
     * При любом исключении транзакция откатывается, исключение пробрасывается выше.
     *
     * @param block блок с запросами, выполняемыми в единой транзакции
     * @return результат [block]
     */
    suspend fun <T> transaction(block: suspend TransactionScope.() -> T): T {
        val connection = pool.create().awaitSingle()
        return try {
            connection.beginTransaction().awaitFirstOrNull()
            val result = TransactionScope(connection).block()
            connection.commitTransaction().awaitFirstOrNull()
            result
        } catch (e: Exception) {
            connection.rollbackTransaction().awaitFirstOrNull()
            throw e
        } finally {
            connection.close().awaitFirstOrNull()
        }
    }
}

/**
 * Контекст транзакции: предоставляет DSL для выполнения запросов
 * в рамках одного соединения с активной транзакцией.
 *
 * @param connection соединение с активной транзакцией
 */
class TransactionScope(private val connection: Connection) {
    /** Начинает построение запроса с заданным SQL. */
    fun sql(sql: String): ConnectionQueryBuilder = ConnectionQueryBuilder(connection, sql)
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
        val connection = pool.create().awaitSingle()
        return try {
            connection.executeStatement(sql, bindings, mapper)
        } finally {
            connection.close().awaitFirstOrNull()
        }
    }
}

/**
 * Построитель SQL-запроса, выполняющий запросы в рамках существующего соединения.
 * Используется внутри [TransactionScope].
 * Поддерживает именованные параметры (`:name`).
 */
class ConnectionQueryBuilder(
    private val connection: Connection,
    private val sql: String,
) {
    private val bindings = mutableListOf<Pair<String, Any?>>()

    /** Привязывает именованный параметр к значению. */
    fun bind(
        name: String,
        value: Any?,
    ): ConnectionQueryBuilder {
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

    private suspend fun <T : Any> execute(mapper: (Row, RowMetadata) -> T): List<T> =
        connection.executeStatement(sql, bindings, mapper)
}

private suspend fun <T : Any> Connection.executeStatement(
    sql: String,
    bindings: List<Pair<String, Any?>>,
    mapper: (Row, RowMetadata) -> T,
): List<T> {
    var paramIndex = 1
    val paramOrder = mutableListOf<String>()
    val processedSql =
        sql.replace(Regex(":([a-zA-Z_][a-zA-Z0-9_]*)")) { match ->
            paramOrder.add(match.groupValues[1])
            "\$${paramIndex++}"
        }
    val statement = createStatement(processedSql)
    paramOrder.forEachIndexed { i, name ->
        val value = bindings.find { it.first == name }?.second
        if (value == null) {
            statement.bindNull(i, Any::class.java)
        } else {
            statement.bind(i, value)
        }
    }
    return statement
        .execute()
        .awaitSingle()
        .map(mapper)
        .asFlow()
        .toList()
}
