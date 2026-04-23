# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Start Commands

### Build and Test
```bash
# Build the entire project (server + shared modules)
./gradlew build

# Run all tests
./gradlew test

# Run a single test file
./gradlew test --tests "org.athletica.crm.usecases.ClientListTest"

# Run a single test method
./gradlew test --tests "org.athletica.crm.usecases.ClientListTest.clientList*"

# Compile Kotlin only (fast check)
./gradlew compileKotlin compileTestKotlin

# Clean build (full rebuild)
./gradlew clean build

# Run tests in parallel (faster)
./gradlew test --parallel --max-workers=4
```

### Database and Migrations
- Tests use PostgreSQL TestContainers (`TestPostgres`)
- Database migrations via Liquibase in `server/src/main/resources/db/changelog/`
- No manual migration needed for tests; TestContainers manage DB lifecycle

### Running the Server
```bash
# Development mode (with -Ddevelopment=true)
./gradlew server:run

# Production mode
./gradlew server:run -x -Ddevelopment=false
```

## Project Structure

### Modules
- **server**: Ktor backend (REST API, database access, business logic)
- **shared**: Kotlin Multiplatform code (shared DTOs, schemas, IDs)
- **composeApp**: Compose Multiplatform UI (currently not the focus)

### Key Directories in `server/src/main/kotlin/org/athletica/crm/`

```
├── core/                  # Core types and foundational patterns
│   ├── RequestContext.kt  # HTTP request context (userId, orgId, permissions)
│   ├── Lang.kt           # Language enum
│   ├── entityids/        # Strongly-typed entity IDs (UserId, ClientId, etc.)
│   ├── permissions/      # Actor interface for permission checks
│   └── errors/           # Domain error types
│
├── domain/                # Domain entities and repositories (DDD bounded contexts)
│   ├── clients/          # Client (participant) domain
│   ├── employees/        # Employee domain (includes EmployeePermissions)
│   ├── discipline/       # Sport/activity types
│   ├── groups/           # Class/course groups
│   ├── clientbalance/    # Client account balance tracking
│   ├── orgbalance/       # Organization-level balance
│   ├── audit/            # Audit logging
│   ├── auth/             # Authentication (Users)
│   ├── mail/             # Email services
│   └── org/              # Organization domain
│
├── usecases/              # Business logic orchestration (thin layer)
│   ├── auth/             # Login, signup, password change
│   ├── clients/          # Client operations (list, add to group, etc.)
│   ├── groups/           # Group operations
│   ├── discipline/       # Discipline CRUD
│   └── notifications/    # Notification operations
│
├── routes/                # HTTP route definitions
│   ├── RouteWithContext.kt  # post/get helpers
│   ├── AuthRoutes.kt
│   ├── ClientsRoutes.kt
│   └── [domain]Routes.kt   # One per domain
│
├── security/              # JWT, password hashing
├── storage/               # Database access (R2DBC), MinIO
├── i18n/                  # Internationalization
├── Di.kt                  # Dependency injection setup
└── Application.kt         # Ktor app configuration
```

## Architecture Patterns

### 1. Domain-Driven Design (DDD)
- **Bounded Contexts**: Each domain (clients, employees, groups) is relatively isolated
- **Entity IDs**: Strongly-typed IDs prevent mixing up IDs (UserId ≠ ClientId)
- **Repositories**: `DbClients`, `DbEmployees`, etc. abstract database access
- **Audit**: Business changes logged through `AuditLog` interfaces (decoupled from persistence)

### 2. Arrow Either for Error Handling
```kotlin
// Return type: Either<DomainError, SuccessValue>
context(RequestContext, tr: Transaction)
suspend fun doSomething(): Either<DomainError, String> = either {
    val result = validate().bind()  // Short-circuit on error
    processData(result)
}

// Caller uses assertIs<Either.Right<T>> in tests or .getOrElse { } in production
```

### 3. Kotlin Context Parameters (Receiver Types)
```kotlin
// Function requires RequestContext in scope
context(RequestContext)
suspend fun clientList(request: ClientListRequest): Either<DomainError, List<ClientListItem>> {
    // RequestContext is implicitly available (userId, orgId, permissions)
}

// Caller provides context via context() builder
context(db, requestContext) {
    val result = clientList(request)
}
```

**Why contexts?**
- Eliminates parameter passing through deep call stacks
- Makes permission/audit context explicit at call site
- Type-safe alternative to thread-local storage

### 4. Request Flow
```
HTTP Request
    ↓
RouteWithContext (post/get)
    ↓
contextFromRequest() → RequestContext (JWT claims + permissions)
    ↓
body() executes with RequestContext in scope
    ↓
UseCase function (context(RequestContext) suspend fun ...)
    ↓
Domain repository (context(RequestContext, tr: Transaction) ...)
    ↓
Database query + audit log
    ↓
HTTP Response (ErrorResponse or data)
```

### 5. Permission Checking
- `RequestContext` implements `Actor` (delegates to `EmployeePermission`)
- `EmployeePermission.hasPermission(permission: Permission): Boolean`
- Permissions loaded lazily from database (caching planned)
- Used in domain layer to restrict operations: `require(ctx.hasPermission(CAN_VIEW_CLIENT_BALANCE))`

### 6. Audit Logging
- Changes logged through `AuditLog` interface (decoupled)
- `PostgresAuditLog` persists to database
- Useful for compliance and user action tracking
- Example: `audit.log(AuditEvent(action = "CLIENT_CREATED", clientId = clientId))`

## Testing

