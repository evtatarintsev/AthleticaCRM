import { useMemo, useState, type ReactNode } from "react";
import { I18nContext, type I18n } from "./context";
import { en } from "./en";
import { storeLocale, type Locale } from "./locale";
import { createTranslator, type Messages } from "./messages";
import { ru } from "./ru";

/** Словари по языкам; `Record` не даёт забыть словарь нового языка. */
const dictionaries: Readonly<Record<Locale, Messages<typeof ru>>> = { ru, en };

/**
 * Локализация для дерева [children], начиная с языка [initialLocale].
 * Смена языка сохраняется в браузере и меняет `lang` документа (из него API-клиент берёт
 * `Accept-Language`).
 */
export function I18nProvider({
  initialLocale,
  children,
}: {
  initialLocale: Locale;
  children: ReactNode;
}) {
  const [locale, setLocaleState] = useState(initialLocale);
  const i18n = useMemo<I18n>(
    () => ({
      locale,
      t: createTranslator<typeof ru>(locale, dictionaries[locale]),
      setLocale: (next) => {
        storeLocale(next);
        document.documentElement.lang = next;
        setLocaleState(next);
      },
    }),
    [locale],
  );
  return <I18nContext value={i18n}>{children}</I18nContext>;
}
