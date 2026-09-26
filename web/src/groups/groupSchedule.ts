import {
  LocalTimeSchema,
  type DayOfWeek,
  type HallId,
  type ScheduleSlot,
  type ScheduleSlotInput,
} from "@/api/generated/contracts";
import { WEEK_DAYS } from "@/lib/localDate";

/**
 * Карточка редактора расписания: набор дней недели с одинаковым временем и залом.
 * [id] — локальный идентификатор карточки в редакторе, на сервер не отправляется.
 */
export interface SlotCard {
  readonly id: number;
  readonly days: ReadonlySet<DayOfWeek>;
  /** Время в формате `HH:MM`, как отдаёт `<input type="time">`. */
  readonly startAt: string;
  readonly endAt: string;
  readonly hallId: HallId | null;
}

/** Причина, по которой карточку нельзя сохранить. */
export type SlotCardError = "noDays" | "noHall" | "endNotAfterStart" | "duplicate";

/** Пустая карточка с идентификатором [id]; зал подставлен, только если он единственный в филиале. */
export function newCard(id: number, singleHallId: HallId | null): SlotCard {
  return { id, days: new Set(), startAt: "", endAt: "", hallId: singleHallId };
}

/**
 * Карточки из действующих слотов [slots]: слоты с одинаковым временем и залом группируются
 * в одну карточку по дням, карточки отсортированы по первому дню, затем по времени начала.
 */
export function slotsToCards(slots: readonly ScheduleSlot[]): SlotCard[] {
  const groups = new Map<
    string,
    {
      readonly startAt: string;
      readonly endAt: string;
      readonly hallId: HallId;
      readonly days: Set<DayOfWeek>;
    }
  >();
  slots.forEach((slot) => {
    const key = `${slot.startAt}|${slot.endAt}|${slot.hallId}`;
    const group = groups.get(key);
    if (group === undefined) {
      groups.set(key, {
        startAt: slot.startAt,
        endAt: slot.endAt,
        hallId: slot.hallId,
        days: new Set([slot.dayOfWeek]),
      });
    } else {
      group.days.add(slot.dayOfWeek);
    }
  });
  return Array.from(groups.values())
    .map((group, index) => ({
      id: index,
      days: group.days,
      startAt: group.startAt,
      endAt: group.endAt,
      hallId: group.hallId,
    }))
    .sort((a, b) => {
      const byDay = earliestDayIndex(a.days) - earliestDayIndex(b.days);
      return byDay !== 0 ? byDay : a.startAt.localeCompare(b.startAt);
    });
}

/** Слоты для запроса `/groups/set-schedule` из карточек [cards]; карточка без зала не даёт слотов. */
export function cardsToSlotInputs(cards: readonly SlotCard[]): readonly ScheduleSlotInput[] {
  return cards.flatMap((card) => {
    const { hallId } = card;
    if (hallId === null || card.startAt === "" || card.endAt === "") {
      return [];
    }
    return WEEK_DAYS.filter((day) => card.days.has(day)).map((day) => ({
      dayOfWeek: day,
      startAt: LocalTimeSchema.parse(card.startAt),
      endAt: LocalTimeSchema.parse(card.endAt),
      hallId,
    }));
  });
}

/**
 * Ошибки каждой карточки из [cards] (см. `group-schedule-editor`): без дней, без зала,
 * окончание не позже начала, повтор дня и времени начала в другой карточке.
 * Карточка без ошибок в результат не попадает.
 */
export function cardErrors(
  cards: readonly SlotCard[],
): ReadonlyMap<number, readonly SlotCardError[]> {
  const dayStartCounts = new Map<string, number>();
  cards.forEach((card) => {
    card.days.forEach((day) => {
      const key = `${day}|${card.startAt}`;
      dayStartCounts.set(key, (dayStartCounts.get(key) ?? 0) + 1);
    });
  });
  const result = new Map<number, SlotCardError[]>();
  cards.forEach((card) => {
    const errors: SlotCardError[] = [];
    if (card.days.size === 0) {
      errors.push("noDays");
    }
    if (card.hallId === null) {
      errors.push("noHall");
    }
    if (card.startAt !== "" && card.endAt !== "" && !(card.startAt < card.endAt)) {
      errors.push("endNotAfterStart");
    }
    const duplicate = Array.from(card.days).some(
      (day) => (dayStartCounts.get(`${day}|${card.startAt}`) ?? 0) > 1,
    );
    if (duplicate) {
      errors.push("duplicate");
    }
    if (errors.length > 0) {
      result.set(card.id, errors);
    }
  });
  return result;
}

/** Индекс первого (по порядку недели) дня из [days]; для пустого набора — конец списка. */
function earliestDayIndex(days: ReadonlySet<DayOfWeek>): number {
  const indices = WEEK_DAYS.map((day, index) => (days.has(day) ? index : WEEK_DAYS.length));
  return Math.min(...indices);
}
