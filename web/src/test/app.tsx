import { QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { vi } from "vitest";
import { createApiClient, type ApiClientOptions } from "@/api/client";
import { createAppRouting } from "@/app/router";
import { I18nProvider } from "@/i18n/I18nProvider";
import { createQueryClient } from "@/query/queries";

/** Запрос к поддельному серверу: путь без `/api/` и query-строки, тело и query-параметры. */
export interface FakeRequest {
  readonly path: string;
  readonly body: unknown;
  readonly query: URLSearchParams;
}

/** Обработчик эндпоинта поддельного сервера. */
export type FakeHandler = (request: FakeRequest) => Response;

/** JSON-ответ [body] с кодом [status]. */
export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Пустой успешный ответ. */
export function empty(): Response {
  return new Response(null, { status: 200 });
}

/** Тело запроса [init]: разобранный JSON, `FormData` или `undefined`. */
function requestBody(init: RequestInit): unknown {
  const { body } = init;
  if (typeof body === "string") {
    const parsed: unknown = JSON.parse(body);
    return parsed;
  }
  return body instanceof FormData ? body : undefined;
}

/**
 * Поддельный сервер: [handlers] по пути эндпоинта; неизвестный путь отвечает 404.
 * [requests] — все запросы по порядку, чтобы проверять, что и с каким телом ушло.
 */
export function fakeServer(handlers: Readonly<Record<string, FakeHandler>>) {
  const requests: FakeRequest[] = [];
  const fetch = vi.fn<ApiClientOptions["fetch"]>((input, init) => {
    const url = new URL(input, "http://localhost");
    const request: FakeRequest = {
      path: url.pathname.replace(/^\/api\//, ""),
      body: requestBody(init),
      query: url.searchParams,
    };
    requests.push(request);
    const handler = handlers[request.path];
    return Promise.resolve(
      handler === undefined ? json({ code: "NOT_FOUND", message: "" }, 404) : handler(request),
    );
  });
  return {
    fetch,
    requests,
    /** Запросы к эндпоинту [path]. */
    to: (path: string) => requests.filter((r) => r.path === path),
  };
}

/** Открывает приложение на пути [path] (от базового `/web`) поверх `fetch` поддельного сервера. */
export function openApp(
  path: string,
  fetch: ApiClientOptions["fetch"],
): { history: RouterHistory } {
  const queryClient = createQueryClient();
  const api = createApiClient({
    fetch,
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  const history = createMemoryHistory({ initialEntries: [`/web${path}`] });
  const routing = createAppRouting({ api, queryClient }, history);
  render(
    <I18nProvider initialLocale="ru">
      <QueryClientProvider client={queryClient}>{routing.element}</QueryClientProvider>
    </I18nProvider>,
  );
  return { history };
}

/** Филиал «Центр» тестовой сессии. */
export const center = { id: "0199a0b2-7c3e-7d2a-9f10-000000000003", name: "Центр" };

/** Сессия пользователя с именем [name], аватаром [avatarId] и филиалом [branch]. */
export function session(name = "Иван Петров", avatarId: string | null = null, branch = center) {
  return {
    id: "0199a0b2-7c3e-7d2a-9f10-000000000001",
    employeeId: "0199a0b2-7c3e-7d2a-9f10-000000000002",
    username: "coach@example.com",
    name,
    avatarId,
    orgInfo: { name: "Лига", balance: { minorUnits: 150000, currency: "RUB" } },
    currentBranch: branch,
  };
}

/** Поддельный сервер с эндпоинтами, которые запрашивает раскладка на любой странице. */
export function appServer(handlers: Readonly<Record<string, FakeHandler>>) {
  return fakeServer({
    "auth/me": () => json(session()),
    "auth/my-branches": () => json({ branches: [center] }),
    notifications: () => json({ notifications: [], unreadCount: 0 }),
    ...handlers,
  });
}
