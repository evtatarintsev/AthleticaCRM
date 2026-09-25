import type { ButtonHTMLAttributes, ReactNode } from "react";

/** Основная кнопка на всю ширину; [loading] блокирует её и показывает индикатор. */
export function PrimaryButton({
  loading,
  children,
  ...button
}: { loading?: boolean; children: ReactNode } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...button}
      disabled={loading || button.disabled}
      aria-busy={loading}
      className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-2.5 text-base font-medium text-white shadow-sm transition hover:bg-brand-700 focus-visible:ring-4 focus-visible:ring-brand-100 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-60 dark:focus-visible:ring-brand-500/30"
    >
      {loading && (
        <span className="size-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
      )}
      {children}
    </button>
  );
}

/** Сообщение об ошибке формы; озвучивается скринридерами. */
export function ErrorAlert({ message }: { message: string }) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-800 dark:border-red-900/60 dark:bg-red-950/50 dark:text-red-200"
    >
      {message}
    </div>
  );
}
