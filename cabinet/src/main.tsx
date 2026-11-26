import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { I18nProvider } from "./i18n/I18nProvider";
import { resolveLocale, storedLocale } from "./i18n/locale";
import "./styles.css";

const locale = resolveLocale(storedLocale(), navigator.language);
document.documentElement.lang = locale;

const root = document.getElementById("root");
if (root === null) {
  throw new Error("В index.html нет элемента #root");
}

/** Запросы кабинета — `fetch` браузера. */
const browserFetch = (input: string, init: RequestInit) => window.fetch(input, init);

createRoot(root).render(
  <StrictMode>
    <I18nProvider initialLocale={locale}>
      <App fetch={browserFetch} />
    </I18nProvider>
  </StrictMode>,
);
