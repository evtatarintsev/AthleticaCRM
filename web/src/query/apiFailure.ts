import type { ApiError, ApiResult } from "@/api/client";

/**
 * Ошибка API в виде исключения. TanStack Query переводит запрос в состояние ошибки
 * только по `throw`, поэтому `ApiResult` превращается в исключение ровно здесь.
 */
export class ApiFailure extends Error {
  /** Типизированная ошибка вызова API. */
  readonly error: ApiError;

  /** Исключение для ошибки [error]. */
  constructor(error: ApiError) {
    super(`API: ${error.kind}`);
    this.name = "ApiFailure";
    this.error = error;
  }
}

/** Значение успешного [result]; ошибка выбрасывается как [ApiFailure]. */
export function unwrap<T>(result: ApiResult<T>): T {
  if (!result.ok) {
    throw new ApiFailure(result.error);
  }
  return result.value;
}

/**
 * Ошибка API из ошибки запроса [error]. Всё, что пришло не из API
 * (ошибка в коде загрузки), для интерфейса выглядит как недоступность сервиса.
 */
export function apiErrorOf(error: Error): ApiError {
  return error instanceof ApiFailure ? error.error : { kind: "unavailable" };
}
