import type { LocalDate } from "@/api/generated/contracts";
import { addDays } from "@/lib/localDate";

/** Сколько дней до сегодняшнего входит в окно блока занятий по умолчанию. */
const DAYS_BEFORE = 14;

/** Сколько дней после сегодняшнего входит в окно блока занятий по умолчанию. */
const DAYS_AFTER = 28;

/** Длина окна в днях, включая обе границы; на неё же окно сдвигается. */
export const WINDOW_LENGTH_DAYS = DAYS_BEFORE + DAYS_AFTER + 1;

/** Период блока занятий карточки группы: первый и последний день включительно. */
export interface SessionsWindow {
  readonly from: LocalDate;
  readonly to: LocalDate;
}

/** Окно по умолчанию вокруг сегодняшнего дня [today]: от двух недель назад до четырёх вперёд. */
export function defaultSessionsWindow(today: LocalDate): SessionsWindow {
  return { from: addDays(today, -DAYS_BEFORE), to: addDays(today, DAYS_AFTER) };
}

/** Окно [window], сдвинутое на свою длину назад ([direction] = -1) или вперёд (1). */
export function shiftSessionsWindow(window: SessionsWindow, direction: -1 | 1): SessionsWindow {
  const days = WINDOW_LENGTH_DAYS * direction;
  return { from: addDays(window.from, days), to: addDays(window.to, days) };
}

/** Положение даты [date] относительно сегодняшнего дня [today] для разделения списка. */
export function dayPosition(date: LocalDate, today: LocalDate): "past" | "today" | "future" {
  if (date < today) {
    return "past";
  }
  return date === today ? "today" : "future";
}
