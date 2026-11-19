import { QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory } from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { createApiClient, type ApiClientOptions } from "@/api/client";
import { AuthMeResponseSchema } from "@/api/generated/contracts";
import { I18nProvider } from "@/i18n/I18nProvider";
import { ru } from "@/i18n/ru";
import { createQueryClient } from "@/query/queries";
import { createAppRouting, isStaticAppPath } from "./router";
import { sections } from "./sections";
import * as fake from "@/test/app";

const me = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000001",
  employeeId: "0199a0b2-7c3e-7d2a-9f10-000000000002",
  username: "coach@example.com",
  name: "Иван Петров",
  avatarId: null,
  orgInfo: { name: "Лига", balance: null },
  currentBranch: { id: "0199a0b2-7c3e-7d2a-9f10-000000000003", name: "Центр" },
  permissions: [],
  emailVerificationPending: false,
};

/** JSON-ответ с кодом [status]. */
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * Открывает приложение на пути [path]. [authenticated] — есть ли сессия:
 * без неё `/auth/me` и обновление токена отвечают 401.
 */
function openApp(path: string, authenticated: boolean) {
  const fetch = vi.fn<ApiClientOptions["fetch"]>((url) => {
    if (url === "/api/auth/me" && authenticated) {
      return Promise.resolve(json(me));
    }
    return Promise.resolve(json({ code: "UNAUTHORIZED", message: "" }, 401));
  });
  const queryClient = createQueryClient();
  const api = createApiClient({
    fetch,
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  const history = createMemoryHistory({ initialEntries: [path] });
  const routing = createAppRouting({ api, queryClient }, history);
  render(
    <I18nProvider initialLocale="ru">
      <QueryClientProvider client={queryClient}>{routing.element}</QueryClientProvider>
    </I18nProvider>,
  );
  return { history, fetch };
}

describe("маршрутизация", () => {
  it("некорректный идентификатор клиента — «не найдено» без запроса к API", async () => {
    const { fetch } = openApp("/clients/not-a-uuid", true);

    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("отдельных страниц задачи больше нет — «не найдено»", async () => {
    openApp("/tasks/0199a0b2-7c3e-7d2a-9f10-000000000501", true);

    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("отдельных страниц формы сотрудника больше нет — «не найдено»", async () => {
    openApp("/employees/new", true);
    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("адрес редактирования сотрудника больше не существует — «не найдено»", async () => {
    openApp("/employees/0199a0b2-7c3e-7d2a-9f10-000000000403/edit", true);
    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("отдельных страниц формы группы больше нет — «не найдено»", async () => {
    openApp("/groups/new", true);
    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("адрес редактирования группы больше не существует — «не найдено»", async () => {
    openApp("/groups/0199a0b2-7c3e-7d2a-9f10-000000000601/edit", true);
    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("отдельной страницы карточки сотрудника больше нет — «не найдено»", async () => {
    openApp("/employees/0199a0b2-7c3e-7d2a-9f10-000000000403", true);
    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("некорректный идентификатор сотрудника в адресе — список без панели и без запроса карточки", async () => {
    const { fetch } = openApp("/employees?employee=not-a-uuid", true);

    expect(await screen.findByRole("heading", { name: ru["employees.title"] })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetch.mock.calls.map(([url]) => url)).not.toContain("/api/employees/detail");
  });

  it("некорректный идентификатор задачи в адресе — список без панели и без запроса карточки", async () => {
    const { fetch } = openApp("/tasks?task=not-a-uuid", true);

    expect(await screen.findByRole("heading", { name: ru["tasks.title"] })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetch.mock.calls.map(([url]) => url)).not.toContain("/api/tasks/detail");
  });

  it("неизвестный адрес — «не найдено»", async () => {
    openApp("/nope", true);

    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("без сессии ведёт на вход и запоминает адрес возврата", async () => {
    const { history } = openApp("/?from=bookmark", false);

    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    await waitFor(() => {
      expect(history.location.pathname).toBe("/login");
    });
    expect(new URLSearchParams(history.location.search).get("redirect")).toBe("/?from=bookmark");
  });
});

describe("раскладка", () => {
  it("каждый раздел открывается ссылкой веб-клиента", async () => {
    openApp("/", true);

    const tasks = await screen.findAllByRole("link", { name: ru["nav.tasks"] });
    tasks.forEach((link) => {
      expect(link).toHaveAttribute("href", "/tasks");
    });
    expect(screen.getAllByRole("link", { name: ru["nav.home"] })[0]).toHaveAttribute("href", "/");
  });

  it("выделяет текущий раздел", async () => {
    openApp("/", true);

    const [home] = await screen.findAllByRole("link", { name: ru["nav.home"] });
    expect(home).toHaveAttribute("aria-current", "page");
    expect(screen.getAllByRole("link", { name: ru["nav.clients"] })[0]).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("показывает пользователя в меню аккаунта", async () => {
    openApp("/", true);

    expect(await screen.findByRole("button", { name: ru["account.menu"] })).toHaveTextContent(
      "Иван Петров",
    );
  });

  it("у каждого раздела есть маршрут веб-клиента", () => {
    sections.forEach((section) => {
      expect(isStaticAppPath(section.path)).toBe(true);
    });
  });
});

/** Ответ сервера «нет сессии». */
function unauthorized(): Response {
  return fake.json({ code: "UNAUTHORIZED", message: "" }, 401);
}

/** Сервер гостя: сессии нет, обновить её нельзя. */
function guestServer() {
  return fake.fakeServer({ "auth/me": unauthorized, "auth/refresh-token": unauthorized });
}

describe("страницы для гостей", () => {
  it("вошедшего пользователя вход ведёт на главную и не оставляет себя в истории", async () => {
    const api = fake.appServer({});
    const { history } = fake.openApp("/login", api.fetch);

    await waitFor(() => {
      expect(history.location.pathname).toBe("/");
    });
    expect(screen.queryByRole("heading", { name: ru["auth.loginTitle"] })).not.toBeInTheDocument();
    expect(history.length).toBe(1);
    expect(api.to("auth/me")).toHaveLength(1);
  });

  it("вошедшего пользователя вход ведёт на адрес возврата вместе с его query", async () => {
    const api = fake.appServer({});
    const search = new URLSearchParams({ redirect: "/clients?sort=balance" });
    const { history } = fake.openApp(`/login?${search.toString()}`, api.fetch);

    await waitFor(() => {
      expect(history.location.pathname).toBe("/clients");
    });
    expect(new URLSearchParams(history.location.search).get("sort")).toBe("balance");
  });

  it("подменённый адрес возврата ведёт вошедшего пользователя на главную", async () => {
    const api = fake.appServer({});
    const search = new URLSearchParams({ redirect: "https://evil.example/" });
    const { history } = fake.openApp(`/login?${search.toString()}`, api.fetch);

    await waitFor(() => {
      expect(history.location.pathname).toBe("/");
    });
  });

  it("вошедшего пользователя регистрация ведёт на главную", async () => {
    const api = fake.appServer({});
    const { history } = fake.openApp("/sign-up", api.fetch);

    await waitFor(() => {
      expect(history.location.pathname).toBe("/");
    });
    expect(
      screen.queryByRole("heading", { name: ru["auth.registerTitle"] }),
    ).not.toBeInTheDocument();
  });

  it("истёк только токен доступа — сессия обновляется, вход ведёт на главную", async () => {
    let accessValid = false;
    const api = fake.appServer({
      "auth/me": () => (accessValid ? fake.json(fake.session()) : unauthorized()),
      "auth/refresh-token": () => {
        accessValid = true;
        return fake.json({});
      },
    });
    const { history } = fake.openApp("/login", api.fetch);

    await waitFor(() => {
      expect(history.location.pathname).toBe("/");
    });
    expect(api.to("auth/refresh-token")).toHaveLength(1);
  });

  it("гость видит форму входа, адрес возврата сохраняется", async () => {
    const api = guestServer();
    const { history } = fake.openApp("/login?redirect=%2Fclients", api.fetch);

    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    await waitFor(() => {
      expect(api.to("auth/refresh-token")).toHaveLength(1);
    });
    expect(history.location.pathname).toBe("/login");
    expect(new URLSearchParams(history.location.search).get("redirect")).toBe("/clients");
  });

  it("гость остаётся на странице регистрации", async () => {
    const api = guestServer();
    const { history } = fake.openApp("/sign-up", api.fetch);

    expect(
      await screen.findByRole("heading", { name: ru["auth.registerTitle"] }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(api.to("auth/refresh-token")).toHaveLength(1);
    });
    expect(history.location.pathname).toBe("/sign-up");
  });

  it("устаревшая сессия в кэше не уводит со входа и не зацикливает переходы", async () => {
    const api = guestServer();
    const { history } = fake.openApp(
      "/login",
      api.fetch,
      AuthMeResponseSchema.parse(fake.session()),
    );

    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    await waitFor(() => {
      expect(api.to("auth/refresh-token")).toHaveLength(1);
    });
    expect(history.location.pathname).toBe("/login");
    expect(api.to("auth/me")).toHaveLength(1);
  });

  it("сервер недоступен — показывается форма входа", async () => {
    const fetch = vi.fn<ApiClientOptions["fetch"]>(() => Promise.reject(new TypeError("offline")));
    const { history } = fake.openApp("/login", fetch);

    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    expect(history.location.pathname).toBe("/login");
  });
});
