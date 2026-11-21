# Tasks

## 1. Гостевые страницы не уводят на вход при неудачном обновлении сессии

- [x] 1.1 В `web/src/app/router.tsx` объявить множество гостевых путей (`/login`, `/sign-up`) рядом с маршрутами. `redirectToLogin` ничего не делает, если `router.latestLocation.pathname` входит в это множество. В KDoc `AppRouting.redirectToLogin` записать новое условие. Проверка — тест группы 1.2.
- [x] 1.2 В `web/src/app/router.test.tsx` дать `openApp` возможность подключить `onSessionExpired` к `routing.redirectToLogin`, как в `main.tsx`. Добавить тесты: гость на `/sign-up` остаётся на `/sign-up` после отвергнутого refresh; гость на `/login?redirect=/clients` сохраняет `redirect=/clients`. Проверка — `npm run test` в `web/`.

## 2. Проверка сессии на страницах входа и регистрации

- [x] 2.1 В `router.tsx` добавить функцию проверки сессии, общую для `loginRoute` и `signUpRoute`. Она всегда вызывает `api.call("auth/me")` без кэша. При успехе кладёт ответ в кэш `sessionQuery` и бросает `redirect({ href: target, replace: true })`. Любая ошибка (`unauthenticated`, `unavailable`, `contract`, `business`) ведёт к показу формы, без исключения. KDoc на русском. Проверка — `npm run check` в `web/` проходит.
- [x] 2.2 Подключить проверку в `beforeLoad` у `loginRoute` (цель — `search.redirect ?? "/"`, уже проверенная `LoginSearchSchema`) и у `signUpRoute` (цель — `"/"`). Проверка — тесты 2.3.
- [x] 2.3 Тесты в `router.test.tsx` по сценариям требования «Страницы входа и регистрации только для гостей»:
  - вошедший на `/login` попадает на `/`;
  - `/login?redirect=/clients?x=1` ведёт на `/clients` с сохранённым query;
  - `redirect=https://evil.example/` ведёт на `/`;
  - вошедший на `/sign-up` попадает на `/`;
  - после перехода в истории нет записи `/login` (`replace`);
  - гость на `/login` и `/sign-up` видит формы;
  - при сессии в кэше `queryClient`, но 401 от сервера, на `/login` видна форма и нет цикла переходов;
  - при недоступной сети (`fetch` отклоняется) на `/login` видна форма;
  - после перехода с `/login` в приложение `auth/me` вызван один раз.

  Проверка — `npm run test` в `web/`.
- [x] 2.4 Тест «истёк только access-токен»: `auth/me` сначала отвечает 401, `auth/refresh-token` — 200, повторный `auth/me` — 200. Открытие `/login` ведёт на `/`. Проверка — `npm run test` в `web/`.
- [x] 2.5 Тест выхода: после `auth/logout` открывается форма входа, а не приложение (refresh-cookie удалена, сервер отвечает 401). Проверка — `npm run test` в `web/`.

## 3. Итоговые проверки

- [x] 3.1 В `web/` выполнить `npm run format` и `npm run check` (typecheck, eslint, prettier, vitest): всё зелёное.
- [x] 3.2 Вручную в dev-сервере: войти, открыть `/login` и `/sign-up` — оба ведут на главную, «Назад» не возвращает на форму; выйти — `/login` и `/sign-up` показывают формы.
- [x] 3.3 `openspec validate redirect-authenticated-from-auth-pages --strict` проходит.

## Workflow follow-up

- Заархивировать change после ревью и мержа (`openspec-archive-change`).
