import { useState } from "react";
import type { DisciplineId, EmployeeId, HallId } from "@/api/generated/contracts";
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
import type { SelectableItem } from "@/ui/MultiSelectPicker";

/** Раздел панели фильтров расписания. */
export type ScheduleFilterSection = "disciplines" | "halls" | "employees";

/** Фильтры, которые выбираются в панели; группа задаётся только переходом из карточки группы. */
export interface ScheduleFilterDraft {
  readonly disciplineIds: readonly DisciplineId[];
  readonly hallIds: readonly HallId[];
  readonly employeeIds: readonly EmployeeId[];
}

const SECTIONS: readonly ScheduleFilterSection[] = ["disciplines", "halls", "employees"];

const EMPTY: ScheduleFilterDraft = { disciplineIds: [], hallIds: [], employeeIds: [] };

/**
 * Боковая панель фильтров расписания с разделами «Дисциплины», «Залы», «Тренеры»; раздел
 * [section] открывается первым, `null` — панель закрыта. Черновик [selected] применяется
 * кнопкой «Применить» ([onApply]), «Очистить» снимает отметки во всех разделах.
 */
export function ScheduleFilterSheet({
  section,
  disciplines,
  halls,
  employees,
  selected,
  onClose,
  onApply,
}: {
  readonly section: ScheduleFilterSection | null;
  readonly disciplines: readonly SelectableItem<DisciplineId>[];
  readonly halls: readonly SelectableItem<HallId>[];
  readonly employees: readonly SelectableItem<EmployeeId>[];
  readonly selected: ScheduleFilterDraft;
  readonly onClose: () => void;
  readonly onApply: (filters: ScheduleFilterDraft) => void;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState(selected);
  const [openedAt, setOpenedAt] = useState(section);
  if (section !== openedAt) {
    setOpenedAt(section);
    if (section !== null) {
      setDraft(selected);
    }
  }
  const order = section === null ? SECTIONS : [section, ...SECTIONS.filter((s) => s !== section)];

  /** Чекбоксы раздела: [items], отмеченные [ids], смена отметки — [onChange]. */
  const options = <Id extends string>(
    items: readonly SelectableItem<Id>[],
    ids: readonly Id[],
    onChange: (ids: readonly Id[]) => void,
  ) =>
    items.length === 0 ? (
      <p className="px-2 py-2 text-sm text-muted-foreground">{t("groups.picker.empty")}</p>
    ) : (
      <ul className="space-y-1">
        {items.map((item) => (
          <li key={item.id}>
            <label className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-2 text-sm hover:bg-accent">
              <input
                type="checkbox"
                checked={ids.includes(item.id)}
                onChange={() => {
                  onChange(
                    ids.includes(item.id) ? ids.filter((id) => id !== item.id) : [...ids, item.id],
                  );
                }}
                className="size-4 shrink-0 accent-primary"
              />
              <span className="min-w-0 truncate">{item.name}</span>
            </label>
          </li>
        ))}
      </ul>
    );

  const body = (s: ScheduleFilterSection) => {
    switch (s) {
      case "disciplines":
        return options(disciplines, draft.disciplineIds, (disciplineIds) => {
          setDraft({ ...draft, disciplineIds });
        });
      case "halls":
        return options(halls, draft.hallIds, (hallIds) => {
          setDraft({ ...draft, hallIds });
        });
      case "employees":
        return options(employees, draft.employeeIds, (employeeIds) => {
          setDraft({ ...draft, employeeIds });
        });
    }
  };

  return (
    <Sheet
      open={section !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <SheetContent side="right" className="overflow-y-auto" closeLabel={t("action.close")}>
        <SheetHeader>
          <SheetTitle>{t("schedule.filters")}</SheetTitle>
          <SheetDescription className="sr-only">{t("schedule.filters")}</SheetDescription>
        </SheetHeader>
        <div className="space-y-4 px-4">
          {order.map((s) => (
            <section key={s} aria-label={t(`schedule.filter.${s}`)}>
              <h3 className="px-2 pb-1 text-xs font-semibold text-muted-foreground uppercase">
                {t(`schedule.filter.${s}`)}
              </h3>
              {body(s)}
            </section>
          ))}
        </div>
        <SheetFooter className="flex-row">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => {
              setDraft(EMPTY);
            }}
          >
            {t("schedule.filter.clear")}
          </Button>
          <Button
            className="flex-1"
            onClick={() => {
              onApply(draft);
              onClose();
            }}
          >
            {t("schedule.filter.apply")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
