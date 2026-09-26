import type { LocalTime, ScheduleSessionSchema } from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";

/** Час начала времени [time] вида `HH:MM` или `HH:MM:SS`. */
function hourOf(time: LocalTime): number {
  const [hours = 0] = time.split(":").map(Number);
  return hours;
}

/** Минуты от начала суток времени [time]. */
function minutesOf(time: LocalTime): number {
  const [hours = 0, minutes = 0] = time.split(":").map(Number);
  return hours * 60 + minutes;
}

/** Часы, в которые начинается хотя бы одно занятие из [sessions], по возрастанию. */
export function occupiedHours(sessions: readonly ScheduleSessionSchema[]): readonly number[] {
  return Array.from(new Set(sessions.map((session) => hourOf(session.startTime)))).sort(
    (a, b) => a - b,
  );
}

/** Занятия [sessions] дня [date] и часа начала [hour], по возрастанию времени начала. */
export function sessionsAt(
  sessions: readonly ScheduleSessionSchema[],
  date: string,
  hour: number,
): readonly ScheduleSessionSchema[] {
  return sessions
    .filter((session) => session.date === date && hourOf(session.startTime) === hour)
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
}

/** Продолжительность занятия [start]–[end] в часах и минутах, без нулевой части. */
export function durationLabel(t: I18n["t"], start: LocalTime, end: LocalTime): string {
  const total = minutesOf(end) - minutesOf(start);
  const hours = Math.floor(total / 60);
  const minutes = total % 60;
  if (hours > 0 && minutes > 0) {
    return `${t("schedule.hours", { count: hours })} ${t("schedule.minutes", { count: minutes })}`;
  }
  if (hours > 0) {
    return t("schedule.hours", { count: hours });
  }
  return t("schedule.minutes", { count: minutes });
}
