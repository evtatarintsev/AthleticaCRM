import { QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory } from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { createApiClient, type ApiClientOptions } from "@/api/client";
import { I18nProvider } from "@/i18n/I18nProvider";
import { ru } from "@/i18n/ru";
import { createQueryClient } from "@/query/queries";
import { createAppRouting, isStaticAppPath } from "./router";
import { migratedSections, sections } from "./sections";

const me = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000001",
  employeeId: "0199a0b2-7c3e-7d2a-9f10-000000000002",
  username: "coach@example.com",
  name: "Иван Петров",
  avatarId: null,
  orgInfo: { name: "Лига", balance: null },
  currentBranch: { id: "0199a0b2-7c3e-7d2a-9f10-000000000003", name: "Центр" },
};

/** JSON-ответ с кодом [status]. */
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * Открывает приложение на пути [path] (с базовым путём `/web`). [authenticated] — есть ли сессия:
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
  const history = createMemoryHistory({ initialEntries: [`/web${path}`] });
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

  it("неизвестный адрес — «не найдено»", async () => {
    openApp("/nope", true);

    expect(await screen.findByRole("heading", { name: ru["notFound.title"] })).toBeInTheDocument();
  });

  it("без сессии ведёт на вход и запоминает адрес возврата", async () => {
    const { history } = openApp("/?from=bookmark", false);

    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    await waitFor(() => {
      expect(history.location.pathname).toBe("/web/login");
    });
    expect(new URLSearchParams(history.location.search).get("redirect")).toBe("/?from=bookmark");
  });
});

describe("раскладка", () => {
  it("неперенесённый раздел открывается в KMP-клиенте обычной ссылкой", async () => {
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

  it("у каждого перенесённого раздела есть маршрут веб-клиента", () => {
    sections
      .filter((section) => migratedSections[section.id])
      .forEach((section) => {
        expect(isStaticAppPath(section.path)).toBe(true);
      });
  });
});
