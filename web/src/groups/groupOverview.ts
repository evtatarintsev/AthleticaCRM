import { z } from "zod";
import type {
  AttendanceLabelSchema,
  AttendanceSessionSchema,
  ClientId,
  DayOfWeek,
  GroupAttendanceResponse,
  GroupClientAttention,
  GroupRosterClient,
  LocalDate,
  LocalTime,
  ScheduleSlot,
  SessionId,
} from "@/api/generated/contracts";
import { addDays, WEEK_DAYS } from "@/lib/localDate";
import { slotsToCards } from "./groupSchedule";

/** Вкладки карточки группы в порядке полосы вкладок. */
export const GROUP_TABS = ["overview", "clients", "memberships", "sessions", "journal"] as const;

/** Вкладка карточки группы. */
export type GroupTab = (typeof GROUP_TABS)[number];

/**
 * Search-параметры адреса `/groups/$groupId`: активная вкладка; отсутствующее или некорректное
 * значение — «Обзор».
 */
export const GroupDetailSearchSchema = z.object({
  tab: z.enum(GROUP_TABS).optional().catch(undefined),
});

/** Search-параметры карточки группы. */
export type GroupDetailSearch = z.output<typeof GroupDetailSearchSchema>;

/** Причины внимания в порядке важности, как их упорядочивает сервер. */
export const ATTENTION_ORDER: readonly GroupClientAttention[] = [
  "DEBT",
  "NO_MEMBERSHIP",
  "MEMBERSHIP_ENDING",
];

/** Число участников по каждой причине внимания и всего с причиной. */
export interface AttentionCounts {
  readonly total: number;
  readonly byReason: Readonly<Record<GroupClientAttention, number>>;
}

/** Счётчики причин внимания по составу [clients]. */
export function attentionCounts(clients: readonly GroupRosterClient[]): AttentionCounts {
  const byReason: Record<GroupClientAttention, number> = {
    DEBT: 0,
    NO_MEMBERSHIP: 0,
    MEMBERSHIP_ENDING: 0,
  };
  clients.forEach((client) => {
    if (client.attention !== null) {
      byReason[client.attention] += 1;
    }
  });
  return { total: byReason.DEBT + byReason.NO_MEMBERSHIP + byReason.MEMBERSHIP_ENDING, byReason };
}

/** Фильтр вкладки «Клиенты». */
export type RosterFilter = "all" | "attention" | "ok";

/**
 * Участники [clients], подходящие под фильтр [filter] и поиск [query] по имени без учёта регистра;
 * порядок состава сохраняется.
 */
export function filterRoster(
  clients: readonly GroupRosterClient[],
  filter: RosterFilter,
  query: string,
): readonly GroupRosterClient[] {
  const needle = query.trim().toLocaleLowerCase();
  return clients.filter((client) => {
    const byFilter =
      filter === "all" ||
      (filter === "attention" ? client.attention !== null : client.attention === null);
    return byFilter && client.name.toLocaleLowerCase().includes(needle);
  });
}

/** Действие в строке участника с причиной внимания. */
export type RosterAction = "repay" | "openClient" | "issue" | "issueNext";

/**
 * Действие для участника [client]: долг гасится с абонемента, только если есть неоплаченные
 * занятия, абонемент активирован и в нём остались занятия (или безлимит), иначе — переход в карточку
 * клиента; без абонемента — выдача, заканчивающийся — выдача нового. Без причины — `null`.
 */
export function rosterAction(client: GroupRosterClient): RosterAction | null {
  switch (client.attention) {
    case null:
      return null;
    case "DEBT": {
      const membership = client.membership;
      const canRepay =
        client.unpaidSessions > 0 &&
        membership !== null &&
        membership.activated &&
        (membership.sessionsRemaining === null || membership.sessionsRemaining > 0);
      return canRepay ? "repay" : "openClient";
    }
    case "NO_MEMBERSHIP":
      return "issue";
    case "MEMBERSHIP_ENDING":
      return "issueNext";
  }
}

/** Группа вкладки «Абонементы»: участники с абонементом [name] (`null` — без абонемента). */
export interface MembershipGroup {
  readonly name: string | null;
  readonly clients: readonly GroupRosterClient[];
  /** Число участников с причиной `MEMBERSHIP_ENDING`. */
  readonly ending: number;
}

/**
 * Состав [clients], сгруппированный по названию абонемента, действующего в группе: группы по
 * убыванию числа участников, затем по названию; «Без абонемента» — последней.
 */
export function membershipGroups(
  clients: readonly GroupRosterClient[],
): readonly MembershipGroup[] {
  const byName = new Map<string | null, GroupRosterClient[]>();
  clients.forEach((client) => {
    const name = client.membership?.name ?? null;
    byName.set(name, [...(byName.get(name) ?? []), client]);
  });
  return Array.from(byName, ([name, members]) => ({
    name,
    clients: members,
    ending: members.filter((client) => client.attention === "MEMBERSHIP_ENDING").length,
  })).sort((a, b) => {
    if ((a.name === null) !== (b.name === null)) {
      return a.name === null ? 1 : -1;
    }
    const bySize = b.clients.length - a.clients.length;
    return bySize !== 0 ? bySize : (a.name ?? "").localeCompare(b.name ?? "");
  });
}

/** Сколько последних закрытых занятий учитывает плитка «Посещаемость». */
export const ATTENDANCE_SESSIONS = 10;

/** Глубина запроса посещаемости для плитки «Посещаемость», дней назад. */
export const ATTENDANCE_LOOKBACK_DAYS = 92;

