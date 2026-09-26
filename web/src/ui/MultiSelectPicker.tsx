import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useI18n } from "@/i18n/context";

/** Запись, которую можно выбрать в [MultiSelectPicker]: идентификатор и подпись. */
export interface SelectableItem<Id extends string> {
  readonly id: Id;
  readonly name: string;
}

/**
 * Множественный выбор записей [items] панелью с чекбоксами: используется для дисциплин
 * и тренеров группы, а также фильтров по дисциплинам, залам и тренерам расписания.
 * Черновик выбора применяется кнопкой «Готово», закрытие без неё ничего не меняет.
 */
export function MultiSelectPicker<Id extends string>({
  open,
  title,
  items,
  selected,
  emptyText,
  onOpenChange,
  onApply,
}: {
  readonly open: boolean;
  readonly title: string;
  readonly items: readonly SelectableItem<Id>[];
  readonly selected: readonly Id[];
  readonly emptyText: string;
  readonly onOpenChange: (open: boolean) => void;
  readonly onApply: (selected: readonly Id[]) => void;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<ReadonlySet<Id>>(new Set(selected));
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setDraft(new Set(selected));
    }
  }

  const toggle = (id: Id) => {
    const next = new Set(draft);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setDraft(next);
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[85vh] overflow-y-auto"
        closeLabel={t("action.close")}
      >
        <SheetHeader>
          <SheetTitle>{title}</SheetTitle>
          <SheetDescription className="sr-only">{title}</SheetDescription>
        </SheetHeader>
        <div className="px-4">
          {items.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">{emptyText}</p>
          ) : (
            <ul className="space-y-1">
              {items.map((item) => (
                <li key={item.id}>
                  <label className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-2 text-sm hover:bg-accent">
                    <input
                      type="checkbox"
                      checked={draft.has(item.id)}
                      onChange={() => {
                        toggle(item.id);
                      }}
                      className="size-4 shrink-0 accent-primary"
                    />
                    <span className="min-w-0 truncate">{item.name}</span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </div>
        <SheetFooter className="flex-row">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => {
              setDraft(new Set());
            }}
          >
            {t("groups.filter.reset")}
          </Button>
          <Button
            className="flex-1"
            onClick={() => {
              onApply(Array.from(draft));
              onOpenChange(false);
            }}
          >
            {t("action.done")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
