import { describe, expect, it } from "vitest";
import { activeState, testMembership } from "@/test/memberships";
import { isCurrentMembership, membershipView } from "./membershipView";

describe("вид абонемента по состоянию с сервера", () => {
  it("неактивированный — без дат, действующий", () => {
    const view = membershipView(
      testMembership({ state: { type: "inactive", activateOn: "2026-10-20" } }),
    );
    expect(view.phase).toBe("inactive");
    expect(view.startDate).toBeNull();
    expect(isCurrentMembership(view)).toBe(true);
  });

  it("причины «заканчивается» берутся с сервера", () => {
    const view = membershipView(
      testMembership({
        sessionsRemaining: 1,
        state: activeState("2026-10-01", "2026-10-31", { endingSoon: ["BY_SESSIONS"] }),
      }),
    );
    expect(view.phase).toBe("ending");
    expect(view.endingBySessions).toBe(true);
    expect(view.endingByDate).toBe(false);
  });

  it("без причин — активен", () => {
    const view = membershipView(testMembership({ state: activeState("2026-10-01", "2026-10-31") }));
    expect(view.phase).toBe("active");
  });

  it("не начался", () => {
    const view = membershipView(
      testMembership({
        state: activeState("2026-11-01", "2026-11-30", { termPosition: "NOT_STARTED" }),
      }),
    );
    expect(view.phase).toBe("upcoming");
    expect(isCurrentMembership(view)).toBe(true);
  });

  it("к закрытию — с причинами, не действующий", () => {
    const view = membershipView(
      testMembership({
        sessionsRemaining: 0,
        state: activeState("2026-09-01", "2026-09-30", {
          termPosition: "EXPIRED",
          closePending: true,
        }),
      }),
    );
    expect(view.phase).toBe("closePending");
    expect(view.expired).toBe(true);
    expect(view.exhausted).toBe(true);
    expect(isCurrentMembership(view)).toBe(false);
  });

  it("закрытый и отозванный — не действующие", () => {
    const closed = membershipView(
      testMembership({
        state: {
          type: "closed",
          startDate: "2026-09-01",
          endDate: "2026-09-30",
          closedOn: "2026-10-01",
          reason: "MANUAL",
        },
      }),
    );
    const revoked = membershipView(
      testMembership({ state: { type: "revoked", startDate: null, endDate: null } }),
    );
    expect(closed.phase).toBe("closed");
    expect(revoked.phase).toBe("revoked");
    expect(isCurrentMembership(closed)).toBe(false);
    expect(isCurrentMembership(revoked)).toBe(false);
  });
});
