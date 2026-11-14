import { describe, expect, it } from "vitest";
import {
  AttendanceLabelIdSchema,
  ClientIdSchema,
  DayOfWeekSchema,
  HallIdSchema,
  LocalDateSchema,
  LocalTimeSchema,
  MembershipIdSchema,
  SessionIdSchema,
  type AttendanceLabelSchema,
  type ClientId,
  type GroupAttendanceResponse,
  type GroupClientAttention,
  type GroupRosterClient,
  type GroupRosterMembership,
  type ScheduleSlot,
  type SessionId,
} from "@/api/generated/contracts";
import { uuidv7 } from "@/lib/uuid";
import {
  attendanceSummary,
  attentionCounts,
  defaultJournalPeriod,
  filterRoster,
  GroupDetailSearchSchema,
  journalRows,
  membershipGroups,
  mergedScheduleRows,
  rosterAction,
  shiftJournalPeriod,
} from "./groupOverview";

const date = (value: string) => LocalDateSchema.parse(value);
const time = (value: string) => LocalTimeSchema.parse(value);
const sessionId = () => SessionIdSchema.parse(uuidv7());

function membership(overrides: Partial<GroupRosterMembership> = {}): GroupRosterMembership {
  return {
    id: MembershipIdSchema.parse(uuidv7()),
    name: "8 занятий",
    activated: true,
    sessionsTotal: 8,
    sessionsRemaining: 5,
    endDate: date("2026-12-01"),
    endingSoon: [],
    closePending: false,
    ...overrides,
  };
}

function client(
  name: string,
  attention: GroupClientAttention | null = null,
  overrides: Partial<GroupRosterClient> = {},
): GroupRosterClient {
  return {
    id: ClientIdSchema.parse(uuidv7()),
    name,
    avatarId: null,
    membership: membership(),
    hasNextMembership: false,
    unpaidSessions: 0,
    balance: { minorUnits: 0, currency: "RUB" },
    attention,
    ...overrides,
  };
}

describe("search-параметр вкладки", () => {
  it("принимает известную вкладку", () => {
    expect(GroupDetailSearchSchema.parse({ tab: "journal" })).toEqual({ tab: "journal" });
  });

  it("неизвестная вкладка сбрасывается к «Обзору»", () => {
    expect(GroupDetailSearchSchema.parse({ tab: "unknown" }).tab).toBeUndefined();
  });
});

describe("attentionCounts", () => {
  it("считает участников по причинам", () => {
    const counts = attentionCounts([
      client("А", "DEBT"),
      client("Б", "DEBT"),
      client("В", "DEBT"),
      client("Г", "NO_MEMBERSHIP"),
      client("Д", "MEMBERSHIP_ENDING"),
      client("Е", "MEMBERSHIP_ENDING"),
      client("Ж"),
    ]);
    expect(counts.total).toBe(6);
    expect(counts.byReason).toEqual({ DEBT: 3, NO_MEMBERSHIP: 1, MEMBERSHIP_ENDING: 2 });
  });

  it("все в порядке — ноль", () => {
    expect(attentionCounts([client("А")]).total).toBe(0);
  });
});

describe("filterRoster", () => {
  const roster = [client("Соколова", "DEBT"), client("Иванов"), client("Песоков")];

  it("«Требуют внимания» оставляет участников с причиной", () => {
    expect(filterRoster(roster, "attention", "").map((c) => c.name)).toEqual(["Соколова"]);
  });

  it("«В порядке» оставляет участников без причины", () => {
    expect(filterRoster(roster, "ok", "").map((c) => c.name)).toEqual(["Иванов", "Песоков"]);
  });

  it("поиск по подстроке имени без учёта регистра", () => {
    expect(filterRoster(roster, "all", "СОКО").map((c) => c.name)).toEqual(["Соколова", "Песоков"]);
  });
});

describe("rosterAction", () => {
  it("неоплаченные занятия при активном абонементе с остатком — погасить долг", () => {
    expect(rosterAction(client("А", "DEBT", { unpaidSessions: 2 }))).toBe("repay");
  });

  it("безлимитный абонемент — погасить долг", () => {
    const unlimited = membership({ sessionsTotal: null, sessionsRemaining: null });
    expect(rosterAction(client("А", "DEBT", { unpaidSessions: 1, membership: unlimited }))).toBe(
      "repay",
    );
  });

  it("долг только по балансу — открыть клиента", () => {
    expect(
      rosterAction(client("А", "DEBT", { balance: { minorUnits: -500_00, currency: "RUB" } })),
    ).toBe("openClient");
  });

  it("неактивированный абонемент или нулевой остаток — открыть клиента", () => {
    const inactive = membership({ activated: false });
    const empty = membership({ sessionsRemaining: 0 });
    expect(rosterAction(client("А", "DEBT", { unpaidSessions: 1, membership: inactive }))).toBe(
      "openClient",
    );
    expect(rosterAction(client("А", "DEBT", { unpaidSessions: 1, membership: empty }))).toBe(
      "openClient",
    );
    expect(rosterAction(client("А", "DEBT", { unpaidSessions: 1, membership: null }))).toBe(
      "openClient",
    );
  });

  it("без абонемента — оформить, заканчивается — новый, в порядке — ничего", () => {
    expect(rosterAction(client("А", "NO_MEMBERSHIP"))).toBe("issue");
    expect(rosterAction(client("А", "MEMBERSHIP_ENDING"))).toBe("issueNext");
    expect(rosterAction(client("А"))).toBeNull();
  });
});

