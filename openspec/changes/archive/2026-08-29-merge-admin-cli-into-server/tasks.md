## 1. Перенос модуля в `:server`

- [x] 1.1 Добавить `implementation(libs.clikt)` в `server/build.gradle.kts` и убедиться, что `./gradlew :server:compileKotlin` проходит
- [x] 1.2 Перенести `AdminContext.kt`, `AdminDi.kt`, `OrgSearch.kt` из `admin-cli/src/main/kotlin/org/athletica/crm/admin/` в `server/src/main/kotlin/org/athletica/crm/admin/`, сохранив пакет `org.athletica.crm.admin`; проверить `./gradlew :server:compileKotlin`
- [x] 1.3 Удалить `include(":admin-cli")` из `settings.gradle.kts` и каталог `admin-cli/` целиком (включая `build.gradle.kts`, `Main.kt`, `AdminCli.kt`, `README.md`); проверить, что `./gradlew projects` больше не показывает `:admin-cli`

## 2. Дерево команд

- [x] 2.1 Создать `ServeCommand` (`SuspendingCliktCommand`, name = `serve`, без опций), вызывающую `EngineMain.main(emptyArray())`; проверить, что `./gradlew server:run --args="serve"` поднимает сервер и он отвечает на `http://localhost:8080`
- [x] 2.2 Создать `FindCommand` (name = `find`, аргумент `query` с `multiple()`), переносящую логику `AdminCli.runFind` без ручного `when`; проверить `athletica admin org find <query>` на локальной БД
- [x] 2.3 Создать `CreditCommand` и `DebitCommand` (аргументы `org` и `amount`, `--description` через `required()`), заменив три ручные проверки из `AdminCli.runBalance` декларативными; убедиться, что запуск без `--description` завершается ошибкой парсера Clikt, а не `echo(..., err = true)`
- [x] 2.4 Создать группирующие узлы `OrgCommand` (name = `org`) и `AdminCommand` (name = `admin`) на `SuspendingNoOpCliktCommand`; проверить, что `athletica admin --help` и `athletica admin org --help` печатают вложенные команды
- [x] 2.5 Собрать дерево в корневой `AthleticaCommand` (`SuspendingNoOpCliktCommand`, name = `athletica`) через `subcommands(...)`; проверить, что `athletica` без аргументов печатает help с двумя ветками `serve` и `admin`
- [x] 2.6 Удалить прежний класс `AdminCli` вместе с его `when {}`-роутером и ручными проверками; убедиться, что в `server/src/main` не осталось ссылок на него

## 3. Точка входа и зависимости

- [x] 3.1 Заменить `fun main(args) = EngineMain.main(args)` в `Application.kt` на запуск корневой Clikt-команды с единственным `runBlocking`; проверить, что `./gradlew :server:compileKotlin` проходит и вторая точка входа (`admin/Main.kt`) отсутствует
- [x] 3.2 Зарегистрировать `AdminDi` в корневой команде через `findOrSetObject { AdminDi.fromEnv() }`, читать в листовых командах через `requireObject<AdminDi>()`; проверить, что `athletica --help` и `athletica serve --help` работают при полностью снятых `POSTGRES_URL`/`POSTGRES_USER`/`POSTGRES_PASSWORD`
- [x] 3.3 Убедиться, что админские команды не запускают Liquibase и фоновые корутины: выполнить `athletica admin org find <query>` и проверить по логам отсутствие миграций и сообщений воркеров

## 4. Артефакт и Docker

- [x] 4.1 Задать `archiveFileName` = `athletica.jar` для fat-jar задачи в `server/build.gradle.kts`; проверить, что `./gradlew :server:shadowJar` создаёт `server/build/libs/athletica.jar`
- [x] 4.2 Обновить `Dockerfile.server`: копировать `athletica.jar` вместо `server-all.jar`, задать `ENTRYPOINT ["java","-jar","athletica.jar"]` и `CMD ["serve"]`; проверить локальной сборкой образа, что `docker run <image>` поднимает сервер
- [x] 4.3 Проверить, что `docker run <image> admin org find <query>` (переопределение `CMD`) доходит до разбора команды, и что `docker-compose.prod.yaml` правок не потребовал

## 5. Соответствие спецификации

- [x] 5.1 Добавить тесты разбора аргументов через Clikt-хелпер `test()`: корректный роутинг `serve` / `admin org find` / `admin org credit`, ошибка при отсутствующем `--description`, ошибка при неизвестной подкоманде; проверить `./gradlew :server:test --tests "*Cli*"`
- [x] 5.2 Проверить сценарии требования «Человекочитаемый вывод»: суммы корректировки и баланса печатаются через `Money.formatted` в валюте организации, сообщения об ошибках уходят в stderr, а stdout не содержит частичного результата
- [x] 5.3 Проверить коды возврата по сценариям спецификации: ненулевой при неизвестной подкоманде, при отсутствующем `--description`, при ненайденной организации и при незаданных `POSTGRES_*`; нулевой при пустом результате `admin org find`
- [x] 5.4 Проверить сценарии требования «Разрешение организации по идентификатору или названию»: поиск по UUID, по однозначному названию, отказ с выводом списка при неоднозначном названии, ошибка при отсутствии совпадений

## 6. Документация и финальная проверка

- [x] 6.1 Перенести содержимое `admin-cli/README.md` в документацию сервера с актуализированными командами (`athletica admin org ...`), способом запуска в проде через `docker compose exec` и требованием к версии Java, соответствующим базовому образу из `Dockerfile.server`
- [x] 6.2 Прогнать обязательные проверки из `CLAUDE.md`: `./gradlew build` и `./gradlew ktlintFormat`
