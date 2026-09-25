import type { ReactNode } from "react";
import { t } from "../i18n";

/** Каркас экранов авторизации: логотип, заголовок [title], подзаголовок [subtitle] и карточка. */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2">
          <span className="grid size-9 place-items-center rounded-xl bg-brand-600 text-lg font-bold text-white">
            A
          </span>
          <span className="text-lg font-semibold tracking-tight">{t.appName}</span>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8 dark:border-slate-800 dark:bg-slate-900">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>
          <div className="mt-6">{children}</div>
        </div>
        {footer && (
          <p className="mt-6 text-center text-sm text-slate-600 dark:text-slate-400">{footer}</p>
        )}
      </div>
    </main>
  );
}
