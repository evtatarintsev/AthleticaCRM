import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  UnpaidSessionSchemaSchema,
  LocalDateSchema,
  TaskListItemSchemaSchema,
} from "@/api/generated/contracts";
import { activeState, testMembership } from "@/test/memberships";
import { clientAlerts, nextBirthday } from "./clientCardAlerts";

const today = LocalDateSchema.parse("2026-10-13");

const client = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  name: "Алиса Иванова",
  avatarId: null,
  birthday: "1990-10-15",
  gender: "FEMALE",
  groups: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000901", name: "Утренняя группа" }],
  balance: { minorUnits: -120000, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

/** Активный безлимит в сроке. */
const inTerm = testMembership({
  sessionsTotal: null,
  sessionsRemaining: null,
  state: activeState("2026-09-18", "2026-10-18", { endingSoon: ["BY_DATE"] }),
});

/** Абонемент, который ещё не начался. */
const notStarted = testMembership({
  state: activeState("2026-10-20", "2026-11-30", { termPosition: "NOT_STARTED" }),
});

/** Абонемент с вышедшим сроком и без занятий — «к закрытию». */
const closePending = testMembership({
  sessionsRemaining: 0,
  state: activeState("2026-09-18", "2026-10-01", { termPosition: "EXPIRED", closePending: true }),
});

const overdueTask = TaskListItemSchemaSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000b01",
  title: "Позвонить",
  assigneeId: null,
  assigneeName: null,
  clientId: client.id,
  clientName: client.name,
  status: "PENDING",
  dueDate: "2026-10-10T09:00:00Z",
  dueDateEnd: null,
});

describe("предупреждения карточки клиента", () => {
  it("ближайший день рождения переходит на следующий год и переносит 29 февраля", () => {
    expect(nextBirthday(LocalDateSchema.parse("1990-10-15"), today)).toBe("2026-10-15");
    expect(nextBirthday(LocalDateSchema.parse("1990-01-05"), today)).toBe("2027-01-05");
    expect(nextBirthday(LocalDateSchema.parse("2000-02-29"), today)).toBe("2027-03-01");
  });

  it("собирает долг, просроченные задачи и день рождения; об окончании абонемента молчит", () => {
    const alerts = clientAlerts({
      client,
      memberships: [inTerm],
      overdue: { tasks: [overdueTask], total: 1 },
      unpaid: [],
      today,
    });
    expect(alerts.map((alert) => alert.type)).toEqual(["debt", "overdueTasks", "birthday"]);
  });

  it("без долга и абонементов при группах — только предупреждение об абонементе", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [],
      overdue: undefined,
      unpaid: [],
      today,
    });
    expect(alerts.map((alert) => alert.type)).toEqual(["noActiveSubscription"]);
  });

  it("пока неоплаченные посещения не загружены, предупреждения об абонементе нет", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [],
      overdue: undefined,
      unpaid: undefined,
      today,
    });
    expect(alerts).toEqual([]);
  });

  it("будущий абонемент считается действующим — предупреждения об абонементе нет", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [notStarted],
      overdue: undefined,
      unpaid: [],
      today,
    });
    expect(alerts).toEqual([]);
  });

  it("только истёкшие абонементы при группах — предупреждение об абонементе", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [closePending],
      overdue: undefined,
      unpaid: [],
      today,
    });
    expect(alerts.map((alert) => alert.type)).toEqual(["noActiveSubscription"]);
  });
});

it("partial repayment respects groups and remaining sessions; unlimited covers all", () => {
  const sessions = [1, 2, 3].map((n) =>
    UnpaidSessionSchemaSchema.parse({
      sessionId: `0199a0b2-7c3e-7d2a-9f10-00000000000${String(n)}`,
      groupId: n === 3 ? "0199a0b2-7c3e-7d2a-9f10-000000000999" : client.groups[0]?.id,
      groupName: "Group",
      date: today,
      startTime: "10:00",
    }),
  );
  const source = { client, unpaid: sessions, overdue: undefined, today };
  expect(
    clientAlerts({
      ...source,
      memberships: [{ ...inTerm, sessionsRemaining: 1, sessionsTotal: 8 }],
    })[0],
  ).toMatchObject({ type: "debt", repayLimit: 1 });
  expect(clientAlerts({ ...source, memberships: [inTerm] })[0]).toMatchObject({
    type: "debt",
    repayLimit: 2,
  });
  expect(
    clientAlerts({
      ...source,
      memberships: [
        {
          ...inTerm,
          groups: [
            ...inTerm.groups,
            { id: UnpaidSessionSchemaSchema.parse(sessions[2]).groupId, name: "Other" },
          ],
        },
      ],
    })[0],
  ).toMatchObject({ type: "debt", repayLimit: 3 });
  expect(clientAlerts({ ...source, unpaid: [], memberships: [inTerm] })[0]).toMatchObject({
    type: "debt",
    membership: null,
    repayLimit: 0,
  });
});
