import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import type { ChecklistItem } from "@/ui/ChecklistSheet";

/**
 * Секция дисциплин или тренеров группы: заголовок [title] с действием «Изменить» [onEdit] и
 * сводка выбора [children].
 */
export function GroupChipsSection({
  title,
  onEdit,
  children,
}: {
  readonly title: string;
  readonly onEdit: () => void;
  readonly children: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-muted-foreground">{title}</h2>
        <Button type="button" variant="ghost" size="sm" onClick={onEdit}>
          {t("groups.detail.editSection")}
        </Button>
      </div>
      {children}
    </section>
  );
}

/** Чипы набора [items] только для чтения; при пустом наборе — сообщение [emptyText]. */
export function GroupChips<Id extends string>({
  items,
  emptyText,
}: {
  readonly items: readonly ChecklistItem<Id>[];
  readonly emptyText: string;
}) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyText}</p>;
  }
  return (
    <ul className="flex flex-wrap gap-2">
      {items.map((item) => (
        <li
          key={item.id}
          className={cn(
            "inline-flex items-center gap-2 rounded-full border bg-accent py-1 pr-3 text-xs font-medium",
            item.avatar === undefined ? "pl-3" : "pl-1",
          )}
        >
          {item.avatar}
          {item.name}
        </li>
      ))}
    </ul>
  );
}
