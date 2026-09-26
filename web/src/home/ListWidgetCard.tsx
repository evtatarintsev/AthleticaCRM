import type { ReactNode } from "react";
import type { ClientListItem } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n, type PlainMessageKey } from "@/i18n/context";

/**
 * Каркас виджета-списка клиентов главной (общий для «Должники» и «Дни рождения»):
 * заголовок, состояния загрузки/ошибки/пустого списка, список строк и футер
 * «Показать всех», если он передан.
 */
export function ListWidgetCard({
  title,
  isPending,
  isError,
  items,
  emptyKey,
  errorKey,
  renderItem,
  footer,
}: {
  readonly title: string;
  readonly isPending: boolean;
  readonly isError: boolean;
  readonly items: readonly ClientListItem[] | undefined;
  readonly emptyKey: PlainMessageKey;
  readonly errorKey: PlainMessageKey;
  readonly renderItem: (client: ClientListItem) => ReactNode;
  readonly footer: ReactNode | null;
}) {
  const { t } = useI18n();
  return (
    <div className="flex-1 rounded-lg border bg-card lg:min-w-0">
      <div className="border-b px-4 py-3">
        <h2 className="font-medium">{title}</h2>
      </div>
      <div className="p-2">
        {isPending && (
          <div className="space-y-2 p-2">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        )}
        {!isPending && isError && <p className="p-4 text-sm text-destructive">{t(errorKey)}</p>}
        {!isPending && !isError && items?.length === 0 && (
          <p className="p-4 text-sm text-muted-foreground">{t(emptyKey)}</p>
        )}
        {!isPending && !isError && items !== undefined && items.length > 0 && (
          <ul className="divide-y">
            {items.map((client) => (
              <li key={client.id} className="py-1.5">
                {renderItem(client)}
              </li>
            ))}
          </ul>
        )}
      </div>
      {footer !== null && <div className="border-t p-1">{footer}</div>}
    </div>
  );
}
