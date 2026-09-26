import {
  DayOfWeekSchema,
  LocalDateSchema,
  type DayOfWeek,
  type LocalDate,
} from "@/api/generated/contracts";

/** Дни недели с понедельника по воскресенье — порядок колонок расписания и чипов дней. */
export const WEEK_DAYS: readonly DayOfWeek[] = DayOfWeekSchema.options;

/** Год, месяц (с нуля) и день даты [date] вида `2025-01-31`, без сдвига по часовому поясу. */
function dateParts(date: LocalDate): [number, number, number] {
  const [year = 1970, month = 1, day = 1] = date.split("-").map(Number);
  return [year, month - 1, day];
}

/** Сегодняшний день в часовом поясе браузера. */
export function todayLocalDate(): LocalDate {
  return LocalDateSchema.parse(
    new Intl.DateTimeFormat("en-CA", { year: "numeric", month: "2-digit", day: "2-digit" }).format(
      new Date(),
    ),
  );
}

/** Дата [date] плюс [days] дней (может быть отрицательным), без сдвига по часовому поясу. */
export function addDays(date: LocalDate, days: number): LocalDate {
  const shifted = new Date(Date.UTC(...dateParts(date)) + days * 86_400_000);
  return LocalDateSchema.parse(shifted.toISOString().slice(0, 10));
}

/** День недели даты [date]. */
export function dayOfWeekOf(date: LocalDate): DayOfWeek {
  const isoDay = new Date(Date.UTC(...dateParts(date))).getUTCDay();
  const index = isoDay === 0 ? 6 : isoDay - 1;
  const day = WEEK_DAYS[index];
  if (day === undefined) {
    throw new Error(`Некорректный день недели: ${String(isoDay)}`);
  }
  return day;
}

/** Понедельник недели, которой принадлежит дата [date]. */
export function mondayOf(date: LocalDate): LocalDate {
  return addDays(date, WEEK_DAYS.indexOf(dayOfWeekOf(date)) * -1);
}

/** Семь дат недели, начинающейся в понедельник [monday]. */
export function weekDates(monday: LocalDate): readonly LocalDate[] {
  return WEEK_DAYS.map((_, index) => addDays(monday, index));
}
