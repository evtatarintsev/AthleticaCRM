import { z } from "zod";
import { endpoints, ErrorResponseSchema } from "./generated/contracts";

/** Описание эндпоинта в сгенерированном манифесте. */
export interface EndpointDefinition {
  /** HTTP-метод. */
  readonly method: string;
  /** Как передаётся запрос. */
  readonly in: "none" | "body" | "query" | "multipart";
  /** Схема запроса. */
  readonly request: z.ZodType;
  /** Формат ответа. */
  readonly out: "json" | "empty" | "file";
  /** Схема ответа. */
  readonly response: z.ZodType;
}

/** Манифест эндпоинтов: путь без префикса `/api/` → описание. */
export type Manifest = Readonly<Record<string, EndpointDefinition>>;

/** Данные запроса эндпоинта [P] манифеста [M]. */
export type RequestOf<M extends Manifest, P extends keyof M> = z.output<M[P]["request"]>;

/** Данные ответа эндпоинта [P] манифеста [M]. */
export type ResponseOf<M extends Manifest, P extends keyof M> = z.output<M[P]["response"]>;

/** Аргументы вызова эндпоинта [P]: запрос, если он есть. Параметров пути у клиентского API нет. */
export type CallArgs<M extends Manifest, P extends keyof M> =
  RequestOf<M, P> extends undefined ? [] : [request: RequestOf<M, P>];

/** Ошибка вызова API. Все места обработки разбирают варианты исчерпывающим `switch`. */
export type ApiError =
  /** Сервер отклонил запрос (4xx): бизнес-ошибка. */
  | {
      readonly kind: "business";
      readonly status: number;
      readonly code: string;
      readonly message: string;
    }
  /** Нет действующего сеанса: 401 и обновить его не удалось. */
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
  /** Вызывается, когда сеанс не удалось обновить: кабинет возвращается к вводу номера. */
  readonly onSessionExpired: () => void;
  /** Куда писать нарушения контракта. */
  readonly reportContractViolation: (endpoint: string, issues: readonly string[]) => void;
}

/** Клиент API поверх манифеста [M]. */
export interface ApiClient<M extends Manifest> {
  /** Вызывает эндпоинт [path] с аргументами [args] и декодирует ответ его схемой. */
  call<P extends keyof M & string>(
    path: P,
    ...args: CallArgs<M, P>
  ): Promise<ApiResult<ResponseOf<M, P>>>;
}

/** Эндпоинт обновления сеанса: refresh-cookie кабинета уходит только на этот путь. */
const REFRESH_PATH = "client-app/auth/refresh";

/** Эндпоинты входа: на них 401 означает неверные данные, а не истёкший сеанс. */
const WITHOUT_REFRESH: ReadonlySet<string> = new Set([
  "client-app/auth/request-code",
  "client-app/auth/login",
  REFRESH_PATH,
]);

/** HTTP-ответ или признак недоступности сети. */
type Sent = { readonly ok: true; readonly response: Response } | { readonly ok: false };

/**
 * Итог обновления сеанса: `rejected` — сервер отверг refresh-токен, сеанса больше нет;
 * `unavailable` — сеть или сервер недоступны, сеанс может быть жив.
 */
type Refresh = "refreshed" | "rejected" | "unavailable";

/**
 * Итог обновления сеанса по ответу [sent]; сбой сети, 5xx и 429 (лимит запросов) не завершают
 * сеанс.
 */
function refreshOutcome(sent: Sent): Refresh {
  if (!sent.ok || sent.response.status >= 500 || sent.response.status === 429) {
    return "unavailable";
  }
  return sent.response.ok ? "refreshed" : "rejected";
}

/**
 * Создаёт клиент API кабинета над манифестом [manifest] с зависимостями [options].
 *
 * - сеанс живёт в HttpOnly-cookie хоста кабинета, `credentials: "same-origin"`;
 * - на 401 сеанс обновляется один раз для всех параллельных запросов, исходный запрос повторяется один раз;
 * - завершает сеанс только отказ сервера в обновлении; сбой сети — ошибка `unavailable`;
 * - ответ декодируется схемой эндпоинта: лишние поля отбрасываются, несовпадение — ошибка `contract`.
 */
