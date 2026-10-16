import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  LocalDateSchema,
  TaskListItemSchemaSchema,
} from "@/api/generated/contracts";
import { activeState, testMembership } from "@/test/memberships";
import { clientAlerts, nextBirthday } from "./clientCardAlerts";

const today = LocalDateSchema.parse("2026-10-13");
const now = new Date("2026-10-13T12:00:00Z");

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
      tasks: [overdueTask],
      today,
      now,
    });
    expect(alerts.map((alert) => alert.type)).toEqual(["debt", "overdueTasks", "birthday"]);
  });

  it("без долга и абонементов при группах — только предупреждение об абонементе", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [],
      tasks: [],
      today,
      now,
    });
    expect(alerts.map((alert) => alert.type)).toEqual(["noActiveSubscription"]);
  });

  it("будущий абонемент считается действующим — предупреждения об абонементе нет", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [notStarted],
      tasks: [],
      today,
      now,
    });
    expect(alerts).toEqual([]);
  });

  it("только истёкшие абонементы при группах — предупреждение об абонементе", () => {
    const alerts = clientAlerts({
      client: { ...client, birthday: null, balance: { minorUnits: 0, currency: "RUB" } },
      memberships: [closePending],
      tasks: [],
      today,
      now,
    });
    expect(alerts.map((alert) => alert.type)).toEqual(["noActiveSubscription"]);
  });
});
