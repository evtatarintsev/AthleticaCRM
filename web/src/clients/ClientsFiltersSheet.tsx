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
import { cn } from "@/lib/utils";
import type { ClientListFilters } from "./clientListSearch";

/**
 * Панель фильтров списка клиентов (паритет с `ClientsFilterContent` KMP-клиента):
 * пол сегментированными кнопками, «без группы» и «задолженность» переключателями,
 * день рождения сегментированными кнопками. Черновик применяется кнопкой,
 * сбрасывается кнопкой «Сбросить».
 */
export function ClientsFiltersSheet({
  open,
  filters,
  onOpenChange,
  onApply,
}: {
  /** Панель открыта. */
  readonly open: boolean;
  /** Фильтры, от которых начинается черновик при открытии панели. */
  readonly filters: ClientListFilters;
  /** Смена состояния панели. */
  readonly onOpenChange: (open: boolean) => void;
  /** Применяет фильтры [filters]. */
  readonly onApply: (filters: ClientListFilters) => void;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<ClientListFilters>(filters);
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setDraft(filters);
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[85vh] overflow-y-auto"
        closeLabel={t("action.close")}
      >
        <SheetHeader>
          <SheetTitle>{t("clients.filters")}</SheetTitle>
          <SheetDescription className="sr-only">{t("clients.filters")}</SheetDescription>
        </SheetHeader>
        <div className="space-y-6 px-4">
          <SegmentedFilter
            label={t("clients.filter.gender")}
            options={[
              { value: "all", label: t("clients.filter.genderAll") },
              { value: "male", label: t("clients.filter.genderMale") },
              { value: "female", label: t("clients.filter.genderFemale") },
            ]}
            selected={draft.gender}
            onSelect={(gender) => {
              setDraft((current) => ({ ...current, gender }));
            }}
          />
          <ToggleFilter
            label={t("clients.filter.noGroup")}
            checked={draft.noGroup}
            onChange={(noGroup) => {
              setDraft((current) => ({ ...current, noGroup }));
            }}
          />
          <ToggleFilter
            label={t("clients.filter.debt")}
            checked={draft.debt}
            onChange={(debt) => {
              setDraft((current) => ({ ...current, debt }));
            }}
          />
          <SegmentedFilter
            label={t("clients.filter.birthday")}
            options={[
              { value: "none", label: t("clients.filter.birthdayAll") },
              { value: "today", label: t("clients.filter.birthdayToday") },
              { value: "tomorrow", label: t("clients.filter.birthdayTomorrow") },
              { value: "week", label: t("clients.filter.birthdayWeek") },
            ]}
            selected={draft.birthday}
            onSelect={(birthday) => {
              setDraft((current) => ({ ...current, birthday }));
            }}
          />
        </div>
        <SheetFooter className="flex-row">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => {
              setDraft((current) => ({
                ...current,
                gender: "all",
                debt: false,
                noGroup: false,
                birthday: "none",
              }));
            }}
          >
            {t("clients.filter.reset")}
          </Button>
          <Button
            className="flex-1"
            onClick={() => {
              onApply(draft);
              onOpenChange(false);
            }}
          >
            {t("clients.filter.apply")}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

/** Группа взаимоисключающих вариантов фильтра: подпись [label] и кнопки-сегменты. */
function SegmentedFilter<V extends string>({
  label,
  options,
  selected,
  onSelect,
}: {
  readonly label: string;
  readonly options: readonly { readonly value: V; readonly label: string }[];
  readonly selected: V;
  readonly onSelect: (value: V) => void;
}) {
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-medium">{label}</legend>
      <div className="grid auto-cols-fr grid-flow-col">
        {options.map((option) => {
          const active = option.value === selected;
          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={active}
              onClick={() => {
                onSelect(option.value);
              }}
              className={cn(
                "-mx-px border px-2 py-2 text-sm outline-none first:rounded-l-md last:rounded-r-md focus-visible:ring-[3px] focus-visible:ring-ring/50",
                active
                  ? "z-10 border-primary bg-primary text-primary-foreground"
                  : "border-border bg-background hover:bg-accent",
              )}
            >
              {option.label}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}

/** Фильтр-переключатель: подпись [label] и чекбокс с ролью переключателя. */
function ToggleFilter({
  label,
  checked,
  onChange,
}: {
  readonly label: string;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
}) {
  return (
    <label className="flex items-center justify-between gap-4 text-sm font-medium">
      {label}
      <input
        type="checkbox"
        role="switch"
        checked={checked}
        onChange={(event) => {
          onChange(event.target.checked);
        }}
        className="size-4 accent-primary"
      />
    </label>
  );
}
