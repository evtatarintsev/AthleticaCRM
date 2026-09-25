import { z } from "zod";
import { endpoints, ErrorResponseSchema } from "./generated/contracts";

/** Все эндпоинты API из сгенерированного манифеста. */
type Endpoints = typeof endpoints;

/** Путь эндпоинта без префикса `/api/`, например `"clients/list"`. */
export type EndpointPath = keyof Endpoints;

/** Данные запроса эндпоинта [P]: JSON-тело, query-параметры, `FormData` или `undefined`. */
export type RequestOf<P extends EndpointPath> = z.output<Endpoints[P]["request"]>;

/** Данные ответа эндпоинта [P]: декодированный JSON, `Blob` или `undefined`. */
export type ResponseOf<P extends EndpointPath> = z.output<Endpoints[P]["response"]>;

/** Имена параметров пути: `"sessions/{id}/cancel"` → `"id"`. */
type PathParamName<P extends string> = P extends `${string}{${infer Name}}${infer Rest}`
  ? Name | PathParamName<Rest>
  : never;

/** Аргументы вызова эндпоинта [P]: запрос (если он есть) и параметры пути (если они есть). */
export type CallArgs<P extends EndpointPath> = [
  ...(RequestOf<P> extends undefined ? [] : [request: RequestOf<P>]),
  ...([PathParamName<P>] extends [never]
    ? []
    : [params: Readonly<Record<PathParamName<P>, string>>]),
];

/** Ошибка вызова API. Все места обработки разбирают варианты исчерпывающим `switch`. */
export type ApiError =
  /** Сервер отклонил запрос (4xx): бизнес-ошибка, в том числе отказ в правах. */
  | {
      readonly kind: "business";
      readonly status: number;
      readonly code: string;
      readonly message: string;
    }
  /** Нет действующей сессии: 401 и обновить её не удалось. */
  | { readonly kind: "unauthenticated" }
  /** Сеть недоступна или сервер ответил 5xx. */
  | { readonly kind: "unavailable" }
  /** Ответ не совпал с контрактом; [issues] — пути к несовпавшим полям. */
  | { readonly kind: "contract"; readonly endpoint: string; readonly issues: readonly string[] };

/** Результат вызова API: значение или типизированная ошибка, без исключений. */
export type ApiResult<T> =
  { readonly ok: true; readonly value: T } | { readonly ok: false; readonly error: ApiError };

/** Зависимости клиента API. */
export interface ApiClientOptions {
  /** Реализация `fetch`; в тестах подменяется. */
  readonly fetch: (input: string, init: RequestInit) => Promise<Response>;
  /** Язык для заголовка `Accept-Language`. */
  readonly language: () => string;
  /** Вызывается, когда сессию не удалось обновить. */
  readonly onSessionExpired: () => void;
  /** Куда писать нарушения контракта. */
  readonly reportContractViolation: (endpoint: string, issues: readonly string[]) => void;
}

/** Клиент API поверх сгенерированного манифеста эндпоинтов. */
export interface ApiClient {
  /** Вызывает эндпоинт [path] с аргументами [args] и декодирует ответ его схемой. */
  call<P extends EndpointPath>(path: P, ...args: CallArgs<P>): Promise<ApiResult<ResponseOf<P>>>;
}

/** Эндпоинты, на которых 401 означает неверные данные, а не истёкшую сессию. */
const WITHOUT_REFRESH: ReadonlySet<EndpointPath> = new Set<EndpointPath>([
  "auth/login",
  "auth/branches",
  "auth/sign-up",
  "auth/refresh-token",
]);

/** HTTP-ответ или признак недоступности сети. */
type Sent = { readonly ok: true; readonly response: Response } | { readonly ok: false };

/**
 * Создаёт клиент API с зависимостями [options].
 *
 * - сессия живёт в HttpOnly-cookie, `credentials: "same-origin"`;
 * - на 401 сессия обновляется один раз для всех параллельных запросов, исходный запрос повторяется один раз;
 * - ответ декодируется схемой эндпоинта: лишние поля отбрасываются, несовпадение — ошибка `contract`.
 */
