import { PlusIcon, TrashIcon } from "lucide-react";
import { useState } from "react";
import {
  LocalDateSchema,
  type DayOfWeek,
  type HallId,
  type LocalDate,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { NativeSelect } from "@/components/ui/native-select";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type I18n } from "@/i18n/context";
import { todayLocalDate, WEEK_DAYS } from "@/lib/localDate";
import { cardErrors, newCard, type SlotCard, type SlotCardError } from "./groupSchedule";

/**
 * Диалог редактирования расписания группы (спецификация `group-schedule-editor`):
 * карточки дней+времени+зала, дата вступления в силу, проверки перед сохранением,
 * работает без горизонтальной прокрутки от 320 CSS px, кнопки сохранения закреплены.
 */
export function GroupScheduleDialog({
  initialCards,
  scheduleChangeAt,
  halls,
  onSave,
  onClose,
}: {
  readonly initialCards: readonly SlotCard[];
  /** Дата уже запланированного изменения расписания, если оно есть. */
  readonly scheduleChangeAt: LocalDate | null;
  readonly halls: readonly { readonly id: HallId; readonly name: string }[];
  /** Сохраняет карточки [cards] с даты [effectiveFrom]; возвращает текст ошибки или `null`. */
  readonly onSave: (cards: readonly SlotCard[], effectiveFrom: LocalDate) => Promise<string | null>;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const today = todayLocalDate();
  const [cards, setCards] = useState<readonly SlotCard[]>(initialCards);
  const [effectiveFrom, setEffectiveFrom] = useState<LocalDate>(today);
  const [failure, setFailure] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const errors = cardErrors(cards);
  const canSave = errors.size === 0 && !saving;
  const cancelsPlannedChange =
    scheduleChangeAt !== null && !saving && effectiveFrom <= scheduleChangeAt;

  const updateCard = (id: number, next: SlotCard) => {
    setCards(cards.map((card) => (card.id === id ? next : card)));
  };

  const save = async () => {
    setSaving(true);
    setFailure(null);
    const error = await onSave(cards, effectiveFrom);
    setSaving(false);
    if (error === null) {
      onClose();
    } else {
      setFailure(error);
    }
  };

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent
        aria-describedby={undefined}
        closeLabel={t("action.close")}
        className="flex max-h-[90vh] flex-col gap-3 p-4 sm:max-w-md"
      >
        <DialogHeader>
          <DialogTitle>{t("groups.schedule.title")}</DialogTitle>
        </DialogHeader>
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto">
          {failure !== null && <FormAlert message={failure} />}

          <label className="block space-y-1.5">
            <span className="text-sm font-medium">{t("groups.schedule.effectiveFrom")}</span>
            <input
              type="date"
              value={effectiveFrom}
              min={today}
              onChange={(event) => {
                const parsed = LocalDateSchema.safeParse(event.target.value);
                if (parsed.success) {
                  setEffectiveFrom(parsed.data);
                }
              }}
              className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
            />
          </label>
          {cancelsPlannedChange && (
            <p className="text-xs text-amber-600 dark:text-amber-500">
              {t("groups.schedule.plannedChangeWarning", { date: scheduleChangeAt })}
            </p>
          )}

          {halls.length === 0 && (
            <p className="text-sm text-muted-foreground">{t("groups.schedule.noHalls")}</p>
          )}

          <div className="space-y-3">
            {cards.map((card) => (
              <SlotCardEditor
                key={card.id}
                card={card}
                halls={halls}
                errors={errors.get(card.id) ?? []}
                onChange={(next) => {
                  updateCard(card.id, next);
                }}
                onRemove={() => {
                  setCards(cards.filter((c) => c.id !== card.id));
                }}
              />
            ))}
          </div>

          <Button
            type="button"
            variant="outline"
            className="w-full"
            onClick={() => {
              const singleHallId = halls.length === 1 ? (halls[0]?.id ?? null) : null;
              const nextId = Math.max(-1, ...cards.map((c) => c.id)) + 1;
              setCards([...cards, newCard(nextId, singleHallId)]);
            }}
          >
            <PlusIcon aria-hidden />
            {t("groups.schedule.addCard")}
          </Button>
        </div>
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            disabled={!canSave}
            aria-busy={saving}
            onClick={() => {
              void save();
            }}
          >
            {t("action.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Карточка одной строки расписания: дни, время, зал, удаление и сообщения об ошибках. */
function SlotCardEditor({
  card,
  halls,
  errors,
  onChange,
  onRemove,
}: {
  readonly card: SlotCard;
  readonly halls: readonly { readonly id: HallId; readonly name: string }[];
  readonly errors: readonly SlotCardError[];
  readonly onChange: (card: SlotCard) => void;
  readonly onRemove: () => void;
}) {
  const { t } = useI18n();
  const toggleDay = (day: DayOfWeek) => {
    const days = new Set(card.days);
    if (days.has(day)) {
      days.delete(day);
    } else {
      days.add(day);
    }
    onChange({ ...card, days });
  };

  return (
    <div className="space-y-2 rounded-md border p-3">
      <div className="grid grid-cols-7 gap-1">
        {WEEK_DAYS.map((day) => {
          const active = card.days.has(day);
          return (
            <button
              key={day}
              type="button"
              aria-pressed={active}
              onClick={() => {
                toggleDay(day);
              }}
              className={
                active
                  ? "rounded-md border border-primary bg-primary py-1.5 text-[11px] font-medium text-primary-foreground"
                  : "rounded-md border py-1.5 text-[11px] font-medium text-muted-foreground hover:bg-accent"
              }
            >
              {t(`day.${day}`)}
            </button>
          );
        })}
      </div>

      <div className="grid grid-cols-2 gap-2">
        <label className="block space-y-1">
          <span className="text-xs text-muted-foreground">{t("groups.schedule.startTime")}</span>
          <input
            type="time"
            value={card.startAt}
            onChange={(event) => {
              onChange({ ...card, startAt: event.target.value });
            }}
            className="w-full min-w-0 rounded-md border border-input bg-transparent px-2 py-1.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30"
          />
        </label>
        <label className="block space-y-1">
          <span className="text-xs text-muted-foreground">{t("groups.schedule.endTime")}</span>
          <input
            type="time"
            value={card.endAt}
            onChange={(event) => {
              onChange({ ...card, endAt: event.target.value });
            }}
            className="w-full min-w-0 rounded-md border border-input bg-transparent px-2 py-1.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 dark:bg-input/30"
          />
        </label>
      </div>

      <div className="flex items-end gap-2">
        <label className="block min-w-0 flex-1 space-y-1">
          <span className="text-xs text-muted-foreground">{t("groups.schedule.hall")}</span>
          <NativeSelect
            value={card.hallId ?? ""}
            onChange={(event) => {
              const value = event.target.value;
              onChange({ ...card, hallId: value === "" ? null : hallIdOf(halls, value) });
            }}
          >
            <option value="">{t("groups.schedule.hallNotSelected")}</option>
            {halls.map((hall) => (
              <option key={hall.id} value={hall.id}>
                {hall.name}
              </option>
            ))}
          </NativeSelect>
        </label>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={t("groups.schedule.removeCard")}
          onClick={onRemove}
        >
          <TrashIcon aria-hidden />
        </Button>
      </div>

      {errors.length > 0 && (
        <ul className="space-y-0.5 text-xs text-destructive">
          {errors.map((error) => (
            <li key={error}>{errorMessage(t, error)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Идентификатор зала из [halls], совпадающий со значением `<select>` [value]. */
function hallIdOf(
  halls: readonly { readonly id: HallId; readonly name: string }[],
  value: string,
): HallId | null {
  return halls.find((hall) => hall.id === value)?.id ?? null;
}

/** Текст ошибки карточки [error] на языке [t]. */
function errorMessage(t: I18n["t"], error: SlotCardError): string {
  switch (error) {
    case "noDays":
      return t("groups.schedule.errorNoDays");
    case "noHall":
      return t("groups.schedule.errorNoHall");
    case "endNotAfterStart":
      return t("groups.schedule.errorEndBeforeStart");
    case "duplicate":
      return t("groups.schedule.errorDuplicate");
  }
}
