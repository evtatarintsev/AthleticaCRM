import { ChevronDownIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";

/**
 * Каркас блока ленты карточки клиента: иконка, заголовок, счётчик записей, действия справа
 * и сворачивание. Содержимое — [children]; [footer] — строка под содержимым (например «Все»).
 */
export function ClientCardBlock({
  icon,
  title,
  count,
  actions,
  footer,
  children,
}: {
  readonly icon: ReactNode;
  readonly title: string;
  /** Количество записей в блоке; `null` — счётчик не показывается. */
  readonly count: number | null;
  readonly actions?: ReactNode;
  readonly footer?: ReactNode;
  readonly children: ReactNode;
}) {
  const [expanded, setExpanded] = useState(true);
  return (
    <section className="rounded-lg border bg-card">
      <div className="flex items-center gap-1 py-1.5 pr-2 pl-4">
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => {
            setExpanded(!expanded);
          }}
          className="flex min-w-0 flex-1 items-center gap-2 py-1 text-left"
        >
          <span className="text-muted-foreground [&_svg]:size-4">{icon}</span>
          <h2 className="truncate font-medium">{title}</h2>
          {count !== null && count > 0 && (
            <span className="rounded-full bg-secondary px-1.5 text-xs text-secondary-foreground tabular-nums">
              {count}
            </span>
          )}
          <ChevronDownIcon
            aria-hidden
            className={cn(
              "ml-auto size-4 text-muted-foreground transition-transform",
              !expanded && "-rotate-90",
            )}
          />
        </button>
        {actions}
      </div>
      {expanded && (
        <div className="border-t">
          {children}
          {footer !== undefined && footer !== null && (
            <div className="border-t px-2 py-1">{footer}</div>
          )}
        </div>
      )}
    </section>
  );
}

/** Состояния загрузки блока: скелетон, ошибка или пустой список с текстом [empty]. */
export function BlockStatus({
  isPending,
  isError,
  isEmpty,
  empty,
}: {
  readonly isPending: boolean;
  readonly isError: boolean;
  readonly isEmpty: boolean;
  readonly empty: string;
}) {
  const { t } = useI18n();
  if (isPending) {
    return (
      <div className="space-y-2 p-4">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
      </div>
    );
  }
  if (isError) {
    return (
      <div className="p-4">
        <FormAlert message={t("clients.detail.loadError")} />
      </div>
    );
  }
  if (isEmpty) {
    return <p className="px-4 py-3 text-sm text-muted-foreground">{empty}</p>;
  }
  return null;
}

/** Кнопка «Все (N)» / «Свернуть» для блока, где записей больше [limit]. */
export function ShowAllButton({
  total,
  limit,
  showAll,
  onToggle,
}: {
  readonly total: number;
  readonly limit: number;
  readonly showAll: boolean;
  readonly onToggle: () => void;
}) {
  const { t } = useI18n();
  if (total <= limit) {
    return null;
  }
  return (
    <Button variant="ghost" size="sm" onClick={onToggle}>
      {showAll ? t("clientCard.showLess") : t("clientCard.showAll", { count: total })}
    </Button>
  );
}

/** Строка блока: основная часть слева, [aside] справа. */
export function BlockRow({
  children,
  aside,
  className,
}: {
  readonly children: ReactNode;
  readonly aside?: ReactNode;
  readonly className?: string;
}) {
  return (
    <li className={cn("flex items-start gap-3 px-4 py-2 text-sm", className)}>
      <div className="min-w-0 flex-1">{children}</div>
      {aside !== undefined && aside !== null && <div className="shrink-0 text-right">{aside}</div>}
    </li>
  );
}

/** Вторая строка записи — мелким приглушённым шрифтом. */
export function BlockSub({ children }: { readonly children: ReactNode }) {
  return <div className="text-xs text-muted-foreground">{children}</div>;
}

/** Тон плашки. */
export type PillTone = "green" | "amber" | "red" | "gray" | "blue";

/** Метка-плашка тона [tone]. */
export function Pill({
  tone,
  children,
}: {
  readonly tone: PillTone;
  readonly children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium whitespace-nowrap [&_svg]:size-3",
        tone === "green" &&
          "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
        tone === "amber" && "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
        tone === "red" && "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
        tone === "gray" && "bg-secondary text-secondary-foreground",
        tone === "blue" && "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200",
      )}
    >
      {children}
    </span>
  );
}
