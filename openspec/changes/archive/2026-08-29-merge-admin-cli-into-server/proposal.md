## Why

Модуль `:admin-cli` — отдельный Gradle-модуль и отдельный артефакт, но самостоятельным приложением он не является: он объявляет `implementation(project(":server"))` и работает с той же БД, что и сервер. При этом он не собирается в CI (`.github/workflows/docker.yml` собирает только `Dockerfile.server` и `Dockerfile.web`), не попадает ни в один Docker-образ и не имеет сетевого доступа к Postgres в проде — сервис `postgres` в `docker-compose.prod.yaml` не публикует порт наружу, а `POSTGRES_URL` указывает на внутреннее имя docker-сети. Итог: инструмент, описанный в `admin-cli/README.md`, сегодня невозможно штатно запустить в продакшене.

Слияние `:admin-cli` в `:server` даёт один артефакт, одну сборку и один образ — и попутно делает админские команды работоспособными в проде, потому что они выполняются внутри контейнера, у которого уже есть и сеть до БД, и все нужные переменные окружения.

## What Changes

- **BREAKING** Модуль `:admin-cli` удаляется из `settings.gradle.kts`; его исходники переезжают в `server/src/main/kotlin/org/athletica/crm/admin/`. Отдельного артефакта `admin-cli-1.0.0-all.jar` больше не существует. Обратная совместимость не требуется — проект не в продакшене.
- **BREAKING** Меняется CLI-поверхность. Вместо плоского набора взаимоисключающих флагов вводится декларативное дерево подкоманд Clikt:

  ```
  athletica serve
  athletica admin org find <query>...
  athletica admin org credit <org> <amount> --description=<text>
  athletica admin org debit  <org> <amount> --description=<text>
  ```

  Уровень `admin` сохраняется как пространство имён под будущие технические команды (например, отдельный запуск миграций вместо запуска при старте).
- Ручной роутинг и ручная валидация в `AdminCli.run()` (`when {}` по наличию опций, проверки «только `--credit` или `--debit`», «`--description` обязателен») заменяются штатными механизмами Clikt: `subcommands()`, `required()`, обязательные аргументы. Библиотека не меняется — Clikt 5.0.3 уже содержит всё необходимое.
- Точка входа сервера `fun main(args)` перестаёт быть прямым делегатом `EngineMain.main(args)` и становится корневой Clikt-командой. Запуск веб-сервера переезжает в подкоманду `serve`, которая вызывает `EngineMain.main(emptyArray())`; источником конфигурации остаётся `application.conf` и переменные окружения.
- Команды становятся suspend-нативными (`SuspendingCliktCommand`), `runBlocking` остаётся только в `fun main`.
- Зависимости админских команд (`AdminDi`) создаются лениво через `Context.obj`, чтобы `serve` не требовал их, а админские команды не требовали `JWT_SECRET`, `MINIO_*`, `SMTP_*`, `YOOKASSA_*`.
- Артефакт переименовывается в `athletica.jar` (сейчас `server-all.jar`, копируемый в образ как `app.jar`). Имя артефакта совпадает с именем корневой команды, которое `AdminCli` уже объявляет.
- `Dockerfile.server` переходит на связку `ENTRYPOINT ["java","-jar","athletica.jar"]` + `CMD ["serve"]`, что сохраняет текущее поведение `docker-compose.prod.yaml` без правок и делает возможным `docker compose exec server java -jar athletica.jar admin org find <query>`.
- `admin-cli/README.md` переносится в документацию сервера с актуальными командами.

## Capabilities

### New Capabilities

- `admin-cli`: интерфейс командной строки приложения — режимы запуска (`serve`) и административные операции над данными (`admin org find|credit|debit`), включая разрешение организации по UUID или названию, обязательность описания операции, формат вывода и коды возврата.

Административный интерфейс — часть контракта продукта, а не деталь реализации: им пользуется живой администратор, и его поверхность (набор команд, что происходит при неоднозначном названии организации, что выводится при пустом результате) должна быть зафиксирована независимо от того, какой библиотекой разобраны аргументы. Ранее эта поверхность существовала только в `admin-cli/README.md`, который уходит вместе с модулем.

### Modified Capabilities

Нет. В `openspec/specs/` спецификаций пока нет; существующих требований изменение не затрагивает.

Правила учёта и семантика самих операций (как считается корректировка баланса организации) остаются прежними — спецификация фиксирует поверхность вызова, а не пересматривает доменные правила.

## Impact

**Сборка и модули**
- `settings.gradle.kts` — удаление `include(":admin-cli")`
- `admin-cli/` — модуль удаляется целиком (5 файлов исходников + `build.gradle.kts` + `README.md`)
- `server/build.gradle.kts` — добавляется `libs.clikt`, задаётся `archiveFileName` = `athletica.jar`

**Код**
- `server/src/main/kotlin/org/athletica/crm/Application.kt` — `fun main` становится корневой Clikt-командой
- `server/src/main/kotlin/org/athletica/crm/admin/` — новый пакет: `AdminCli.kt`, `AdminContext.kt`, `AdminDi.kt`, `OrgSearch.kt` (`Main.kt` исчезает — вторая точка входа больше не нужна)

**Инфраструктура**
- `Dockerfile.server` — имя jar, `ENTRYPOINT`/`CMD`
- `docker-compose.prod.yaml` — правок не требует
- `.github/workflows/docker.yml` — правок не требует; `admin-cli` и так в нём отсутствовал

**Зависимости**
- Плагин `shadow` в `admin-cli/build.gradle.kts` уходит вместе с модулем; fat-jar сервера собирается плагином Ktor, как и сейчас
- Clikt, ранее зависимость только `:admin-cli`, становится зависимостью `:server`

**Не входит в объём**
- Конвертация суммы через `Double` в `AdminCli.runBalance` (`10.0.pow(fractionDigits)` + `roundToLong()`) нарушает правило «деньги только через `Money`» из `CLAUDE.md`. Фиксируется в `design.md` как известный долг, исправляется отдельно.
- Расхождение между базовым образом `eclipse-temurin:17` в `Dockerfile.server` и требованием «Java 21+» в `admin-cli/README.md`.
