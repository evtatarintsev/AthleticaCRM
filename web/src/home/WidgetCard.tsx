import { Cake, Check, Clock, NotebookText, Wallet } from "lucide-react";
import type { ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type { DashboardSettings, DashboardWidget } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { WidgetSettingsButton } from "./WidgetSettingsButton";

function widgetTone(type: DashboardWidget["type"]) {
  switch (type) {
    case "sessions":
      return { Icon: Clock, classes: "bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-300" };
    case "debtors":
      return { Icon: Wallet, classes: "bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300" };
    case "birthdays":
      return {
        Icon: Cake,
        classes: "bg-pink-100 text-pink-700 dark:bg-pink-950 dark:text-pink-300",
      };
    case "unclosedSessions":
      return {
        Icon: NotebookText,
        classes: "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
      };
  }
}

/** Общий каркас четырёх виджетов главной. */
export function WidgetCard({
  api,
  settings,
  widget,
  title,
  count,
  state,
  emptyKey,
  errorKey,
  children,
  footer,
}: {
  readonly api: ApiClient;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidget;
  readonly title: string;
  readonly count: number;
  readonly state: "pending" | "error" | "empty" | "ready";
  readonly emptyKey: PlainMessageKey;
  readonly errorKey: PlainMessageKey;
  readonly children?: ReactNode;
  readonly footer?: ReactNode;
}) {
  const { t } = useI18n();
  const { Icon, classes } = widgetTone(widget.type);
  return (
    <div className="min-w-0 rounded-2xl border bg-card shadow-sm">
      <div className="flex items-center gap-2.5 px-3.5 py-3">
        <span className={cn("grid size-8 shrink-0 place-items-center rounded-xl", classes)}>
          <Icon className="size-4.5" aria-hidden />
        </span>
        <h2 className="min-w-0 truncate text-sm font-semibold">{title}</h2>
        {state === "ready" && count > 0 && (
          <span className="shrink-0 rounded-full bg-accent px-2 py-0.5 text-xs font-semibold text-muted-foreground tabular-nums">
            {count}
          </span>
        )}
        <div className="ml-auto shrink-0">
          <WidgetSettingsButton api={api} settings={settings} widget={widget} />
        </div>
      </div>
      {state === "pending" && (
        <div className="space-y-2 px-4 pb-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}
      {state === "error" && <p className="px-4 pb-4 text-sm text-destructive">{t(errorKey)}</p>}
      {state === "empty" && (
        <div className="flex items-center gap-2.5 px-4 pb-4 text-sm text-muted-foreground">
          <span className="grid size-7 shrink-0 place-items-center rounded-full bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300">
            <Check className="size-4" aria-hidden />
          </span>
          {t(emptyKey)}
        </div>
      )}
      {state === "ready" && (
        <>
          <div className="px-1.5 pb-1.5">{children}</div>
          {footer}
        </>
      )}
    </div>
  );
}
