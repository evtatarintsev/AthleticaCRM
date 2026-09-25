import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { render, type RenderResult } from "@testing-library/react";
import type { ReactNode } from "react";
import { I18nProvider } from "@/i18n/I18nProvider";
import type { Locale } from "@/i18n/locale";

/**
 * Рендерит [ui] с локализацией [locale] внутри минимального роутера в памяти:
 * страницам со ссылками роутера нужен его контекст. Роутер загружается асинхронно,
 * поэтому элементы ищутся через `findBy…`.
 */
export function renderPage(ui: ReactNode, locale: Locale = "ru"): RenderResult {
  const router = createRouter({
    routeTree: createRootRoute({ component: () => ui }),
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return render(
    <I18nProvider initialLocale={locale}>
      <RouterProvider router={router} />
    </I18nProvider>,
  );
}
