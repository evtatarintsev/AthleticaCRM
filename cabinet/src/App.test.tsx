import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import type { ApiClientOptions } from "./api/client";
import { I18nProvider } from "./i18n/I18nProvider";

/** Ответ фейкового сервера на запрос с телом [body] и query [query]. */
type Handler = (body: unknown, query: URLSearchParams) => Response;

const ORG = "0190a000-0000-7000-8000-000000000001";
const IVAN = "0190a000-0000-7000-8000-000000000002";
const MARIA = "0190a000-0000-7000-8000-000000000003";

/** JSON-ответ с кодом [status]. */
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Пустой успешный ответ. */
function ok(): Response {
  return new Response(null, { status: 200 });
}

/** Профиль клиента [clientId] с именем [clientName] в «Динамо». */
function profile(clientId: string, clientName: string) {
  return { orgId: ORG, orgName: "Динамо", clientId, clientName };
}

/** Фейковый сервер: маршрут `client-app/...` → обработчик; неизвестный маршрут — 404. */
function server(routes: Readonly<Record<string, Handler>>) {
  const calls: { path: string; body: unknown }[] = [];
  const fetch = vi.fn<ApiClientOptions["fetch"]>((input, init) => {
    const url = new URL(input, "https://clients.example.com");
    const path = url.pathname.replace("/api/", "");
    const body: unknown = typeof init.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ path, body });
    const handler = routes[path];
    return Promise.resolve(
      handler === undefined ? new Response(null, { status: 404 }) : handler(body, url.searchParams),
    );
  });
  return { fetch, calls };
}

/** Рендерит кабинет с запросами в [fetch]. */
function renderApp(fetch: ApiClientOptions["fetch"]) {
  render(
    <I18nProvider initialLocale="ru">
      <App fetch={fetch} />
    </I18nProvider>,
  );
}

/** Обработчик главной: имя клиента из [names] по `clientId` запроса. */
function home(names: Readonly<Record<string, string>>): Handler {
  return (_body, query) =>
    json({ clientName: names[query.get("clientId") ?? ""] ?? "?", orgName: "Динамо" });
}

