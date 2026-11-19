import { describe, expect, it } from "vitest";
import { ScheduleSessionSchemaSchema } from "@/api/generated/contracts";
import { activeState, testMembership } from "@/test/memberships";
import { currentMemberships } from "./clientOverview";
import { upcomingSessions } from "./clientCardQueries";

describe("client overview", () => {
  it("sorts started memberships by end date before future ones and excludes expired", () => {
    const future = testMembership({
      id: "0199a0b2-7c3e-7d2a-9f10-000000000a02",
      state: activeState("2026-11-20", "2026-12-20", { termPosition: "NOT_STARTED" }),
    });
    const started = testMembership({ state: activeState("2026-10-20", "2026-11-20") });
    const expired = testMembership({
      state: activeState("2026-09-01", "2026-10-01", { termPosition: "EXPIRED" }),
    });
    expect(currentMemberships([future, expired, started])).toEqual([started, future]);
  });
  it("excludes cancelled, started and exactly current sessions", () => {
    const session = (time: string, status = "SCHEDULED") =>
      ScheduleSessionSchemaSchema.parse({
        id: "0199a0b2-7c3e-7d2a-9f10-000000000001",
        date: "2026-11-15",
        startTime: time,
        endTime: "19:00",
        group: { id: "0199a0b2-7c3e-7d2a-9f10-000000000901", name: "Group" },
        hall: { id: "0199a0b2-7c3e-7d2a-9f10-000000000902", name: "Hall" },
        coaches: [],
        disciplines: [],
        colorKey: "GREEN",
        coachesOverridden: false,
        rescheduledFrom: null,
        isManual: false,
        status,
      });
    const future = session("18:00");
    const past = [session("10:00"), session("15:00"), session("16:00", "CANCELLED")];
    const now = new Date(2026, 10, 15, 15);
    expect(upcomingSessions([...past, future], now)).toEqual([future]);
    expect(upcomingSessions(past, now)).toEqual([]);
  });
});
