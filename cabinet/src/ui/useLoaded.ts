import { useEffect, useState } from "react";
import type { ApiResult } from "../api/client";
import { errorText } from "../api/errorText";
import { useI18n } from "../i18n/context";

/** Состояние загрузки данных: ждём, ошибка с текстом или данные. */
export type Loaded<T> =
  | { readonly kind: "loading" }
  | { readonly kind: "failed"; readonly message: string }
  | { readonly kind: "ready"; readonly value: T };

/**
 * Данные вызова [load], перезапрашиваемые при смене [load] (передаётся через `useCallback`);
 * ответ устаревшего запроса отбрасывается. [reload] запрашивает данные снова.
 */
export function useLoaded<T>(load: () => Promise<ApiResult<T>>) {
  const { t } = useI18n();
  const [state, setState] = useState<Loaded<T>>({ kind: "loading" });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    void load().then((result) => {
      if (active) {
        setState(
          result.ok
            ? { kind: "ready", value: result.value }
            : { kind: "failed", message: errorText(result.error, t) },
        );
      }
    });
    return () => {
      active = false;
    };
  }, [load, attempt, t]);
  return {
    state,
    reload: () => {
      setAttempt((it) => it + 1);
    },
  };
}
