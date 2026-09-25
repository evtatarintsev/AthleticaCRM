import { describe, expect, expectTypeOf, it, vi } from "vitest";
import { createApiClient, type ApiClientOptions, type CallArgs, type RequestOf } from "./client";
import type { ClientId, GroupId, HallListResponse } from "./generated/contracts";
import { BranchIdSchema, ClientIdSchema, HallIdSchema } from "./generated/contracts";

const hallId = HallIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000001");
const clientId = ClientIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000002");

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
  return { api: createApiClient(options), options };
}

/** URL, по которым ходил [fetchMock]. */
function urls(fetchMock: ReturnType<typeof vi.fn<ApiClientOptions["fetch"]>>): string[] {
  return fetchMock.mock.calls.map(([url]) => url);
}

describe("типы вызова", () => {
  it("идентификаторы разных сущностей не взаимозаменяемы", () => {
    expectTypeOf<RequestOf<"clients/detail">>().toEqualTypeOf<Readonly<{ id: ClientId }>>();
    expectTypeOf<GroupId>().not.toExtend<ClientId>();
    expectTypeOf<{ id: GroupId }>().not.toExtend<RequestOf<"clients/detail">>();
  });

  it("эндпоинт без запроса вызывается без аргументов, параметры пути обязательны", () => {
    expectTypeOf<CallArgs<"halls/list">>().toEqualTypeOf<[]>();
    expectTypeOf<CallArgs<"sessions/{id}/cancel">>().toEqualTypeOf<
      [params: Readonly<Record<"id", string>>]
    >();
  });
});

describe("createApiClient", () => {
  it("декодирует ответ и отбрасывает лишние поля", async () => {
    const { api } = client(() =>
      Promise.resolve(json({ halls: [{ id: hallId, name: "Большой", extra: true }], extra: 1 })),
    );

    const result = await api.call("halls/list");

    const expected: HallListResponse = { halls: [{ id: hallId, name: "Большой" }] };
    expect(result).toEqual({ ok: true, value: expected });
  });

  it("поле не того типа — ошибка контракта с путём к полю", async () => {
    const { api, options } = client(() =>
      Promise.resolve(json({ halls: [{ id: hallId, name: 42 }] })),
    );

    const result = await api.call("halls/list");

    const error = result.ok ? null : result.error;
    expect(error).toMatchObject({ kind: "contract", endpoint: "halls/list" });
    expect(error?.kind === "contract" ? error.issues : []).toHaveLength(1);
    expect(error?.kind === "contract" ? error.issues.join() : "").toContain("halls.0.name");
    expect(options.reportContractViolation).toHaveBeenCalledOnce();
  });

  it("GET передаёт запрос query-параметрами, а язык — заголовком", async () => {
    const { api, options } = client(() => Promise.resolve(json({}, 500)));

    await api.call("clients/detail", { id: clientId });

    const [url, init] = options.fetch.mock.calls[0] ?? ["", {}];
    expect(url).toBe(`/api/clients/detail?id=${clientId}`);
    expect(new Headers(init.headers).get("Accept-Language")).toBe("en");
  });

  it("подставляет параметры пути", async () => {
    const { api, options } = client(() => Promise.resolve(new Response(null, { status: 200 })));

    const result = await api.call("sessions/{id}/cancel", { id: "s-1" });

    expect(result).toEqual({ ok: true, value: undefined });
    expect(urls(options.fetch)).toEqual(["/api/sessions/s-1/cancel"]);
  });

  it("4xx — ошибка бизнес-логики с кодом и сообщением сервера", async () => {
    const { api } = client(() =>
      Promise.resolve(
        json({ code: "BRANCH_ACCESS_DENIED", message: "Нет доступа", fields: null }, 400),
      ),
    );

    const result = await api.call("halls/list");

    expect(result).toEqual({
      ok: false,
      error: {
        kind: "business",
        status: 400,
        code: "BRANCH_ACCESS_DENIED",
        message: "Нет доступа",
      },
    });
  });

  it("сеть недоступна — ошибка без исключения", async () => {
    const { api } = client(() => Promise.reject(new TypeError("Failed to fetch")));

    const result = await api.call("halls/list");

    expect(result).toEqual({ ok: false, error: { kind: "unavailable" } });
  });

  it("три параллельных 401 обновляют сессию один раз и повторяют запросы", async () => {
    let refreshed = false;
    const { api, options } = client((url) => {
      if (url === "/api/auth/refresh-token") {
        refreshed = true;
        return Promise.resolve(json({ accessToken: "a", refreshToken: "r" }));
      }
      return Promise.resolve(refreshed ? json({ halls: [] }) : json("expired", 401));
    });

    const results = await Promise.all([
      api.call("halls/list"),
      api.call("halls/list"),
      api.call("halls/list"),
    ]);

    expect(results).toEqual(Array.from({ length: 3 }, () => ({ ok: true, value: { halls: [] } })));
    expect(urls(options.fetch).filter((url) => url === "/api/auth/refresh-token")).toHaveLength(1);
    expect(urls(options.fetch).filter((url) => url === "/api/halls/list")).toHaveLength(6);
  });

  it("неудачное обновление сессии — ошибка unauthenticated", async () => {
    const { api, options } = client((url) =>
      Promise.resolve(url === "/api/auth/refresh-token" ? json({}, 400) : json("expired", 401)),
    );

    const result = await api.call("halls/list");

    expect(result).toEqual({ ok: false, error: { kind: "unauthenticated" } });
    expect(options.onSessionExpired).toHaveBeenCalledOnce();
  });

  it("401 на входе не обновляет сессию", async () => {
    const { api, options } = client(() => Promise.resolve(json({}, 401)));

    const result = await api.call("auth/login", {
      username: "u",
      password: "p",
      branchId: BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000003"),
    });

    expect(result).toEqual({ ok: false, error: { kind: "unauthenticated" } });
    expect(urls(options.fetch)).toEqual(["/api/auth/login"]);
  });
});
