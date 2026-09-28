import {
  ClientIdSchema,
  EmployeeIdSchema,
  GroupIdSchema,
  HallIdSchema,
  LocalDateSchema,
  LocalTimeSchema,
  type ClientId,
  type LocalDate,
  type LocalTime,
  type ScheduleCoachSchema,
  type ScheduleGroupSchema,
  type ScheduleHallSchema,
  type SessionId,
  type SessionStatus,
} from "@/api/generated/contracts";

/** Статус посещения участника занятия. */
export type AttendanceStatus = "PRESENT" | "ABSENT" | "SICK" | "LATE";

/** Участник занятия с отметкой посещаемости. */
export interface MockParticipant {
  readonly id: ClientId;
  readonly name: string;
  readonly attendance: AttendanceStatus;
}

/**
 * Мок-данные карточки занятия. Форма повторяет вероятную будущую `SessionDetailResponse`
 * (см. design.md изменения `session-detail-page-ui`), чтобы замена мока на реальный запрос
 * не потребовала переписывать страницу.
 */
export interface MockSessionDetail {
  readonly sessionId: SessionId;
  readonly group: ScheduleGroupSchema;
  readonly date: LocalDate;
  readonly startTime: LocalTime;
  readonly endTime: LocalTime;
  readonly hall: ScheduleHallSchema;
  readonly status: SessionStatus;
  readonly coaches: readonly ScheduleCoachSchema[];
  readonly participants: readonly MockParticipant[];
  readonly note: string;
}

/** Данные фикстуры без `sessionId` — он подставляется по идентификатору занятия. */
type Fixture = Omit<MockSessionDetail, "sessionId">;

const client = (id: string, name: string, attendance: AttendanceStatus): MockParticipant => ({
  id: ClientIdSchema.parse(id),
  name,
  attendance,
});

const coach = (id: string, name: string): ScheduleCoachSchema => ({
  id: EmployeeIdSchema.parse(id),
  name,
});

const FIXTURES: readonly Fixture[] = [
  {
    group: { id: GroupIdSchema.parse("0199a0b2-0000-7000-8000-100000000001"), name: "Юниоры У8" },
    date: LocalDateSchema.parse("2026-10-06"),
    startTime: LocalTimeSchema.parse("17:00"),
    endTime: LocalTimeSchema.parse("18:00"),
    hall: { id: HallIdSchema.parse("0199a0b2-0000-7000-8000-200000000001"), name: "Зал А" },
    status: "SCHEDULED",
    coaches: [coach("0199a0b2-0000-7000-8000-300000000001", "Сергей Волков")],
    participants: [
      client("0199a0b2-0000-7000-8000-400000000001", "Аня Кузнецова", "ABSENT"),
      client("0199a0b2-0000-7000-8000-400000000002", "Максим Орлов", "ABSENT"),
      client("0199a0b2-0000-7000-8000-400000000003", "Полина Фролова", "ABSENT"),
      client("0199a0b2-0000-7000-8000-400000000004", "Тимур Гареев", "ABSENT"),
    ],
    note: "",
  },
  {
    group: { id: GroupIdSchema.parse("0199a0b2-0000-7000-8000-100000000002"), name: "Взрослые" },
    date: LocalDateSchema.parse("2026-09-22"),
    startTime: LocalTimeSchema.parse("19:30"),
    endTime: LocalTimeSchema.parse("21:00"),
    hall: { id: HallIdSchema.parse("0199a0b2-0000-7000-8000-200000000002"), name: "Зал Б" },
    status: "COMPLETED",
    coaches: [
      coach("0199a0b2-0000-7000-8000-300000000002", "Ирина Соколова"),
      coach("0199a0b2-0000-7000-8000-300000000003", "Дмитрий Лебедев"),
    ],
    participants: [
      client("0199a0b2-0000-7000-8000-400000000005", "Ольга Зайцева", "PRESENT"),
      client("0199a0b2-0000-7000-8000-400000000006", "Никита Соловьёв", "PRESENT"),
      client("0199a0b2-0000-7000-8000-400000000007", "Мария Попова", "LATE"),
      client("0199a0b2-0000-7000-8000-400000000008", "Артём Новиков", "SICK"),
      client("0199a0b2-0000-7000-8000-400000000009", "Елена Морозова", "ABSENT"),
    ],
    note: "Разбирали технику подачи",
  },
  {
    group: { id: GroupIdSchema.parse("0199a0b2-0000-7000-8000-100000000003"), name: "Юниоры У12" },
    date: LocalDateSchema.parse("2026-09-18"),
    startTime: LocalTimeSchema.parse("16:00"),
    endTime: LocalTimeSchema.parse("17:30"),
    hall: { id: HallIdSchema.parse("0199a0b2-0000-7000-8000-200000000001"), name: "Зал А" },
    status: "CANCELLED",
    coaches: [coach("0199a0b2-0000-7000-8000-300000000001", "Сергей Волков")],
    participants: [
      client("0199a0b2-0000-7000-8000-400000000010", "Вера Егорова", "ABSENT"),
      client("0199a0b2-0000-7000-8000-400000000011", "Илья Кириллов", "ABSENT"),
      client("0199a0b2-0000-7000-8000-400000000012", "Ксения Романова", "ABSENT"),
    ],
    note: "",
  },
];

/** Сумма кодов символов [value] — детерминированный, но неравномерный индекс фикстуры. */
function hash(value: string): number {
  let sum = 0;
  for (let i = 0; i < value.length; i += 1) {
    sum += value.charCodeAt(i);
  }
  return sum;
}

/**
 * Мок-данные карточки занятия [sessionId]. Один и тот же идентификатор всегда даёт одну и ту же
 * фикстуру; разные идентификаторы, как правило, дают фикстуры с разным статусом, чтобы дизайн
 * страницы проверялся на всех статусах занятия без ручного переключения.
 */
export function mockSessionDetail(sessionId: SessionId): MockSessionDetail {
  const fixture = FIXTURES[hash(sessionId) % FIXTURES.length] ?? FIXTURES[0];
  if (fixture === undefined) {
    throw new Error("FIXTURES пуст");
  }
  return { ...fixture, sessionId };
}

/** Отменяет занятие [detail]. */
export function cancelSession(detail: MockSessionDetail): MockSessionDetail {
  return { ...detail, status: "CANCELLED" };
}

/** Переносит занятие [detail] на дату [date] и время [startTime]–[endTime]. */
export function rescheduleSession(
  detail: MockSessionDetail,
  date: LocalDate,
  startTime: LocalTime,
  endTime: LocalTime,
): MockSessionDetail {
  return { ...detail, date, startTime, endTime };
}

/** Меняет зал занятия [detail] на [hall]. */
export function changeHall(detail: MockSessionDetail, hall: ScheduleHallSchema): MockSessionDetail {
  return { ...detail, hall };
}

/** Меняет список тренеров занятия [detail] на [coaches]. */
export function changeCoaches(
  detail: MockSessionDetail,
  coaches: readonly ScheduleCoachSchema[],
): MockSessionDetail {
  return { ...detail, coaches };
}

/** Записывает заметку [note] к занятию [detail]. */
export function setNote(detail: MockSessionDetail, note: string): MockSessionDetail {
  return { ...detail, note };
}

/** Отмечает посещаемость участника [participantId] занятия [detail] статусом [attendance]. */
export function setAttendance(
  detail: MockSessionDetail,
  participantId: ClientId,
  attendance: AttendanceStatus,
): MockSessionDetail {
  return {
    ...detail,
    participants: detail.participants.map((participant) =>
      participant.id === participantId ? { ...participant, attendance } : participant,
    ),
  };
}
