import type { LocalDate, TodaySessionItem } from "@/api/generated/contracts";

/** Состояния ленты по локальным часам браузера; чужая дата остаётся без подсветки. */
export function sessionStates(
  sessions: readonly Pick<TodaySessionItem, "startTime" | "endTime">[],
  responseDate: LocalDate | undefined,
  today: LocalDate,
  now: Date,
): readonly ("past" | "live" | "next" | "none")[] {
  const minutes = now.getHours() * 60 + now.getMinutes();
  const nextStart = Math.min(
    ...sessions.map((session) => timeMinutes(session.startTime)).filter((start) => start > minutes),
  );
  return sessions.map((session) => {
    if (responseDate !== today) {
      return "none";
    }
    if (timeMinutes(session.endTime) <= minutes) {
      return "past";
    }
    if (timeMinutes(session.startTime) <= minutes) {
      return "live";
    }
    return timeMinutes(session.startTime) === nextStart ? "next" : "none";
  });
}

function timeMinutes(time: string): number {
  const [hours = 0, minutes = 0] = time.split(":").map(Number);
  return hours * 60 + minutes;
}

/** Календарная разница без влияния переходов на летнее время. */
export function journalAgeDays(date: LocalDate, today: LocalDate): number {
  return (Date.parse(today) - Date.parse(date)) / 86_400_000;
}

/** Возраст, который исполнится в ближайший день рождения начиная с [today] включительно. */
export function upcomingAge(birthday: LocalDate, today: LocalDate): number {
  const age = Number(today.slice(0, 4)) - Number(birthday.slice(0, 4));
  return birthday.slice(5) < today.slice(5) ? age + 1 : age;
}

/** 29 февраля совпадает только с 29 февраля. */
export function isBirthdayToday(birthday: LocalDate, today: LocalDate): boolean {
  return birthday.slice(5) === today.slice(5);
}
