import { describe, expect, expectTypeOf, it, vi } from "vitest";
import { z } from "zod";
import { createApiClient, type ApiClientOptions, type CallArgs } from "./client";

/** Тестовый манифест: проверки клиента не зависят от сгенерированного контракта. */
const manifest = {
  "client-app/home": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: z.object({ greeting: z.string() }).readonly(),
  },
  "client-app/connect": {
    method: "POST",
    in: "body",
    request: z.object({ orgId: z.string() }).readonly(),
    out: "empty",
    response: z.undefined(),
  },
  "client-app/auth/login": {
    method: "POST",
    in: "body",
    request: z.object({ phone: z.string(), code: z.string() }).readonly(),
    out: "empty",
    response: z.undefined(),
  },
} as const;

/** JSON-ответ с кодом [status]. */
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Клиент с подменённым [fetchImpl] и шпионами на побочные эффекты. */
function client(fetchImpl: ApiClientOptions["fetch"]) {
  const options = {
    fetch: vi.fn<ApiClientOptions["fetch"]>(fetchImpl),
    language: () => "en",
    onSessionExpired: vi.fn<ApiClientOptions["onSessionExpired"]>(),
    reportContractViolation: vi.fn<ApiClientOptions["reportContractViolation"]>(),
  } satisfies ApiClientOptions;
  return { api: createApiClient(manifest, options), options };
}

/** URL, по которым ходил [fetchMock]. */
function urls(fetchMock: ReturnType<typeof vi.fn<ApiClientOptions["fetch"]>>): string[] {
  return fetchMock.mock.calls.map(([url]) => url);
}

describe("типы вызова", () => {
  it("эндпоинт без запроса вызывается без аргументов, с запросом — с ним", () => {
    expectTypeOf<CallArgs<typeof manifest, "client-app/home">>().toEqualTypeOf<[]>();
    expectTypeOf<CallArgs<typeof manifest, "client-app/connect">>().toEqualTypeOf<
      [request: Readonly<{ orgId: string }>]
    >();
  });
});

describe("createApiClient", () => {
  it("декодирует ответ и отбрасывает лишние поля", async () => {
    const { api, options } = client(() =>
      Promise.resolve(json({ greeting: "Hello", extra: true })),
    );
    expect(await api.call("client-app/home")).toEqual({ ok: true, value: { greeting: "Hello" } });
    expect(urls(options.fetch)).toEqual(["/api/client-app/home"]);
  });

  it("ответ не по контракту — ошибка contract и отчёт о нарушении", async () => {
    const { api, options } = client(() => Promise.resolve(json({ greeting: 42 })));
    const result = await api.call("client-app/home");
    expect(result).toMatchObject({
      ok: false,
      error: { kind: "contract", endpoint: "client-app/home" },
    });
    expect(options.reportContractViolation).toHaveBeenCalledOnce();
  });

  it("на 401 обновляет сеанс и повторяет запрос", async () => {
    const responses = [
      json({}, 401),
      new Response(null, { status: 200 }),
      json({ greeting: "Hi" }),
    ];
    const { api, options } = client(() => Promise.resolve(responses.shift() ?? json({}, 500)));
    expect(await api.call("client-app/home")).toEqual({ ok: true, value: { greeting: "Hi" } });
    expect(urls(options.fetch)).toEqual([
      "/api/client-app/home",
      "/api/client-app/auth/refresh",
      "/api/client-app/home",
    ]);
  });

  it("отказ в обновлении завершает сеанс", async () => {
    const { api, options } = client(() => Promise.resolve(json({}, 401)));
    expect(await api.call("client-app/home")).toEqual({
      ok: false,
      error: { kind: "unauthenticated" },
    });
    expect(options.onSessionExpired).toHaveBeenCalledOnce();
  });

  it("лимит запросов на обновлении не завершает сеанс", async () => {
    const responses = [json({}, 401), json({}, 429)];
    const { api, options } = client(() => Promise.resolve(responses.shift() ?? json({}, 500)));
    expect(await api.call("client-app/home")).toEqual({
      ok: false,
      error: { kind: "unavailable" },
    });
    expect(options.onSessionExpired).not.toHaveBeenCalled();
  });

  it("401 после успешного обновления завершает сеанс", async () => {
    const responses = [json({}, 401), new Response(null, { status: 200 }), json({}, 401)];
    const { api, options } = client(() => Promise.resolve(responses.shift() ?? json({}, 500)));
    expect(await api.call("client-app/home")).toEqual({
      ok: false,
      error: { kind: "unauthenticated" },
    });
    expect(options.onSessionExpired).toHaveBeenCalledOnce();
  });

  it("401 на входе — неверные данные, без обновления сеанса", async () => {
    const { api, options } = client(() => Promise.resolve(json({}, 401)));
    await api.call("client-app/auth/login", { phone: "79001234567", code: "123456" });
    expect(urls(options.fetch)).toEqual(["/api/client-app/auth/login"]);
    expect(options.onSessionExpired).not.toHaveBeenCalled();
  });

  it("бизнес-ошибка отдаёт код и сообщение", async () => {
    const { api } = client(() =>
      Promise.resolve(json({ code: "CLIENT_NOT_FOUND", message: "Нет", fields: null }, 400)),
    );
    expect(await api.call("client-app/connect", { orgId: "org" })).toEqual({
      ok: false,
      error: { kind: "business", status: 400, code: "CLIENT_NOT_FOUND", message: "Нет" },
    });
  });

  it("сбой сети — unavailable", async () => {
    const { api } = client(() => Promise.reject(new TypeError("offline")));
    expect(await api.call("client-app/home")).toEqual({
      ok: false,
      error: { kind: "unavailable" },
    });
  });
});
