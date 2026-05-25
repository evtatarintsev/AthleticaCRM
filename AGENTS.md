# Правила оформления кода

## Общие принципы
1. **Иммутабельность** — предпочитать `val` вместо `var` и неизменяемые структуры.
2. **Явные зависимости** — передавать через конструктор, не использовать глобальное состояние
3. **Тестируемость** — код должен легко тестироваться без моков и глобальных объектов
4. **Функциональный стиль** — предпочитать чистые функции, избегать side effect
5. **Проверка компиляции** — ВСЕГДА запускать компиляцию (`./gradlew build` или `./gradlew compileKotlin`) перед тем как сообщать о завершении задачи. Код должен компилироваться без ошибок.

## Правила внесения изменений в проект
Перед тем как предложить изменения в коде, необходимо убедиться, что они соответствуют стандартам качества проекта.

### Обязательные проверки

1. `./gradlew build`
2. `./gradlew ktlintFormat`

Никогда не предлагайте изменения, которые нарушают линтер или не собираются

### Переменные окружения

При добавлении или изменении переменных окружения в `application.conf` необходимо:
1. Добавить переменную в `docker-compose.prod.yaml` (в блок `environment:` сервиса `server`), чтобы она пробрасывалась с хоста в контейнер.
2. Добавить переменную с примером значения в `.env.prod.example` — этот файл документирует все переменные, необходимые для production-деплоя.

Оба файла обновляются в том же коммите, что и изменение `application.conf`.


### 6. Command Query Separation (CQS) в именовании методов
Методы должны либо выполнять действие (команда), либо возвращать данные (запрос), но не оба одновременно.

Query методы (запросы) должны только возвращать значение и не менять состояние (не создавать effects).
Имя Query метода должно быть существительным обозначающим результат вызова,
например, `makeAccessToken` должен называться просто  `accessToken`.

Command методы (команды) должны выполнять действие (созвать effects) и не возвращать значение (Unit).
Имя Command метода должно быть глаголом описывающим действие метода,
например, `sendEmail()` или `user.save()`.

Бывают ситуации когда придерживаться этого правила сложно или накладно для производительности,
тогда возможны исключения, но в общем случае придерживаемся его.


## Именование запросов и ответов

Не использовать суффикс `DTO` для запросов и ответов: обозначать как `request`/`response` или `schema`.

**Неправильно:** `CreateUserDto`  
**Правильно:** `CreateUSerRequest`

**Неправильно:** `UserDetailDto`  
**Правильно:** `USerDetailResponse`

**Неправильно:** `PostListItemDto`  
**Правильно:** `PostListItemSchema`


## Проектирование API для фронтенда

В проекте используется следующий подход к проектированию API: API должен быть удобен для использования на фронтенде.

Нужно стараться предоставлять фронту готовые данные, собранные из доменных сущностей в обработчиках запросов.
При этом логика отображения не должна просачиваться в доменные модели — они остаются независимыми от фронтенда.

Логика сборки ответов для фронта должна находиться в обработчике запроса.


## Независимость доменных моделей от схем запросов и ответов

Доменные модели (интерфейсы репозиториев, сервисов, агрегаты) **не должны** импортировать или использовать классы из пакетов `api.schemas`, `request`, `response` и т.п.

**Плохо** — доменный интерфейс зависит от API-схемы:
```kotlin
import org.athletica.crm.api.schemas.settings.DisplaySettings

interface UserDisplaySettings {
    context(ctx: RequestContext, tr: Transaction)
    suspend fun get(): DisplaySettings

    context(ctx: RequestContext, tr: Transaction)
    suspend fun save(settings: DisplaySettings)
}
```

**Хорошо** — доменный интерфейс оперирует собственными типами; маппинг происходит в обработчике запроса:
```kotlin
interface UserDisplaySettings {
    context(ctx: RequestContext, tr: Transaction)
    suspend fun get(): UserSettings

    context(ctx: RequestContext, tr: Transaction)
    suspend fun save(settings: UserSettings)
}

// В роуте/хэндлере:
val settings = userDisplaySettings.get()
call.respond(settings.toDisplaySettings())
```


## Path-переменные в REST-роутах

Не использовать path-переменные в REST-роутах.

**Неправильно:** `POST /users/<id>/update`  
**Правильно:** передавать идентификатор в `body`.

Для `GET`-запросов передавать параметры в `query`.


## Обработка ошибок

Логические ошибки и ожидаемые сбои возвращаются через `Either<Error, Value>` из библиотеки Arrow.

