import { useState } from "react";

/** Граница широкого экрана — Tailwind `md`, на ней списки переключаются между таблицей и списком. */
export const WIDE_SCREEN = "(min-width: 768px)";

/**
 * Совпадает ли медиа-запрос [query] в момент монтирования. Изменения ширины после открытия
 * не отслеживаются: поворот планшета не должен сам менять выбранную раскладку.
 */
export function useInitialMediaQuery(query: string): boolean {
  const [matches] = useState(() => window.matchMedia(query).matches);
  return matches;
}
