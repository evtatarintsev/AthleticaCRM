import {
  LocalDateSchema,
  type LocalDate,
  type LocalTime,
  type ScheduleSessionSchema,
} from "@/api/generated/contracts";

/** За сколько минут до начала сегодняшнее занятие получает метку «через N мин». */
export const SOON_MINUTES = 60;

/** Текущий момент в часовом поясе организации: календарная дата и минуты от начала суток. */
export interface OrgNow {
  readonly date: LocalDate;
  readonly minutes: number;
}

/** Момент [now] в часовом поясе организации [timeZone]; пояс браузера не участвует. */
export function orgNowOf(now: Date, timeZone: string): OrgNow {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "00";
  return {
    date: LocalDateSchema.parse(`${part("year")}-${part("month")}-${part("day")}`),
    minutes: Number(part("hour")) * 60 + Number(part("minute")),
  };
}

/** Дата [date] (`YYYY-MM-DD`) как `Date` в UTC-полночь, без сдвига по часовому поясу браузера. */
export function utcDate(date: LocalDate): Date {
  const [year = 1970, month = 1, day = 1] = date.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

/** Подпись дня [date] вида «среда, 18 ноября» на языке [locale]. */
export function longDayLabel(locale: string, date: LocalDate): string {
  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  }).format(utcDate(date));
}

/**
 * Подпись недели с понедельника [monday] на языке [locale]: «16 – 22 ноября 2026», а при смене
 * месяца — «30 ноября – 6 декабря 2026».
 */
export function weekRangeLabel(locale: string, monday: LocalDate): string {
  const sunday = utcDate(monday);
  sunday.setUTCDate(sunday.getUTCDate() + 6);
  const dayMonth = new Intl.DateTimeFormat(locale, {
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  });
  const start = utcDate(monday);
  const from =
    start.getUTCMonth() === sunday.getUTCMonth()
      ? String(start.getUTCDate())
      : dayMonth.format(start);
  return `${from} – ${dayMonth.format(sunday)} ${String(sunday.getUTCFullYear())}`;
}

/** Минуты от начала суток времени [time] вида `HH:MM` или `HH:MM:SS`. */
export function minutesOf(time: LocalTime): number {
  const [hours = 0, minutes = 0] = time.split(":").map(Number);
  return hours * 60 + minutes;
}

/** Время `HH:MM` по минутам от начала суток [minutes]. */
export function timeOfMinutes(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

/** Состояние занятия относительно текущего момента. */
export type SessionPhase =
  | { readonly kind: "cancelled" }
  | { readonly kind: "past" }
  | { readonly kind: "live" }
  | { readonly kind: "soon"; readonly minutes: number }
  | { readonly kind: "planned" };

/** Началось ли занятие [session] к моменту [now]. */
export function hasStarted(
  session: Pick<ScheduleSessionSchema, "date" | "startTime">,
  now: OrgNow,
): boolean {
  return (
    session.date < now.date ||
    (session.date === now.date && minutesOf(session.startTime) <= now.minutes)
  );
}

/**
 * Состояние занятия [session] в момент [now]: отменённое — всегда «отменено»; закончившееся —
 * «прошло»; начавшееся — «идёт»; сегодняшнее, до начала которого не больше [SOON_MINUTES], — «скоро».
 */
export function sessionPhaseOf(
  session: Pick<ScheduleSessionSchema, "date" | "startTime" | "endTime" | "status">,
  now: OrgNow,
): SessionPhase {
  if (session.status === "CANCELLED") {
    return { kind: "cancelled" };
  }
  const ended =
    session.date < now.date ||
    (session.date === now.date && minutesOf(session.endTime) <= now.minutes);
  if (ended) {
    return { kind: "past" };
  }
  if (hasStarted(session, now)) {
    return { kind: "live" };
  }
  const untilStart = minutesOf(session.startTime) - now.minutes;
  if (session.date === now.date && untilStart <= SOON_MINUTES) {
    return { kind: "soon", minutes: untilStart };
  }
  return { kind: "planned" };
}