- **Логические ошибки** (неверные данные, бизнес-ограничения, недоступность ресурса) — `Either.Left`
- **Успешный результат** — `Either.Right`
- **Обработка** — функциональный стиль: `fold`, `map`, `mapLeft`, `flatMap`; `when` по типу — только если нужна разная логика для разных подтипов
- **`fold`** используется для терминальной обработки обоих случаев (например, в роутах)
- **`map`/`mapLeft`** — для трансформации значений в пайплайне, не для side effects

Исключения (`throw`/`try-catch`) допустимы только:
- Для **настоящих** исключительных ситуаций (баги, нехватка памяти, сбои JVM) — не обрабатываются явно
- Для **интеграции с внешними библиотеками**, которые сами бросают исключения — оборачиваются на границе в `Either` и дальше не пробрасываются

Явный `catch` внутри бизнес-логики — признак того, что ошибка должна быть смоделирована через `Either`.


## Невозможность создания объектов в невалидном состоянии

Если у типа есть инвариант (формат строки, диапазон значения, набор допустимых символов),
он должен проверяться **в момент создания**, а не в каждом callsite.
Это паттерн **Smart Constructor** (FP-сообщество), также известный как
**Parse, Don't Validate** (Alexis King) и **Make Illegal States Unrepresentable** (Yaron Minsky).

Реализация в Kotlin: `value class` с приватным основным конструктором и фабричным методом
в `companion object`, возвращающим `Either<DomainError, T>`. Дополнительно — `String.toX()`-расширение
для удобства и кастомный `KSerializer`, чтобы инвариант проверялся уже на десериализации.

**Пример:** [CustomFieldKey](shared/src/commonMain/kotlin/org/athletica/crm/core/customfields/CustomFieldKey.kt) —
машинный ключ кастомного поля, разрешён только набор `[a-z_]+`.
Конструктор приватный, единственная точка создания — `CustomFieldKey.from(...)` или `String.toFieldKey()`.
На JSON-границе инвариант поддерживается `CustomFieldKey.Serializer`,
так что невалидное значение не может попасть ни через конструктор, ни через десериализацию.

Когда применять: любые «string-обёртки» с непустым множеством запрещённых значений
(machine keys, slugs, формат идентификатора, ограниченные диапазоны).
Для простых тегов-обёрток без инвариантов (Entity ID на UUID v7) приватный конструктор
не нужен — любой `Uuid` валиден.


## Деньги — только через `Money`

Денежные значения (балансы, суммы операций, цены, корректировки) в коде
представляются исключительно типом `org.athletica.crm.core.money.Money`.
Использование `Double`, `Float`, `BigDecimal` или «голых» `Long`/`Int`
для денежных полей запрещено: `Double`/`Float` теряют точность на сложении,
а «голый» `Long`/`BigDecimal` теряет валюту.

[Money](shared/src/commonMain/kotlin/org/athletica/crm/core/money/Money.kt)
хранит сумму в минорных единицах (`Long`, копейки/центы) и валюту
([Currency](shared/src/commonMain/kotlin/org/athletica/crm/core/money/Currency.kt)).
Арифметика между разными валютами — программная ошибка
(`IllegalArgumentException`); конвертации валют в продукте нет.
Валюта организации задаётся на регистрации и в дальнейшем readonly —
смена потребовала бы конвертации по курсу, что вне рамок продукта.

На границе с БД использовать `Row.asMoney(column, currency)` и
`Money.toDbDecimal()` (см. `server/src/main/kotlin/org/athletica/crm/storage/Database.kt`);
форматирование для UI — через `Money.formatted`. Валюта операции совпадает
с валютой организации (`RequestContext.currency`).

```kotlin
val amount = Money(120_050, Currency.RUB)  // 1200,50 ₽
amount.formatted                            // "1 200,50 ₽"
amount + Money(5_000, Currency.RUB)         // ок
amount + Money(100, Currency.USD)           // IllegalArgumentException
```


## Ссылки между агрегатами — только по идентификатору

Доменная сущность одного агрегата не должна содержать **объект** другого агрегата —
только его идентификатор (Evans, *DDD*; Vernon, *Implementing DDD*).
Например, в `ClientBalanceEntry` и `OrgBalanceEntry` поле `performedBy` имеет тип
`EmployeeId?`, а не `PerformedBy(id, name)`.

Сборка человекочитаемого представления (имя сотрудника, аватар и т.п.) — задача
**слоя routes / projection**: загрузить нужные агрегаты по id и собрать DTO.
Domain ничего не знает про DTO, сериализацию и UI-нужды.

Признаки нарушения: импорт из `org.athletica.crm.api.schemas.*` внутри `domain/*`,
`@Serializable` на доменной сущности, поля-объекты соседних агрегатов в data class-ах.