/** Баланс клиента: 500 ₽ после начисления, два неоплаченных занятия. */
const BALANCE = {
  balance: { minorUnits: 50_000, currency: "RUB" },
  unpaidSessions: 2,
  entries: [
    {
      id: "0197a3c2-0000-7000-8000-000000000001",
      occurredOn: "2026-11-20",
      kind: "ADJUSTMENT",
      amount: { minorUnits: 50_000, currency: "RUB" },
    },
  ],
};

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("вход", () => {
  it.each([
    ["CLIENT_APP_CODE_WRONG", "Неверный код"],
    ["CLIENT_APP_CODE_EXPIRED", "Код истёк"],
  ])(
    "после условий ошибка %s позволяет исправить код или запросить новый",
    async (errorCode, message) => {
      const startedAt = Date.now();
      const now = vi.spyOn(Date, "now").mockReturnValue(startedAt);
      let accepted = false;
      const { fetch, calls } = server({
        "client-app/profiles": () => new Response(null, { status: 401 }),
        "client-app/auth/refresh": () => new Response(null, { status: 401 }),
        "client-app/auth/request-code": () =>
          json({ sent: true, retryAfterSeconds: 60, termsVersion: "v1" }),
        "client-app/auth/login": () => {
          const result = json(
            {
              code: accepted ? errorCode : "CLIENT_APP_TERMS_REQUIRED",
              message: accepted ? message : "Примите условия",
              fields: null,
            },
            400,
          );
          accepted = true;
          return result;
        },
      });
      renderApp(fetch);
      fireEvent.change(await screen.findByLabelText("Номер телефона"), {
        target: { value: "+79031234567" },
      });
      fireEvent.click(screen.getByRole("button", { name: "Получить код" }));
      fireEvent.change(await screen.findByLabelText("Код из сообщения"), {
        target: { value: "000000" },
      });
      expect(screen.getByText("Новый код можно запросить через 1:00")).toBeInTheDocument();
      fireEvent.click(screen.getByRole("button", { name: "Войти" }));
      const accept = await screen.findByRole("button", { name: "Принимаю условия и вхожу" });
      now.mockReturnValue(startedAt + 61_000);
      fireEvent.click(accept);
      expect(await screen.findByRole("alert")).toHaveTextContent(message);
      fireEvent.change(screen.getByLabelText("Код из сообщения"), { target: { value: "123456" } });
      expect(screen.getByLabelText("Код из сообщения")).toHaveValue("123456");
      fireEvent.click(screen.getByRole("button", { name: "Отправить по SMS" }));
      await vi.waitFor(() => {
        expect(calls.filter((it) => it.path === "client-app/auth/request-code")).toHaveLength(2);
      });
      expect(calls.at(-1)?.body).toEqual({ phone: "+79031234567", viaSms: true });
    },
  );

  it("номер → код → условия → главная", async () => {
    let signedIn = false;
    const { fetch, calls } = server({
      "client-app/profiles": () =>
        signedIn
          ? json({ profiles: [profile(IVAN, "Иван Петров")], newInvitations: 0 })
          : new Response(null, { status: 401 }),
      "client-app/auth/refresh": () => new Response(null, { status: 401 }),
      "client-app/auth/request-code": () =>
        json({ sent: true, retryAfterSeconds: 60, termsVersion: "v1" }),
      "client-app/auth/login": (body) => {
        const accepted =
          typeof body === "object" && body !== null && "acceptedTermsVersion" in body
            ? body.acceptedTermsVersion
            : null;
        if (accepted !== "v1") {
          return json(
            { code: "CLIENT_APP_TERMS_REQUIRED", message: "Примите условия", fields: null },
            400,
          );
        }
        signedIn = true;
        return ok();
      },
      "client-app/home": home({ [IVAN]: "Иван Петров" }),
      "client-app/balance": () => json(BALANCE),
    });
    renderApp(fetch);

    fireEvent.change(await screen.findByLabelText("Номер телефона"), {
      target: { value: "+7 903 123-45-67" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Получить код" }));
    fireEvent.change(await screen.findByLabelText("Код из сообщения"), {
      target: { value: "123456" },
    });
    expect(screen.getByText("Новый код можно запросить через 1:00")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Войти" }));
    fireEvent.click(await screen.findByRole("button", { name: "Принимаю условия и вхожу" }));

    expect(await screen.findByText("Это кабинет клиента Иван Петров")).toBeInTheDocument();
    expect(calls.filter((it) => it.path === "client-app/auth/login").map((it) => it.body)).toEqual([
      { phone: "+7 903 123-45-67", code: "123456", acceptedTermsVersion: null },
      { phone: "+7 903 123-45-67", code: "123456", acceptedTermsVersion: "v1" },
    ]);
  });

  it("после отсчёта можно отправить код по SMS", async () => {
    const { fetch, calls } = server({
      "client-app/profiles": () => new Response(null, { status: 401 }),
      "client-app/auth/refresh": () => new Response(null, { status: 401 }),
      "client-app/auth/request-code": () =>
        json({ sent: true, retryAfterSeconds: 0, termsVersion: "v1" }),
    });
    renderApp(fetch);

    fireEvent.change(await screen.findByLabelText("Номер телефона"), {
      target: { value: "+79031234567" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Получить код" }));
    fireEvent.click(await screen.findByRole("button", { name: "Отправить по SMS" }));

    await screen.findByLabelText("Код из сообщения");
    await vi.waitFor(() => {
      expect(
        calls.filter((it) => it.path === "client-app/auth/request-code").map((it) => it.body),
      ).toEqual([
        { phone: "+79031234567", viaSms: false },
        { phone: "+79031234567", viaSms: true },
      ]);
    });
  });

  it("неверный код — текст ошибки сервера", async () => {
    const { fetch } = server({
      "client-app/profiles": () => new Response(null, { status: 401 }),
      "client-app/auth/refresh": () => new Response(null, { status: 401 }),
      "client-app/auth/request-code": () =>
        json({ sent: true, retryAfterSeconds: 60, termsVersion: "v1" }),
      "client-app/auth/login": () =>
        json({ code: "CLIENT_APP_CODE_WRONG", message: "Неверный код", fields: null }, 400),
    });
    renderApp(fetch);

    fireEvent.change(await screen.findByLabelText("Номер телефона"), {
      target: { value: "+79031234567" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Получить код" }));
    fireEvent.change(await screen.findByLabelText("Код из сообщения"), {
      target: { value: "000000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Войти" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Неверный код");
  });
});

describe("приглашения", () => {
  it("«Подключить» открывает главную подключённого клиента, имён до подключения нет", async () => {
    let connected = false;
    const { fetch, calls } = server({
      "client-app/profiles": () =>
        json(
          connected
            ? { profiles: [profile(IVAN, "Иван Петров")], newInvitations: 0 }
            : { profiles: [], newInvitations: 1 },
        ),
      "client-app/invitations": () =>
        json({
          invitations: [
            {
              orgId: ORG,
              orgName: "Спортшкола «Динамо»",
              invitedAt: "2026-11-18T10:00:00Z",
              clientCount: 2,
            },
          ],
        }),
      "client-app/connect": () => {
        connected = true;
        return ok();
      },
      "client-app/home": home({ [IVAN]: "Иван Петров" }),
      "client-app/balance": () => json(BALANCE),
    });
    renderApp(fetch);

    expect(await screen.findByText("Спортшкола «Динамо»")).toBeInTheDocument();
    expect(
      screen.getByText(/Приглашение от 18 ноября · 2 клиента с вашим номером/),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Подключить" }));

    expect(await screen.findByText("Это кабинет клиента Иван Петров")).toBeInTheDocument();
    expect(calls.find((it) => it.path === "client-app/connect")?.body).toEqual({ orgId: ORG });
  });

  it("«Это не я» убирает приглашение; без профилей — «Профилей пока нет»", async () => {
    let rejected = false;
    const { fetch, calls } = server({
      "client-app/profiles": () => json({ profiles: [], newInvitations: rejected ? 0 : 1 }),
      "client-app/invitations": () =>
        json({
          invitations: [
            { orgId: ORG, orgName: "Динамо", invitedAt: "2026-11-18T10:00:00Z", clientCount: 1 },
          ],
        }),
      "client-app/not-me": () => {
        rejected = true;
        return ok();
      },
    });
    renderApp(fetch);

    fireEvent.click(await screen.findByRole("button", { name: "Это не я" }));

    expect(
      await screen.findByText("Профилей пока нет — спросите у администратора"),
    ).toBeInTheDocument();
    expect(calls.find((it) => it.path === "client-app/not-me")?.body).toEqual({ orgId: ORG });
  });
});

describe("профили", () => {
  it("переключатель меняет профиль, выбор запоминается на устройстве", async () => {
    const routes = {
      "client-app/profiles": () =>
        json({ profiles: [profile(IVAN, "Иван"), profile(MARIA, "Мария")], newInvitations: 1 }),
      "client-app/home": home({ [IVAN]: "Иван", [MARIA]: "Мария" }),
      "client-app/balance": () => json(BALANCE),
    };
    renderApp(server(routes).fetch);

    expect(await screen.findByText("Это кабинет клиента Иван")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Сменить профиль/ }));
    expect(screen.getByRole("button", { name: /Новые приглашения/ })).toHaveTextContent("1");
    fireEvent.click(screen.getByRole("button", { name: "Мария" }));
    expect(await screen.findByText("Это кабинет клиента Мария")).toBeInTheDocument();

    cleanup();
    renderApp(server(routes).fetch);
    expect(await screen.findByText("Это кабинет клиента Мария")).toBeInTheDocument();
  });

  it("все доступы закрыты — «Профилей пока нет»", async () => {
    renderApp(
      server({ "client-app/profiles": () => json({ profiles: [], newInvitations: 0 }) }).fetch,
    );

    expect(
      await screen.findByText("Профилей пока нет — спросите у администратора"),
    ).toBeInTheDocument();
  });
});

describe("аккаунт", () => {
  const CURRENT = "0190a000-0000-7000-8000-000000000010";
  const OLD = "0190a000-0000-7000-8000-000000000011";

  /** Сервер с профилем Ивана и сеансами [sessions] плюс дополнительные маршруты [routes]. */
  function withSessions(
    sessions: () => readonly unknown[],
    routes: Readonly<Record<string, Handler>> = {},
  ) {
    return server({
      "client-app/profiles": () => json({ profiles: [profile(IVAN, "Иван")], newInvitations: 0 }),
      "client-app/home": home({ [IVAN]: "Иван" }),
      "client-app/balance": () => json(BALANCE),
      "client-app/sessions": () => json({ sessions: sessions() }),
      ...routes,
    });
  }

  /** Сеанс [id] с названием [name]; [current] — этот браузер. */
  function session(id: string, name: string | null, current: boolean) {
    return {
      id,
      name,
      createdAt: "2026-11-01T10:00:00Z",
      lastSeenAt: "2026-11-20T10:00:00Z",
      current,
    };
  }

  it("список сеансов: текущий отмечен, другой можно завершить", async () => {
    let terminated = false;
    const { fetch, calls } = withSessions(
      () =>
        terminated
          ? [session(CURRENT, "Safari, iOS", true)]
          : [session(CURRENT, "Safari, iOS", true), session(OLD, "Chrome, Android", false)],
      {
        "client-app/sessions/terminate": () => {
          terminated = true;
          return ok();
        },
      },
    );
    renderApp(fetch);

    fireEvent.click(await screen.findByRole("button", { name: "Аккаунт и сеансы" }));
    expect(await screen.findByText("Этот браузер")).toBeInTheDocument();
    expect(screen.getByText("Safari, iOS")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Завершить сеанс Safari, iOS" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Завершить сеанс Chrome, Android" }));

    await vi.waitFor(() => {
      expect(screen.queryByText("Chrome, Android")).toBeNull();
    });
    expect(calls.find((it) => it.path === "client-app/sessions/terminate")?.body).toEqual({
      sessionId: OLD,
    });
  });

  it("выход возвращает к вводу номера", async () => {
    const { fetch, calls } = withSessions(() => [session(CURRENT, null, true)], {
      "client-app/auth/logout": () => ok(),
    });
    renderApp(fetch);

    fireEvent.click(await screen.findByRole("button", { name: "Аккаунт и сеансы" }));
    expect(await screen.findByText("Неизвестное устройство")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Выйти" }));

    expect(await screen.findByLabelText("Номер телефона")).toBeInTheDocument();
    expect(calls.some((it) => it.path === "client-app/auth/logout")).toBe(true);
  });

  it("удаление аккаунта — только после подтверждения, затем вход по номеру", async () => {
    const { fetch, calls } = withSessions(() => [session(CURRENT, "Safari, iOS", true)], {
      "client-app/account/delete": () => ok(),
    });
    renderApp(fetch);

    fireEvent.click(await screen.findByRole("button", { name: "Аккаунт и сеансы" }));
    fireEvent.click(await screen.findByRole("button", { name: "Удалить аккаунт" }));
    expect(screen.getByText(/Вы выйдете на всех устройствах/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Отмена" }));
    expect(calls.some((it) => it.path === "client-app/account/delete")).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Удалить аккаунт" }));
    fireEvent.click(screen.getByRole("button", { name: "Удалить навсегда" }));

    expect(await screen.findByLabelText("Номер телефона")).toBeInTheDocument();
    expect(calls.filter((it) => it.path === "client-app/account/delete")).toHaveLength(1);
  });
});

describe("баланс", () => {
  it("блок на главной и история с типом операции", async () => {
    const { fetch, calls } = server({
      "client-app/profiles": () => json({ profiles: [profile(IVAN, "Иван")], newInvitations: 0 }),
      "client-app/home": home({ [IVAN]: "Иван" }),
      "client-app/balance": () => json(BALANCE),
    });
    renderApp(fetch);
    expect(await screen.findByText("500,00 ₽")).toBeInTheDocument();
    expect(screen.getByText("Неоплаченных занятий: 2")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "История баланса" }));
    expect(await screen.findByText("Начисление")).toBeInTheDocument();
    expect(screen.getByText("+500,00 ₽")).toBeInTheDocument();
    expect(screen.getByText("20 ноября 2026 г.")).toBeInTheDocument();
    expect(calls.filter((it) => it.path === "client-app/balance")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "Назад" }));
    expect(await screen.findByText("Это кабинет клиента Иван")).toBeInTheDocument();
  });

  it("без движений и долга блока на главной нет", async () => {
    const { fetch } = server({
      "client-app/profiles": () => json({ profiles: [profile(IVAN, "Иван")], newInvitations: 0 }),
      "client-app/home": home({ [IVAN]: "Иван" }),
      "client-app/balance": () =>
        json({ balance: { minorUnits: 0, currency: "RUB" }, unpaidSessions: 0, entries: [] }),
    });
    renderApp(fetch);
    expect(await screen.findByText("Это кабинет клиента Иван")).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByText("Загрузка…")).not.toBeInTheDocument();
    });
    expect(screen.queryByText("Баланс")).not.toBeInTheDocument();
  });
});
