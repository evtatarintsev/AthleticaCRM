package org.athletica.crm.storage

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.EntityId
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Абстракция над «откуда брать соединение».
 * [Database] реализует её через пул, [TransactionScope] — через уже открытое соединение.
 */
interface ConnectionScope {
    suspend fun <R> use(block: suspend (Connection) -> R): R
}

interface Transaction {
    fun sql(sql: String): QueryBuilder
}

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
 * Принимает [pool] — пул соединений с базой данных.
 */
class Database(private val pool: ConnectionPool) : ConnectionScope {
    /** Начинает построение запроса с заданным SQL. */
    fun sql(sql: String): QueryBuilder = QueryBuilder(sql, this)

    /**
     * Выполняет [block] в рамках одной ленивой транзакции.
     * BEGIN откладывается до первого реального SQL-запроса: если весь [block]
     * отработал только через кэш — ни BEGIN ни COMMIT не отправляются.
     * При любом исключении транзакция откатывается (если была открыта).
     *
     * Возвращает результат [block].
     */
    suspend fun <T> transaction(block: suspend LazyTransactionScope.() -> T): T {
        val connection = pool.create().awaitSingle()
        val scope = LazyTransactionScope(connection)
        return try {
            val result = scope.block()
            if (scope.begun) connection.commitTransaction().awaitFirstOrNull()
            result
        } catch (e: Exception) {
            if (scope.begun) connection.rollbackTransaction().awaitFirstOrNull()
            throw e
        } finally {
            connection.close().awaitFirstOrNull()
        }
    }

    override suspend fun <R> use(block: suspend (Connection) -> R): R {
        val connection = pool.create().awaitSingle()
        return try {
            block(connection)
        } finally {
            connection.close().awaitFirstOrNull()
        }
    }
}

/**
 * Ленивый контекст транзакции: [Connection.beginTransaction] откладывается до первого
 * реального SQL-запроса через [sql].
 *
 * Если весь блок отработал только через кэш и ни одного [sql] не было вызвано —
 * [begun] остаётся `false` и транзакция не открывается вовсе: экономим два round-trip
 * (BEGIN + COMMIT) к БД.
 */
class LazyTransactionScope(private val connection: Connection) : Transaction {
    var begun = false
        private set

    private val lazyScope =
        object : ConnectionScope {
            override suspend fun <R> use(block: suspend (Connection) -> R): R {
                if (!begun) {
                    connection.beginTransaction().awaitFirstOrNull()
                    begun = true
                }
                return block(connection)
            }
        }

    override fun sql(sql: String): QueryBuilder = QueryBuilder(sql, lazyScope)
}

/**
 * Построитель SQL-запроса с поддержкой именованных параметров (`:name`).
 * При выполнении `:name` заменяются на `$1`, `$2`, ... в порядке появления в SQL.
 * Используется как из [Database], так и из [TransactionScope] — источник соединения
 * скрыт за [ConnectionScope].
 */
class QueryBuilder(
    private val sql: String,
    private val scope: ConnectionScope,
) {
    private val bindings = mutableListOf<Pair<String, Any?>>()

    /** Привязывает именованный параметр к значению. */
    fun bind(name: String, value: Any?): QueryBuilder {
        bindings.add(name to value)
        return this
    }

    /** Привязывает именованный [Uuid] параметр, конвертируя в [java.util.UUID] для R2DBC. */
    fun bind(name: String, value: Uuid?) = bind(name, value?.toJavaUuid())

    /** Привязывает именованный [EntityId] параметр (любой подтип: ClientId, EmployeeId, …). */
    fun bind(name: String, value: EntityId?) = bind(name, value?.value)

    /** Привязывает именованный [EmailAddress] параметр как строку. */
    fun bind(name: String, value: EmailAddress?) = bind(name, value?.value)

    /** Привязывает именованный [LocalDate] параметр. */
    fun bind(name: String, value: LocalDate?) = bind(name, value?.toJavaLocalDate())

    /** Привязывает именованный [Instant] параметр, конвертируя в [java.time.OffsetDateTime] для R2DBC. */
    fun bind(name: String, value: Instant?) =
        bind(
            name,
            value?.let {
                java.time.OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()),
                    java.time.ZoneOffset.UTC,
                )
            },
        )

    /** Привязывает список [EntityId] как массив UUID. */
    @JvmName("bindEntityIds")
    fun bind(name: String, value: List<EntityId>) = bind(name, value.map { it.value.toJavaUuid() }.toTypedArray())

    /** Привязывает список [Uuid] как массив UUID. */
    @JvmName("bindUuids")
    fun bind(name: String, value: List<Uuid>) = bind(name, value.map { it.toJavaUuid() }.toTypedArray())

    /**
     * Выполняет запрос и возвращает первый результат или `null`.
     *
     * [mapper] — функция преобразования строки результата в доменный объект.
     */
    suspend fun <T : Any> firstOrNull(mapper: (Row, RowMetadata) -> T): T? = execute(mapper).firstOrNull()

    /**
     * Выполняет запрос и возвращает первый результат или `null`.
     *
     * [mapper] — функция преобразования строки результата в доменный объект.
     */
    suspend fun <T : Any> firstOrNull(mapper: (Row) -> T): T? = execute { row, _ -> mapper(row) }.firstOrNull()

    /**
     * Выполняет запрос и возвращает список результатов.
     *
     * [mapper] — функция преобразования строки результата в доменный объект.
     */
    suspend fun <T : Any> list(mapper: (Row, RowMetadata) -> T): List<T> = execute(mapper)

    /**
     * Выполняет запрос и возвращает список результатов.
     *
     * [mapper] — функция преобразования строки результата в доменный объект.
     */
    suspend fun <T : Any> list(mapper: (Row) -> T): List<T> = execute { row, _ -> mapper(row) }

    /**
     * Выполняет запрос и возвращает количество затронутых строк (INSERT / UPDATE / DELETE).
     */
    suspend fun execute(): Long =
        scope.use { connection ->
            connection.executeStatement(sql, bindings)
        }

    private suspend fun <T : Any> execute(mapper: (Row, RowMetadata) -> T): List<T> =
        scope.use { connection ->
            connection.executeStatement(sql, bindings, mapper)
        }
}

