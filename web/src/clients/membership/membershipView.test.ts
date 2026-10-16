import { describe, expect, it } from "vitest";
import { LocalDateSchema, MembershipSchemaSchema } from "@/api/generated/contracts";
import { membershipView } from "./membershipView";

const today = LocalDateSchema.parse("2026-10-15");

/** Абонемент с периодом [startDate]–[endDate] и остатком [remaining] из [total] занятий. */
function membership(
  startDate: string,
  endDate: string,
  remaining: number | null,
  total: number | null,
) {
  return MembershipSchemaSchema.parse({
    id: "0199a0b2-7c3e-7d2a-9f10-000000000a01",
    name: "1111",
    sessionsTotal: total,
    sessionsRemaining: remaining,
    startDate,
    endDate,
    price: { minorUnits: 400000, currency: "RUB" },
    status: "ACTIVE",
  });
}

describe("состояние абонемента", () => {
  it("последний день абонемента — ещё не истёк, вчерашний — истёк", () => {
    expect(membershipView(membership("2026-10-01", "2026-10-15", 5, 8), today).phase).toBe(
      "ending",
    );
    expect(membershipView(membership("2026-10-01", "2026-10-14", 5, 8), today).phase).toBe(
      "expired",
    );
  });

  it("заканчивается по сроку ровно за 7 дней, за 8 — действует", () => {
    const seven = membershipView(membership("2026-10-01", "2026-10-22", 12, 12), today);
    expect(seven).toEqual({ phase: "ending", endingByDate: true, endingBySessions: false });
    expect(membershipView(membership("2026-10-01", "2026-10-23", 12, 12), today).phase).toBe(
      "active",
    );
  });

  it("заканчивается по занятиям при остатке 1 и 0, при 2 — действует", () => {
    expect(membershipView(membership("2026-10-01", "2026-12-31", 1, 8), today)).toEqual({
      phase: "ending",
      endingByDate: false,
      endingBySessions: true,
    });
    expect(membershipView(membership("2026-10-01", "2026-12-31", 0, 8), today).phase).toBe(
      "ending",
    );
    expect(membershipView(membership("2026-10-01", "2026-12-31", 2, 8), today).phase).toBe(
      "active",
    );
  });

  it("безлимит не заканчивается по занятиям", () => {
    expect(membershipView(membership("2026-10-01", "2026-12-31", null, null), today).phase).toBe(
      "active",
    );
  });

  it("обе причины сразу", () => {
    expect(membershipView(membership("2026-10-01", "2026-10-18", 1, 8), today)).toEqual({
      phase: "ending",
      endingByDate: true,
      endingBySessions: true,
    });
  });

  it("абонемент с будущей датой начала не начался, даже если скоро кончится", () => {
    expect(membershipView(membership("2026-10-16", "2026-10-18", 1, 8), today)).toEqual({
      phase: "upcoming",
      endingByDate: false,
      endingBySessions: false,
    });
    expect(membershipView(membership("2026-10-15", "2026-12-31", 8, 8), today).phase).toBe(
      "active",
    );
  });
});