describe("membershipGroups", () => {
  it("группирует по названию абонемента, без абонемента — последней", () => {
    const roster = [
      ...Array.from({ length: 20 }, (_, i) =>
        client(`К${String(i)}`, i < 2 ? "MEMBERSHIP_ENDING" : null),
      ),
      ...Array.from({ length: 3 }, (_, i) =>
        client(`Н${String(i)}`, "NO_MEMBERSHIP", { membership: null }),
      ),
    ];
    const groups = membershipGroups(roster);
    expect(groups.map((g) => [g.name, g.clients.length, g.ending])).toEqual([
      ["8 занятий", 20, 2],
      [null, 3, 0],
    ]);
  });
});

/** Ответ посещаемости по занятиям [sessions] и отметкам участников [rows]. */
function attendance(
  sessions: readonly SessionId[],
  rows: readonly {
    readonly clientId: ClientId;
    readonly name: string;
    readonly marks: readonly {
      readonly sessionId: SessionId;
      readonly present: boolean;
      readonly labels?: readonly AttendanceLabelSchema[];
    }[];
  }[],
): GroupAttendanceResponse {
  return {
    sessions: sessions.map((id, index) => ({
      id,
      date: date(`2026-10-${String(10 + index).padStart(2, "0")}`),
      startTime: time("10:00"),
    })),
    participants: rows.map((row) => ({
      clientId: row.clientId,
      name: row.name,
      presentCount: row.marks.filter((m) => m.present).length,
      absentCount: row.marks.filter((m) => !m.present).length,
      marks: row.marks.map((m) => ({
        sessionId: m.sessionId,
        presence: m.present ? "PRESENT" : "ABSENT",
        labels: m.labels ?? [],
      })),
    })),
  };
}

describe("attendanceSummary", () => {
  it("доля «пришёл» по последним 10 закрытым занятиям", () => {
    const sessions = Array.from({ length: 12 }, sessionId);
    const people = Array.from({ length: 34 }, () => ClientIdSchema.parse(uuidv7()));
    let presentLeft = 262;
    const rows = people.map((id) => ({
      clientId: id,
      name: id,
      marks: sessions.slice(2).map((s) => {
        const present = presentLeft > 0;
        presentLeft -= 1;
        return { sessionId: s, present };
      }),
    }));
    const summary = attendanceSummary(attendance(sessions, rows));
    expect(summary?.percent).toBe(77);
    expect(summary?.sessions).toHaveLength(10);
    expect(summary?.sessions.reduce((sum, s) => sum + s.total, 0)).toBe(340);
  });

  it("нет закрытых занятий — null", () => {
    expect(attendanceSummary(attendance([], []))).toBeNull();
  });
});

describe("journalRows", () => {
  const sick: AttendanceLabelSchema = {
    id: AttendanceLabelIdSchema.parse(uuidv7()),
    name: "болеет",
    scope: "ABSENT",
    position: 0,
    isArchived: false,
  };

  it("отличает «не участвовал», «не пришёл» и исключённых", () => {
    const s1 = sessionId();
    const newcomer = client("Новенький");
    const regular = client("Аня");
    const formerId = ClientIdSchema.parse(uuidv7());
    const rows = journalRows(
      [newcomer, regular],
      attendance(
        [s1],
        [
          {
            clientId: regular.id,
            name: "Аня",
            marks: [{ sessionId: s1, present: false, labels: [sick] }],
          },
          { clientId: formerId, name: "Ушедший", marks: [{ sessionId: s1, present: true }] },
        ],
      ),
    );
    expect(rows.map((r) => [r.name, r.former])).toEqual([
      ["Аня", false],
      ["Новенький", false],
      ["Ушедший", true],
    ]);
    expect(rows[0]?.cells).toEqual([{ kind: "absent", sessionId: s1, labels: [sick] }]);
    expect(rows[1]?.cells).toEqual([{ kind: "none" }]);
  });
});

describe("период журнала", () => {
  it("по умолчанию — последние 4 недели, сдвиг на 4 недели", () => {
    const period = defaultJournalPeriod(date("2026-11-14"));
    expect(period).toEqual({ from: date("2026-10-18"), to: date("2026-11-14") });
    expect(shiftJournalPeriod(period, -1)).toEqual({
      from: date("2026-09-20"),
      to: date("2026-10-17"),
    });
  });
});

describe("mergedScheduleRows", () => {
  it("склеивает Пн, Ср, Пт с одинаковым временем и залом", () => {
    const hallId = HallIdSchema.parse(uuidv7());
    const slot = (day: string): ScheduleSlot => ({
      dayOfWeek: DayOfWeekSchema.parse(day),
      startAt: time("18:00"),
      endAt: time("19:30"),
      hallId,
      hallName: "Зал 1",
      validity: null,
    });
    expect(mergedScheduleRows([slot("FRIDAY"), slot("MONDAY"), slot("WEDNESDAY")])).toEqual([
      {
        days: ["MONDAY", "WEDNESDAY", "FRIDAY"],
        startAt: time("18:00"),
        endAt: time("19:30"),
        hallName: "Зал 1",
      },
    ]);
  });
});