export function createApiClient<M extends Manifest>(
  manifest: M,
  options: ApiClientOptions,
): ApiClient<M> {
  let refreshing: Promise<Refresh> | null = null;

  const refreshSession = (): Promise<Refresh> => {
    refreshing ??= send(`/api/${REFRESH_PATH}`, { method: "POST" }).then((sent) => {
      refreshing = null;
      return refreshOutcome(sent);
    });
    return refreshing;
  };

  async function send(url: string, init: RequestInit): Promise<Sent> {
    const headers = new Headers({
      Accept: "application/json",
      "Accept-Language": options.language(),
    });
    new Headers(init.headers).forEach((value, key) => {
      headers.set(key, value);
    });
    try {
      return {
        ok: true,
        response: await options.fetch(url, { ...init, headers, credentials: "same-origin" }),
      };
    } catch {
      return { ok: false };
    }
  }

  /** Запрос эндпоинта [definition] по пути [path] с данными [request]. */
  function sendEndpoint(
    path: string,
    definition: EndpointDefinition,
    request: unknown,
  ): Promise<Sent> {
    const url = `/api/${path}`;
    switch (definition.in) {
      case "none":
        return send(url, { method: definition.method });
      case "body":
        return send(url, {
          method: definition.method,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(request),
        });
      case "query":
        return send(url + queryString(request), { method: definition.method });
      case "multipart":
        return send(
          url,
          request instanceof FormData
            ? { method: definition.method, body: request }
            : { method: definition.method },
        );
    }
  }

  /**
   * Публичная сигнатура связывает путь с типами запроса и ответа; реализация работает с `unknown`.
   * Ответ декодируется схемой из той же записи манифеста, по которой выведен [ResponseOf],
   * поэтому тип результата верен по построению.
   */
  function call<P extends keyof M & string>(
    path: P,
    ...args: CallArgs<M, P>
  ): Promise<ApiResult<ResponseOf<M, P>>>;
  async function call(path: string, ...args: readonly unknown[]): Promise<ApiResult<unknown>> {
    const definition = manifest[path];
    if (definition === undefined) {
      return failure({ kind: "contract", endpoint: path, issues: ["неизвестный эндпоинт"] });
    }
    const request: unknown = args[0];

    let sent = await sendEndpoint(path, definition, request);
    if (sent.ok && sent.response.status === 401 && !WITHOUT_REFRESH.has(path)) {
      switch (await refreshSession()) {
        case "refreshed":
          break;
        case "rejected":
          options.onSessionExpired();
          return failure({ kind: "unauthenticated" });
        case "unavailable":
          return failure({ kind: "unavailable" });
      }
      sent = await sendEndpoint(path, definition, request);
    }
    if (!sent.ok) {
      return failure({ kind: "unavailable" });
    }
    const { response } = sent;
    if (response.ok) {
      const decoded = z.safeParse(definition.response, await body(response, definition.out));
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
      if (!WITHOUT_REFRESH.has(path)) {
        options.onSessionExpired();
      }
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

/** Клиент клиентского API кабинета на сгенерированном манифесте. */
export function createCabinetApi(options: ApiClientOptions): ApiClient<typeof endpoints> {
  return createApiClient(endpoints, options);
}

/** Неуспешный результат с ошибкой [error]. */
function failure(error: ApiError): { readonly ok: false; readonly error: ApiError } {
  return { ok: false, error };
}

/** Тело ответа [response] в формате [out]; неразбираемый JSON — `undefined`. */
async function body(response: Response, out: EndpointDefinition["out"]): Promise<unknown> {
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

/** Query-строка из плоского объекта [request]; `null` и `undefined` пропускаются. */
function queryString(request: unknown): string {
  if (typeof request !== "object" || request === null) {
    return "";
  }
  const query = new URLSearchParams();
  Object.entries(request).forEach(([key, value]: [string, unknown]) => {
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      query.set(key, String(value));
    }
  });
  const text = query.toString();
  return text === "" ? "" : `?${text}`;
}
