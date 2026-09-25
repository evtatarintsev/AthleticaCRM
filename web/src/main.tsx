import { QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createApiClient } from "./api/client";
import { createAppRouting } from "./app/router";
import { Toaster } from "./components/ui/sonner";
import { I18nProvider } from "./i18n/I18nProvider";
import { resolveLocale, storedLocale } from "./i18n/locale";
import { createQueryClient } from "./query/queries";
import "./index.css";

const locale = resolveLocale(storedLocale(), navigator.language);
document.documentElement.lang = locale;

const queryClient = createQueryClient();

const api = createApiClient({
  fetch: (input, init) => fetch(input, init),
  language: () => document.documentElement.lang,
  onSessionExpired: () => {
    queryClient.clear();
    routing.redirectToLogin();
  },
  reportContractViolation: (endpoint, issues) => {
    console.error(`Ответ /api/${endpoint} не совпадает с контрактом`, issues);
  },
});

const routing = createAppRouting({ api, queryClient });

const root = document.getElementById("root");
if (root === null) {
  throw new Error("В index.html нет элемента #root");
}

createRoot(root).render(
  <StrictMode>
    <I18nProvider
      initialLocale={locale}
      onLocaleChange={() => {
        void queryClient.invalidateQueries();
      }}
    >
      <QueryClientProvider client={queryClient}>
        {routing.element}
        <Toaster />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
);
