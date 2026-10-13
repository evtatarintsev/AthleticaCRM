import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  LocalDateSchema,
  MembershipSchemaSchema,
  TaskListItemSchemaSchema,
} from "@/api/generated/contracts";
import { clientAlerts, membershipEndingSoon, nextBirthday } from "./clientCardAlerts";

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

/** Абонемент с окончанием [endDate] и остатком [remaining] из [total] занятий. */
function membership(endDate: string, remaining: number | null, total: number | null) {
  return MembershipSchemaSchema.parse({
    id: "0199a0b2-7c3e-7d2a-9f10-000000000a01",
    name: "Безлимит",
    sessionsTotal: total,
    sessionsRemaining: remaining,
    startDate: "2026-09-18",
    endDate,
    price: { minorUnits: 650000, currency: "RUB" },
    status: "ACTIVE",
  });
}

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
  it("абонемент заканчивается по сроку или по остатку занятий", () => {
    expect(membershipEndingSoon(membership("2026-10-18", null, null), today)).toBe(true);
    expect(membershipEndingSoon(membership("2026-12-31", 1, 8), today)).toBe(true);
    expect(membershipEndingSoon(membership("2026-12-31", 5, 8), today)).toBe(false);
  });

  it("ближайший день рождения переходит на следующий год и переносит 29 февраля", () => {
    expect(nextBirthday(LocalDateSchema.parse("1990-10-15"), today)).toBe("2026-10-15");
    expect(nextBirthday(LocalDateSchema.parse("1990-01-05"), today)).toBe("2027-01-05");
    expect(nextBirthday(LocalDateSchema.parse("2000-02-29"), today)).toBe("2027-03-01");
  });

  it("собирает долг, заканчивающийся абонемент, просроченные задачи и день рождения", () => {
    const alerts = clientAlerts({
      client,
      memberships: [membership("2026-10-18", null, null)],
      tasks: [overdueTask],
      today,
      now,
    });
    expect(alerts.map((alert) => alert.type)).toEqual([
      "debt",
      "subscriptionEnding",
      "overdueTasks",
      "birthday",
    ]);
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
});
