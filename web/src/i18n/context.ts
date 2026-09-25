import { createContext, useContext } from "react";
import type { Formatters } from "./format";
import type { Locale } from "./locale";
import type { Translate } from "./messages";
import type { ru } from "./ru";

/** Текущий язык интерфейса, перевод и форматирование. */
export interface I18n {
  /** Язык интерфейса. */
  readonly locale: Locale;
  /** Перевод по ключу словаря. */
  readonly t: Translate<typeof ru>;
  /** Форматирование чисел, денег и дат. */
  readonly format: Formatters;
  /** Переключает язык и запоминает выбор. */
  readonly setLocale: (locale: Locale) => void;
}

/** Контекст локализации; значение задаёт `I18nProvider`. */
export const I18nContext = createContext<I18n | null>(null);

/** Локализация текущего дерева компонентов. */
export function useI18n(): I18n {
  const i18n = useContext(I18nContext);
  if (i18n === null) {
    throw new Error("useI18n вызван вне I18nProvider");
  }
  return i18n;
}