/** Явка на одном закрытом занятии. */
export interface SessionTurnout {
  readonly session: AttendanceSessionSchema;
  readonly present: number;
  readonly total: number;
}

/** Посещаемость по последним закрытым занятиям: доля «пришёл» в процентах и явка по занятиям. */
export interface AttendanceSummary {
  readonly percent: number;
  readonly sessions: readonly SessionTurnout[];
}

/**
 * Сводка посещаемости по последним [ATTENDANCE_SESSIONS] закрытым занятиям ответа [attendance]:
 * сумма «пришёл» к сумме отметок участников; `null` — закрытых занятий нет.
 */
export function attendanceSummary(attendance: GroupAttendanceResponse): AttendanceSummary | null {
  const sessions = attendance.sessions.slice(-ATTENDANCE_SESSIONS).map((session) => {
    const marks = attendance.participants.flatMap((row) =>
      row.marks.filter((mark) => mark.sessionId === session.id),
    );
    return {
      session,
      present: marks.filter((mark) => mark.presence === "PRESENT").length,
      total: marks.length,
    };
  });
  if (sessions.length === 0) {
    return null;
  }
  const present = sessions.reduce((sum, s) => sum + s.present, 0);
  const total = sessions.reduce((sum, s) => sum + s.total, 0);
  return { percent: total === 0 ? 0 : Math.round((present * 100) / total), sessions };
}

/** Клетка журнала: «пришёл», «не пришёл» с метками или «не участвовал». */
export type JournalCell =
  | {
      readonly kind: "present" | "absent";
      readonly sessionId: SessionId;
      readonly labels: readonly AttendanceLabelSchema[];
    }
  | { readonly kind: "none" };

/** Строка журнала: участник и клетки по занятиям периода. */
export interface JournalRow {
  readonly clientId: ClientId;
  readonly name: string;
  /** Отмечен в периоде, но уже не в группе. */
  readonly former: boolean;
  readonly cells: readonly JournalCell[];
}

/**
 * Строки журнала: текущий состав [roster] по имени (новые — с прочерками), затем отмеченные в
 * [attendance], но исключённые из группы — с пометкой `former`.
 */
export function journalRows(
  roster: readonly GroupRosterClient[],
  attendance: GroupAttendanceResponse,
): readonly JournalRow[] {
  const marksOf = new Map(attendance.participants.map((row) => [row.clientId, row.marks]));
  const current = new Set(roster.map((client) => client.id));
  const row = (clientId: ClientId, name: string, former: boolean): JournalRow => {
    const marks = marksOf.get(clientId) ?? [];
    return {
      clientId,
      name,
      former,
      cells: attendance.sessions.map((session): JournalCell => {
        const mark = marks.find((m) => m.sessionId === session.id);
        if (mark === undefined) {
          return { kind: "none" };
        }
        return {
          kind: mark.presence === "PRESENT" ? "present" : "absent",
          sessionId: session.id,
          labels: mark.labels,
        };
      }),
    };
  };
  return [
    ...roster
      .toSorted((a, b) => a.name.localeCompare(b.name))
      .map((client) => row(client.id, client.name, false)),
    ...attendance.participants
      .filter((participant) => !current.has(participant.clientId))
      .map((participant) => row(participant.clientId, participant.name, true)),
  ];
}

/** Период журнала: [from]..[to] включительно. */
export interface JournalPeriod {
  readonly from: LocalDate;
  readonly to: LocalDate;
}

/** Длина периода журнала в днях. */
const JOURNAL_PERIOD_DAYS = 28;

/** Период журнала по умолчанию: последние 4 недели по [today] включительно. */
export function defaultJournalPeriod(today: LocalDate): JournalPeriod {
  return { from: addDays(today, 1 - JOURNAL_PERIOD_DAYS), to: today };
}

/** Период [period], сдвинутый на [steps] периодов (отрицательное — назад). */
export function shiftJournalPeriod(period: JournalPeriod, steps: number): JournalPeriod {
  return {
    from: addDays(period.from, steps * JOURNAL_PERIOD_DAYS),
    to: addDays(period.to, steps * JOURNAL_PERIOD_DAYS),
  };
}

/** Строка расписания: дни недели с одинаковым временем и залом. */
export interface ScheduleRow {
  readonly days: readonly DayOfWeek[];
  readonly startAt: LocalTime;
  readonly endAt: LocalTime;
  readonly hallName: string | null;
}

/** Слоты [slots] по одному на строку, по дню недели и времени начала. */
export function scheduleRowsByDay(slots: readonly ScheduleSlot[]): readonly ScheduleRow[] {
  return slots
    .toSorted((a, b) => {
      const byDay = WEEK_DAYS.indexOf(a.dayOfWeek) - WEEK_DAYS.indexOf(b.dayOfWeek);
      return byDay !== 0 ? byDay : a.startAt.localeCompare(b.startAt);
    })
    .map((slot) => ({
      days: [slot.dayOfWeek],
      startAt: slot.startAt,
      endAt: slot.endAt,
      hallName: slot.hallName,
    }));
}

/** Слоты [slots] с одинаковым временем и залом, склеенные в одну строку (раскладка телефона). */
export function mergedScheduleRows(slots: readonly ScheduleSlot[]): readonly ScheduleRow[] {
  return slotsToCards(slots).flatMap((card) => {
    const slot = slots.find(
      (s) => s.startAt === card.startAt && s.endAt === card.endAt && s.hallId === card.hallId,
    );
    return slot === undefined
      ? []
      : [
          {
            days: WEEK_DAYS.filter((day) => card.days.has(day)),
            startAt: slot.startAt,
            endAt: slot.endAt,
            hallName: slot.hallName,
          },
        ];
  });
}