## Внутренняя итерация (Internal Iteration) для side-эффектов над коллекцией

Для выполнения действий над каждым элементом коллекции используй `forEach` вместо `for`.
`for` — это внешняя итерация (external iteration): вызывающий код управляет обходом.
`forEach` — внутренняя итерация (internal iteration): обход делегируется коллекции, код выражает **что** делать с элементом, а не **как** его перебирать.
Термины введены в контексте паттерна Iterator (GoF) и popularized в функциональных языках; в JVM-экосистеме закреплены в Effective Java (Bloch) и Kotlin Coding Conventions.

**Плохо** — внешняя итерация:
```kotlin
for (clientId in request.clientIds) {
    audit.logUpdate("client", clientId, auditData)
}
```

**Хорошо** — внутренняя итерация:
```kotlin
request.clientIds.forEach {
    audit.logUpdate("client", it, auditData)
}
```

Исключение: `for` допустим, если внутри тела нужен `break`, `continue` или `return` из внешней функции — `forEach` их не поддерживает.


## Минимальная область видимости переменных (Minimize Variable Span)

Объявляй переменную как можно ближе к месту её первого использования («declare variables close to their first use», Code Complete — Steve McConnell, Chapter 10).
Чем меньше расстояние между объявлением и использованием, тем проще читать код: не нужно держать переменную в голове и возвращаться к её объявлению.

**Плохо** — `requestJson` объявлена задолго до использования, между объявлением и использованием есть несвязанный блок `try/catch`:
```kotlin
val requestJson = Json.encodeToString(request)

try {
    db.transaction {
        for (clientId in request.clientIds) {
            sql("INSERT INTO client_groups ...")
                .bind("clientId", clientId)
                .bind("groupId", request.groupId)
                .execute()
        }
    }
} catch (e: R2dbcDataIntegrityViolationException) {
    raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
}

for (clientId in request.clientIds) {
    audit.logUpdate("client", clientId, requestJson)
}
```

**Хорошо** — переменная объявлена непосредственно перед использованием:
```kotlin
try {
    db.transaction { ... }
} catch (e: R2dbcDataIntegrityViolationException) {
    raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
}

val auditData = Json.encodeToString(request)
for (clientId in request.clientIds) {
    audit.logUpdate("client", clientId, auditData)
}
```


## Фигурные скобки в управляющих конструкциях

`if`, `for`, `while` всегда используются с фигурными скобками, даже если тело состоит из одного выражения.

**Плохо:**
```kotlin
if (updatedRows == 0L) raise(UserNotFound("Employee not found for user='${ctx.userId}'"))
```

**Хорошо:**
```kotlin
if (updatedRows == 0L) {
    raise(UserNotFound("Employee not found for user='${ctx.userId}'"))
}
```


## Работа с комментариями
Комментариев в коде быть не должно, кроме документации.

Допускается использование комментариев для:
- **TODO** — пометки о необходимых доработках
- **FIXME** — пометки о проблемах, требующих исправления


## Документирование кода
1. Документация и комментарии должны быть на русском языке
2. Каждый класс должен иметь документацию, объясняющую его назначение.
3. Документация к полям указывается перед каждым полем, а не в общей документации к классу.
4. Каждая функция/метод должны иметь документацию, объясняющую назначение.
5. Запрещается оставлять закоментированный или неиспользуемый код.
6. Для kotlin кода обязательно соблюдение https://kotlinlang.org/docs/coding-conventions.html#documentation-comments
   - Избегать тегов `@param` и `@return` — вместо них описание параметров и возвращаемого значения
     вписывается прямо в текст документации со ссылками `[paramName]` на параметры.
   - Теги `@param` / `@return` допустимы только если описание слишком длинное и не вписывается в основной текст.


## Локализация строк интерфейса

Все строки пользовательского интерфейса обязаны использовать механизм локализации Compose Multiplatform Resources (`stringResource(Res.string.key)`).

Хардкодить строки в UI-коде запрещено. Нужная строка должна быть добавлена в `composeApp/src/commonMain/composeResources/values/strings.xml` (и соответствующие `values-<lang>/strings.xml` для каждого поддерживаемого языка).

**Плохо:**
```kotlin
Text("Добавить группу")
```

**Хорошо:**
```kotlin
Text(stringResource(Res.string.action_add_client_group))
```


## ViewModel: одна `var`-ячейка с иммутабельным снимком состояния

ViewModel держит **ровно одну** изменяемую ячейку — `var state: XState by mutableStateOf(...)` (или `StateFlow<XState>`), где `XState` — `data class` с `val`-полями. Все переходы — чистые методы на `XState`, возвращающие новую копию через `copy(...)`; методы VM только присваивают `state = state.with…(...)`.

