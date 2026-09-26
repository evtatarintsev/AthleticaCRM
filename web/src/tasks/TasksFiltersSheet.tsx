import { useState } from "react";
import { TaskStatusSchema, type TaskStatus } from "@/api/generated/contracts";
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
import { cn } from "@/lib/utils";
import { taskStatusLabelKey } from "./taskStatus";
import type { TaskListFilters } from "./taskListSearch";

/** Все статусы задач в порядке отображения (паритет с `TaskStatus.entries` KMP-клиента). */
const ALL_STATUSES: readonly TaskStatus[] = TaskStatusSchema.options;

/**
 * Панель фильтров списка задач (паритет с `TasksFilterContent` KMP-клиента):
 * переключатель «только мои» и множественный выбор статусов. Черновик применяется
 * кнопкой, сбрасывается кнопкой «Сбросить».
 */
export function TasksFiltersSheet({
  open,
  filters,
  onOpenChange,
  onApply,
}: {
  readonly open: boolean;
  readonly filters: TaskListFilters;
  readonly onOpenChange: (open: boolean) => void;
  readonly onApply: (filters: TaskListFilters) => void;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<TaskListFilters>(filters);
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setDraft(filters);
    }
  }

  const toggleStatus = (status: TaskStatus) => {
    setDraft((current) => ({
      ...current,
      statuses: current.statuses.includes(status)
        ? current.statuses.filter((s) => s !== status)
        : [...current.statuses, status],
    }));
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[85vh] overflow-y-auto"
        closeLabel={t("action.close")}
      >
        <SheetHeader>
          <SheetTitle>{t("tasks.filters")}</SheetTitle>
          <SheetDescription className="sr-only">{t("tasks.filters")}</SheetDescription>
        </SheetHeader>
        <div className="space-y-6 px-4">
          <fieldset>
            <legend className="mb-2 text-sm font-medium">
              {t("tasks.filter.sectionAssignee")}
            </legend>
            <label className="flex items-center justify-between gap-4 text-sm font-medium">
              {t("tasks.filter.onlyMine")}
              <input
                type="checkbox"
                role="switch"
                checked={draft.onlyMine}
                onChange={(event) => {
                  setDraft((current) => ({ ...current, onlyMine: event.target.checked }));
                }}
                className="size-4 accent-primary"
              />
            </label>
          </fieldset>
          <fieldset>
            <legend className="mb-2 text-sm font-medium">{t("tasks.filter.sectionStatus")}</legend>
            <div className="flex flex-wrap gap-2">
              {ALL_STATUSES.map((status) => {
                const active = draft.statuses.includes(status);
                return (
                  <button
                    key={status}
                    type="button"
                    aria-pressed={active}
                    onClick={() => {
                      toggleStatus(status);
                    }}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs font-medium outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
                      active
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-border bg-background hover:bg-accent",
                    )}
                  >
                    {t(taskStatusLabelKey(status))}
                  </button>
                );
              })}
            </div>
          </fieldset>
        </div>
        <SheetFooter className="flex-row">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => {
              setDraft((current) => ({ ...current, onlyMine: false, statuses: [] }));
            }}
          >
            {t("tasks.filter.reset")}
          </Button>
          <Button
            className="flex-1"
            onClick={() => {
              onApply(draft);
              onOpenChange(false);
            }}
          >
            {t("tasks.filter.apply")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