private suspend fun Connection.executeStatement(
    sql: String,
    bindings: List<Pair<String, Any?>>,
): Long {
    val connection = this
    var paramIndex = 1
    val paramOrder = mutableListOf<String>()
    val processedSql =
        sql.replace(Regex("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)")) { match ->
            paramOrder.add(match.groupValues[1])
            "\$${paramIndex++}"
        }
    val statement = connection.createStatement(processedSql)
    paramOrder.forEachIndexed { i, name ->
        val value = bindings.find { it.first == name }?.second
        if (value == null) statement.bindNull(i, Any::class.java) else statement.bind(i, value)
    }
    return statement
        .execute()
        .awaitFirstOrNull()
        ?.rowsUpdated
        ?.awaitFirstOrNull() ?: 0L
}

private suspend fun <T : Any> Connection.executeStatement(
    sql: String,
    bindings: List<Pair<String, Any?>>,
    mapper: (Row, RowMetadata) -> T,
): List<T> {
    var paramIndex = 1
    val paramOrder = mutableListOf<String>()
    val processedSql =
        sql.replace(Regex("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)")) { match ->
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

fun Row.asString(column: String) = asStringOrNull(column)!!

fun Row.asString(pos: Int) = asStringOrNull(pos)!!

fun Row.asStringOrNull(column: String): String? = get(column, String::class.java)

fun Row.asStringOrNull(pos: Int): String? = get(pos, String::class.java)

fun Row.asUuid(column: String) = asUuidOrNull(column)!!

fun Row.asUuid(pos: Int) = asUuidOrNull(pos)!!

fun Row.asUuidOrNull(column: String): Uuid? = get(column, java.util.UUID::class.java)?.toKotlinUuid()

fun Row.asUuidOrNull(pos: Int): Uuid? = get(pos, java.util.UUID::class.java)?.toKotlinUuid()

fun Row.asLong(column: String) = get(column, java.lang.Long::class.java)!!.toLong()

fun Row.asLong(pos: Int) = get(pos, java.lang.Long::class.java)!!.toLong()

fun Row.asInt(column: String) = get(column, java.lang.Integer::class.java)!!.toInt()

fun Row.asInt(pos: Int) = get(pos, java.lang.Integer::class.java)!!.toInt()

fun Row.asInstant(pos: Int) =
    get(pos, java.time.OffsetDateTime::class.java)!!
        .toInstant()
        .let { Instant.fromEpochMilliseconds(it.toEpochMilli()) }

fun Row.asInstant(column: String) =
    get(column, java.time.OffsetDateTime::class.java)!!
        .toInstant()
        .let { Instant.fromEpochMilliseconds(it.toEpochMilli()) }

fun Row.asDouble(pos: Int) = get(pos, java.math.BigDecimal::class.java)!!.toDouble()

fun Row.asDouble(column: String) = get(column, java.math.BigDecimal::class.java)!!.toDouble()

fun Row.asLocalDate(pos: Int) = asLocalDateOrNull(pos)!!

fun Row.asLocalDate(column: String) = asLocalDateOrNull(column)!!

fun Row.asLocalDateOrNull(pos: Int): LocalDate? = get(pos, java.time.LocalDate::class.java)?.toKotlinLocalDate()

fun Row.asLocalDateOrNull(column: String): LocalDate? = get(column, java.time.LocalDate::class.java)?.toKotlinLocalDate()

fun Row.asBooleanOrNull(pos: Int): Boolean? = get(pos, Boolean::class.java)

fun Row.asBooleanOrNull(column: String): Boolean? = get(column, Boolean::class.java)

fun Row.asBoolean(pos: Int) = asBooleanOrNull(pos)!!

fun Row.asBoolean(column: String) = asBooleanOrNull(column)!!
