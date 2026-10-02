import type {
  LocalDate,
  LocalTime,
  ScheduleCoachSchema,
  ScheduleHallSchema,
  SessionJournalResponse,
  SessionStatus,
} from "@/api/generated/contracts";

/**
 * Данные карточки занятия, которые меняют пока не подключённые к серверу действия
 * (отменить, перенести, сменить зал, сменить тренеров, заметка). Журнал посещаемости
 * и статус приходят с сервера; эти действия накладывают поверх них локальные изменения.
 */
export interface SessionCardOverrides {
  readonly status?: SessionStatus;
  readonly date?: LocalDate;
  readonly startTime?: LocalTime;
  readonly endTime?: LocalTime;
  readonly hall?: ScheduleHallSchema;
  readonly coaches?: readonly ScheduleCoachSchema[];
  readonly note?: string;
}

/** Карточка занятия с наложенными локальными изменениями. */
export interface SessionCard {
  readonly status: SessionStatus;
  readonly date: LocalDate;
  readonly startTime: LocalTime;
  readonly endTime: LocalTime;
  readonly hall: ScheduleHallSchema;
  readonly coaches: readonly ScheduleCoachSchema[];
  readonly note: string;
}

/** Карточка журнала [journal] с локальными изменениями [overrides]. */
export function sessionCard(
  journal: SessionJournalResponse,
  overrides: SessionCardOverrides,
): SessionCard {
  return {
    status: overrides.status ?? journal.status,
    date: overrides.date ?? journal.date,
    startTime: overrides.startTime ?? journal.startTime,
    endTime: overrides.endTime ?? journal.endTime,
    hall: overrides.hall ?? journal.hall,
    coaches: overrides.coaches ?? journal.coaches,
    note: overrides.note ?? "",
  };
}

/** Отменяет занятие локально. */
export function cancelSession(overrides: SessionCardOverrides): SessionCardOverrides {
  return { ...overrides, status: "CANCELLED" };
}

/** Переносит занятие на дату [date] и время [startTime]–[endTime]. */
export function rescheduleSession(
  overrides: SessionCardOverrides,
  date: LocalDate,
  startTime: LocalTime,
  endTime: LocalTime,
): SessionCardOverrides {
  return { ...overrides, date, startTime, endTime };
}

/** Меняет зал занятия на [hall]. */
export function changeHall(
  overrides: SessionCardOverrides,
  hall: ScheduleHallSchema,
): SessionCardOverrides {
  return { ...overrides, hall };
}

/** Меняет список тренеров занятия на [coaches]. */
export function changeCoaches(
  overrides: SessionCardOverrides,
  coaches: readonly ScheduleCoachSchema[],
): SessionCardOverrides {
  return { ...overrides, coaches };
}

/** Записывает заметку [note] к занятию. */
export function setNote(overrides: SessionCardOverrides, note: string): SessionCardOverrides {
  return { ...overrides, note };
}
