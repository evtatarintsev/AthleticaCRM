import { expect, type APIRequestContext } from "@playwright/test";
import { z } from "zod";
import { endpoints } from "../../web/src/api/generated/contracts";

/** Все эндпоинты API из сгенерированного манифеста веб-клиента. */
type Endpoints = typeof endpoints;

/** Путь эндпоинта без префикса `/api/`, например `"clients/list"`. */
export type EndpointPath = keyof Endpoints;

/** Данные запроса эндпоинта [P] до кодирования схемой. */
export type RequestOf<P extends EndpointPath> = z.input<Endpoints[P]["request"]>;

/** Декодированный ответ эндпоинта [P]. */
export type ResponseOf<P extends EndpointPath> = z.output<Endpoints[P]["response"]>;

/**
 * Вызывает эндпоинт [path] с данными [data] от имени сессии [request], ожидает 200
 * и декодирует ответ схемой того же эндпоинта: расхождение контракта со стендом — падение теста.
 * Поддерживает тело JSON и query; multipart — через `request.post` напрямую.
 */
export function call<P extends EndpointPath>(
  request: APIRequestContext,
  path: P,
  data: RequestOf<P>,
): Promise<ResponseOf<P>>;
export async function call(
  request: APIRequestContext,
  path: EndpointPath,
  data: unknown,
): Promise<unknown> {
  const definition = endpoints[path];
  const url = `/api/${path}`;
  const response =
    definition.in === "query"
      ? await request.fetch(url, { method: definition.method, params: queryParams(data) })
      : await request.fetch(url, {
          method: definition.method,
          ...(definition.in === "body" ? { data } : {}),
        });
  const text = await response.text();
  expect(response.status(), `${path}: ${text}`).toBe(200);
  if (definition.out !== "json") {
    return undefined;
  }
  const payload: unknown = text === "" ? undefined : JSON.parse(text);
  // Сервер опускает ложный emailVerificationPending; web/src/api/client.ts дополняет его так же.
  return z.parse(
    definition.response,
    path === "auth/me" && typeof payload === "object" && payload !== null
      ? { emailVerificationPending: false, ...payload }
      : payload,
  );
}

/** Плоские query-параметры из [data], как их кодирует веб-клиент. */
function queryParams(data: unknown): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {};
  if (typeof data === "object" && data !== null) {
    Object.entries(data).forEach(([key, value]) => {
      if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
        params[key] = value;
      }
    });
  }
  return params;
}
