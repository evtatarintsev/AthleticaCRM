import { z } from "zod";

/** Поддерживаемые языки интерфейса; первый — язык по умолчанию. */
export const LocaleSchema = z.enum(["ru", "en"]);

/** Язык интерфейса. */
export type Locale = z.output<typeof LocaleSchema>;

/** Ключ `localStorage`, под которым хранится язык, выбранный вручную. */
const STORAGE_KEY = "athletica.locale";

/**
 * Язык интерфейса: выбранный вручную [stored], иначе язык браузера [browserLanguage]
 * (`en-US` → `en`), иначе русский.
 */
export function resolveLocale(stored: string | null, browserLanguage: string): Locale {
  const manual = LocaleSchema.safeParse(stored);
  if (manual.success) {
    return manual.data;
  }
  const primary = LocaleSchema.safeParse(browserLanguage.toLowerCase().split("-")[0]);
  return primary.success ? primary.data : "ru";
}

/** Язык, выбранный вручную, или `null`; хранилище может быть недоступно. */
export function storedLocale(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/** Запоминает язык [locale], выбранный вручную; недоступное хранилище не мешает смене языка. */
export function storeLocale(locale: Locale): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    return;
  }
}