export function createApiClient(options: ApiClientOptions): ApiClient {
  let refreshing: Promise<boolean> | null = null;

  const refreshSession = (): Promise<boolean> => {
    refreshing ??= send("auth/refresh-token", undefined, undefined).then((sent) => {
      refreshing = null;
      return sent.ok && sent.response.ok;
    });
    return refreshing;
  };

  async function send(path: EndpointPath, request: unknown, params: unknown): Promise<Sent> {
    const definition = endpoints[path];
    const headers = new Headers({
      Accept: "application/json",
      "Accept-Language": options.language(),
    });
    const init: RequestInit = { method: definition.method, credentials: "same-origin", headers };
    let url = `/api/${substituteParams(path, params)}`;
    switch (definition.in) {
      case "none":
        break;
      case "body":
        headers.set("Content-Type", "application/json");
        init.body = JSON.stringify(request);
        break;
      case "query":
        url += queryString(request);
        break;
      case "multipart":
        if (request instanceof FormData) {
          init.body = request;
        }
        break;
    }
    try {
      return { ok: true, response: await options.fetch(url, init) };
    } catch {
      return { ok: false };
    }
  }

  /**
   * Публичная сигнатура связывает путь с типами запроса и ответа; реализация работает с `unknown`.
   * Ответ декодируется схемой из той же записи `endpoints[path]`, по которой выведен [ResponseOf],
   * поэтому тип результата верен по построению. Напрямую вывести его из generic-пути через
   * `z.safeParse` TypeScript не может: схемы раскладываются в объединение.
   */
  function call<P extends EndpointPath>(
    path: P,
    ...args: CallArgs<P>
  ): Promise<ApiResult<ResponseOf<P>>>;
  async function call(
    path: EndpointPath,
    ...args: readonly unknown[]
  ): Promise<ApiResult<unknown>> {
    const hasRequest = endpoints[path].in !== "none";
    const request: unknown = hasRequest ? args[0] : undefined;
    const params: unknown = hasRequest ? args[1] : args[0];

    let sent = await send(path, request, params);
    if (sent.ok && sent.response.status === 401 && !WITHOUT_REFRESH.has(path)) {
      if (!(await refreshSession())) {
        options.onSessionExpired();
        return failure({ kind: "unauthenticated" });
      }
      sent = await send(path, request, params);
    }
    if (!sent.ok) {
      return failure({ kind: "unavailable" });
    }
    const { response } = sent;
    if (response.ok) {
      const decoded = z.safeParse(
        endpoints[path].response,
        await body(response, endpoints[path].out),
      );
      if (!decoded.success) {
        const issues = decoded.error.issues.map(
          (issue) => `${issue.path.join(".")}: ${issue.message}`,
        );
        options.reportContractViolation(path, issues);
        return failure({ kind: "contract", endpoint: path, issues });
      }
      return { ok: true, value: decoded.data };
    }
    if (response.status === 401) {
      return failure({ kind: "unauthenticated" });
    }
    if (response.status >= 400 && response.status < 500) {
      const payload = ErrorResponseSchema.safeParse(await body(response, "json"));
      return failure({
        kind: "business",
        status: response.status,
        code: payload.success ? payload.data.code : "",
        message: payload.success ? payload.data.message : "",
      });
    }
    return failure({ kind: "unavailable" });
  }

  return { call };
}

/** Неуспешный результат с ошибкой [error]. */
function failure(error: ApiError): { readonly ok: false; readonly error: ApiError } {
  return { ok: false, error };
}

/** Тело ответа [response] в формате [out]; неразбираемый JSON — `undefined`. */
async function body(response: Response, out: "json" | "empty" | "file"): Promise<unknown> {
  switch (out) {
    case "empty":
      return undefined;
    case "file":
      return response.blob();
    case "json":
      try {
        const parsed: unknown = await response.json();
        return parsed;
      } catch {
        return undefined;
      }
  }
}

/** Путь [path] с подставленными значениями [params] вместо `{имя}`. */
function substituteParams(path: string, params: unknown): string {
  return path.replace(/\{([^}]+)\}/g, (placeholder, name: string) => {
    const value: unknown = isRecord(params) ? params[name] : undefined;
    return typeof value === "string" ? encodeURIComponent(value) : placeholder;
  });
}

/** Query-строка из плоского объекта [request]; `null` и `undefined` пропускаются. */
function queryString(request: unknown): string {
  if (!isRecord(request)) {
    return "";
  }
  const query = new URLSearchParams();
  Object.entries(request).forEach(([key, value]) => {
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      query.set(key, String(value));
    }
  });
  const text = query.toString();
  return text === "" ? "" : `?${text}`;
}

/** Истина, если [value] — объект со строковыми ключами. */
function isRecord(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === "object" && value !== null;
}
