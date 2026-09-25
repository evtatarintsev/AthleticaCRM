import type { ReactNode } from "react";
import { useI18n } from "@/i18n/context";
import { LanguageSwitcher } from "./LanguageSwitcher";

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
  const { t } = useI18n();
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2">
          <Logo />
          <span className="text-lg font-semibold tracking-tight">{t("app.name")}</span>
        </div>
        <div className="rounded-2xl border bg-card p-6 text-card-foreground shadow-sm sm:p-8">
          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
          <div className="mt-6">{children}</div>
        </div>
        {footer !== undefined && (
          <p className="mt-6 text-center text-sm text-muted-foreground">{footer}</p>
        )}
        <div className="mt-6 flex justify-center">
          <LanguageSwitcher />
        </div>
      </div>
    </main>
  );
}

/** Знак приложения. */
export function Logo() {
  return (
    <span
      aria-hidden
      className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary text-lg font-bold text-primary-foreground"
    >
      A
    </span>
  );
}
