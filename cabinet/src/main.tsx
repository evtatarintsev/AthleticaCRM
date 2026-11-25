import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { I18nProvider } from "./i18n/I18nProvider";
import { resolveLocale, storedLocale } from "./i18n/locale";

const locale = resolveLocale(storedLocale(), navigator.language);
document.documentElement.lang = locale;

const root = document.getElementById("root");
if (root === null) {
  throw new Error("В index.html нет элемента #root");
}

createRoot(root).render(
  <StrictMode>
    <I18nProvider initialLocale={locale}>
      <App />
    </I18nProvider>
  </StrictMode>,
);