Несколько `var` с `mutableStateOf` в одном классе — запах: связанные инварианты невозможно гарантировать (один переход забывает сбросить соседнее поле), каждая мутация — самостоятельный эффект. Сводим в одну data class — заодно тесты ассертят `state`, а не пять полей.

**Плохо** — пять отдельных `var`, инварианты держатся «на честном слове»:
```kotlin
class XViewModel {
    var data by mutableStateOf<ListData<T>>(ListData.Loading); private set
    var filter by mutableStateOf(F()); private set
    var sort by mutableStateOf<SortState?>(null); private set
    var searchQuery by mutableStateOf(""); private set
    var activeSavedViewId by mutableStateOf<SavedViewId?>(null); private set

    fun setFilter(f: F) { filter = f; activeSavedViewId = null } // легко забыть сбросить id
}
```

**Хорошо** — одна ячейка, чистые переходы на data class:
```kotlin
data class XState<T, F>(
    val data: ListData<T>,
    val filter: F,
    val sort: SortState?,
    val searchQuery: String,
    val activeSavedViewId: SavedViewId?,
) {
    fun withFilter(f: F) = copy(filter = f, activeSavedViewId = null)
}

class XViewModel {
    var state: XState<T, F> by mutableStateOf(initial); private set
    fun setFilter(f: F) { state = state.withFilter(f) }
}
```


## Направление композиции: общий ⊃ специфичный

Переиспользуемый координатор (`ListPageViewModel`, `FormViewModel`, любой generic-«движок») принимает специфичный делегат **через конструктор**. **Не наоборот**: специфичная VM не должна хранить координатор как поле и передавать в него `this`. Признак инверсии — экран обращается к `viewModel.subVm.X` почти везде, и только пара мест — к корневой VM. Если ловите себя на `vm.x.y.z` в большинстве callsite — инкапсуляция сломана: либо поднять поле в координатор, либо передавать делегата напрямую туда, где он нужен.

**Плохо** — specific хранит generic, экран читает `viewModel.list.X` почти везде:
```kotlin
class TasksViewModel(...) : ListPageDelegate<...> {
    val list = ListPageViewModel(this)  // ← инверсия
}
```

**Хорошо** — generic принимает specific:
```kotlin
class TasksPageDelegate(...) : ListPageDelegate<...>
val viewModel = ListPageViewModel(TasksPageDelegate(...), scope)
```


## Метаданные ответа — внутри `Loaded`, а не отдельным `var`

Если сервер вместе со списком возвращает `total` (или любую другую метаданность), это должно быть полем `Loaded(items, total)`, а **не** отдельным `var total: Int` на VM, выставляемым побочным эффектом внутри `.map { ... }`. Пайплайн fetch → state остаётся чистым, без скрытых присваиваний.

**Плохо** — побочка внутри `.map`:
```kotlin
var total: Int by mutableStateOf(0); private set
suspend fun fetch(...) = api.list(...).map { total = it.total; it.items }
```

**Хорошо** — `total` часть данных:
```kotlin
data class Loaded<T>(val items: List<T>, val total: Int = items.size)
suspend fun fetch(...) = api.list(...).map { FetchResult(it.items, it.total) }
```


## Инжектируй зависимости через конструктор, а не поля класса
1. Инжектировать зависимости в поля класса (через `@Value` или `@Autowired`) запрещено.
2. Используй конструктор класса для внедрения зависимостей.
3. Использование `lateinit var` является плохим стилем. Допустимо только в исключительных случаях, когда другой способ инициализации невозможен.

   **Плохо** — `lateinit var` с `@BeforeEach`:
   ```kotlin
   class UserServiceTest {
       private lateinit var hasher: PasswordHasher
       private lateinit var userService: UserService

       @BeforeEach
       fun setup() {
           hasher = PasswordHasher()
           userService = UserService(db = ..., passwordHasher = hasher)
       }
   }
   ```

   **Хорошо** — `val` для простых зависимостей, `by lazy` когда нужна отложенная инициализация (например, зависимость от ресурса, стартующего в `@BeforeClass`):
   ```kotlin
   class UserServiceTest {
       private val hasher = PasswordHasher()

       val userService by lazy {
           UserService(
               db = createDatabase(postgres.jdbcUrl, postgres.username, postgres.password),
               passwordHasher = hasher,
           )
       }

       companion object {
           private val postgres = PostgreSQLContainer("postgres:18")

           @BeforeClass @JvmStatic
           fun setup() { postgres.start() }
       }
   }
   ```