### Test Organization
- **Domain tests** (`domain/*Test.kt`): Test repositories in isolation
- **UseCase tests** (`usecases/*Test.kt`): Test business logic orchestration
- **TestPostgres**: Singleton that manages PostgreSQL TestContainer lifecycle
- Tests truncate database between test methods (`@Before fun setUp()`)

### Test Patterns
```kotlin
@Test
fun `my test`() = runTest {
    // Setup: Insert test data directly via SQL
    val clientId = insertClient(orgId, "Test Client")

    // Act: Call function with test context
    val result = TestPostgres.db.transaction {
        context(testContext, this) {
            clientList(ClientListRequest())
        }
    }

    // Assert: Use Arrow assertion
    val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
    assertEquals(1, clients.size)
}

// Test context: empty EmployeePermission() (unprivileged)
private fun ctx(orgId: Uuid) = RequestContext(
    lang = Lang.EN,
    userId = UserId.new(),
    orgId = OrgId(orgId),
    employeeId = EmployeeId.new(),
    username = "user@example.com",
    clientIp = "127.0.0.1",
    permission = EmployeePermission(),  // No special permissions for tests
)
```

### Running Tests
```bash
./gradlew test                                    # Run all
./gradlew test --tests "*ClientListTest"         # By class
./gradlew test --tests "*ClientListTest*возвращает*"  # By name pattern
./gradlew compileTestKotlin -v                   # Verify compilation
```

## Key Technical Decisions

### Database Access: R2DBC
- **Why**: Non-blocking reactive database access (compatible with Kotlin coroutines)
- **Pattern**: `Transaction` receiver type for queries; use `.bind()` for prepared statements
- **Pooling**: `r2dbc-pool` for connection pooling

### Multimodule Gradle
- **shared**: Compiled to both JVM and WASM; contains DTOs and IDs used across platforms
- **server**: JVM-only, depends on shared
- **composeApp**: Multiplatform UI (lower priority)
- Use `projects.shared` in dependencies (type-safe project accessors)

### Error Handling Strategy
- **Domain errors**: Modeled as sealed classes (`DomainError`)
- **Common errors**: `CommonDomainError(code: String, message: String)` for 80% of cases
- **Client errors**: HTTP 400 (validation) → 404 (not found) → 409 (conflict)
- **Server errors**: HTTP 500 (log to audit trail)
- Never expose sensitive details in error messages (e.g., no SQL errors)

### Foreign Key Constraints
- Database enforces FK constraints; R2DBC surfaces FK violations as exceptions
- Tests catch FK exceptions with `runCatching` if needed (see AddClientsToGroupTest)

## Dependency Injection (Manual)

Located in `Di.kt`:
- **Database**: Singleton R2DBC connection pool (lifecycle: startup → shutdown)
- **Services**: Instantiated per module (e.g., `DbClients()`)
- **Config**: JwtConfig, PasswordHasher
- Routes registered in `Application.kt` with DI context passed

No reflection-based DI framework; all wiring is explicit and visible.

## Common Development Tasks

### Adding a New Usecase
1. Create business logic in `usecases/[domain]/` (context-based)
2. Create test in `server/src/test/kotlin/org/athletica/crm/usecases/`
3. Register route in `routes/[Domain]Routes.kt` using `post` or `get`
4. Return `Either<DomainError, SuccessType>`

### Adding a New Domain Aggregate
1. Create entity and repository in `domain/[newdomain]/`
2. Use `DbX` class for database implementation
3. Optional: Create `AuditX` class if changes need auditing
4. Write domain tests in `server/src/test/kotlin/org/athletica/crm/domain/`

### Modifying Database Schema
1. Create new Liquibase changelog in `server/src/main/resources/db/changelog/`
2. SQL changes are auto-applied on startup (Liquibase)
3. Tests restart with fresh schema (TestContainers)

### Adding a Permission Check
1. Add permission to `Permission` enum (core/permissions/Permission.kt)
2. Check in domain layer: `require(ctx.hasPermission(CAN_DO_X))`
3. Return `CommonDomainError("PERMISSION_DENIED")` on check failure

## Documentation References
- **Ubiquitous Language**: See README.md for domain terms (Client, Group, Discipline, etc.)
- **Architecture Deep Dive**: See `docs/ARCHITECTURE_CORE.md`
- **Use Cases**: See `docs/USECASES_CORE.md` for all 28+ business flows
- **Client Balance**: See `docs/client-balance.md` for accounting rules
- **Quick Reference**: See `docs/QUICK_REFERENCE.md` for developer cheat sheet

## Known Constraints & Gotchas

1. **Context Parameters Are Lexically Scoped**: If you `context(ctx)` and then call a function that also expects `context(RequestContext)`, the inner function must be called *within* the outer context block. No implicit shadowing.

2. **TestPostgres.truncate() Is Global**: All tests share the same database connection pool. Use `@Before` to clean up between tests, or tests will pollute each other.

3. **Arrow Either Short-Circuit**: `.bind()` only works inside `either { }` blocks; calling `.bind()` on a raw `Either` type outside throws an exception. Use `.getOrElse { }` or pattern match instead.

4. **FK Constraints on Insert**: If you insert a client with a non-existent orgId, PostgreSQL will reject it. Tests use valid IDs; production must validate.

5. **Audit Logging is Not Transactional**: If a transaction commits but audit fails, the action is recorded without the audit log. Design audit as best-effort.

6. **Permission Checks Are Point-in-Time**: Permissions are loaded once at request start. If permissions change mid-request, the request doesn't see the change. For long-running operations, consider re-checking.

## Future Improvements (Planned)
- Permission caching with TTL (see `EmployeePermissions.kt`)
- GraphQL API (alternative to REST)
- Real-time notifications via WebSockets
- Payment integration
