import { useMemo, useState, type ReactNode } from "react";
import { I18nContext, type I18n } from "./context";
import { en } from "./en";
import { createFormatters } from "./format";
import { storeLocale, type Locale } from "./locale";
import { createTranslator, type Messages } from "./messages";
import { ru } from "./ru";

/** Словари по языкам; `Record` не даёт забыть словарь нового языка. */
const dictionaries: Readonly<Record<Locale, Messages<typeof ru>>> = { ru, en };

/**
 * Локализация для дерева [children], начиная с языка [initialLocale].
 * Смена языка сохраняется в браузере, меняет `lang` документа (из него API-клиент берёт
 * `Accept-Language`) и вызывает [onLocaleChange], чтобы перезапросить данные с сервера.
 */
export function I18nProvider({
  initialLocale,
  onLocaleChange,
  children,
}: {
  initialLocale: Locale;
  onLocaleChange?: (locale: Locale) => void;
  children: ReactNode;
}) {
  const [locale, setLocaleState] = useState(initialLocale);
  const i18n = useMemo<I18n>(
    () => ({
      locale,
      t: createTranslator<typeof ru>(locale, dictionaries[locale]),
      format: createFormatters(locale),
      setLocale: (next) => {
        storeLocale(next);
        document.documentElement.lang = next;
        setLocaleState(next);
        onLocaleChange?.(next);
      },
    }),
    [locale, onLocaleChange],
  );
  return <I18nContext value={i18n}>{children}</I18nContext>;
}
