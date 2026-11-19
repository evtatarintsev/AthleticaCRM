import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { LocalDateSchema, LocalTimeSchema, type SessionStatus } from "@/api/generated/contracts";
import { orgNowOf, sessionPhaseOf, timeOfMinutes, type OrgNow } from "./scheduleTime";

beforeAll(() => {
  vi.stubEnv("TZ", "Europe/Berlin");
});

afterAll(() => {
  vi.unstubAllEnvs();
});

/** Занятие [day] с [start] до [end] со статусом [status]. */
function session(day: string, start: string, end: string, status: SessionStatus = "SCHEDULED") {
  return {
    date: LocalDateSchema.parse(day),
    startTime: LocalTimeSchema.parse(start),
    endTime: LocalTimeSchema.parse(end),
    status,
  };
}

/** 18 ноября, [time] в поясе организации. */
function at(time: string): OrgNow {
  const [h = 0, m = 0] = time.split(":").map(Number);
  return { date: LocalDateSchema.parse("2026-11-18"), minutes: h * 60 + m };
}

describe("часовой пояс организации", () => {
  it("браузер в Берлине, организация в Москве: занятие 18:00–19:00 идёт в 18:30", () => {
    const now = orgNowOf(new Date("2026-11-18T15:30:00Z"), "Europe/Moscow");

    expect(now).toEqual({ date: "2026-11-18", minutes: 18 * 60 + 30 });
    expect(timeOfMinutes(now.minutes)).toBe("18:30");
    expect(sessionPhaseOf(session("2026-11-18", "18:00", "19:00"), now)).toEqual({ kind: "live" });
  });

  it("дата организации уже 19 ноября, пока в браузере 18-е", () => {
    expect(orgNowOf(new Date("2026-11-18T21:30:00Z"), "Europe/Moscow").date).toBe("2026-11-19");
  });
});

describe("состояние занятия", () => {
  it("начало ровно сейчас — идёт", () => {
    expect(sessionPhaseOf(session("2026-11-18", "18:00", "19:00"), at("18:00")).kind).toBe("live");
  });

  it("конец ровно сейчас — прошло", () => {
    expect(sessionPhaseOf(session("2026-11-18", "18:00", "19:00"), at("19:00")).kind).toBe("past");
  });

  it("за 60 минут до начала — скоро, за 61 — запланировано", () => {
    expect(sessionPhaseOf(session("2026-11-18", "18:00", "19:00"), at("17:00"))).toEqual({
      kind: "soon",
      minutes: 60,
    });
    expect(sessionPhaseOf(session("2026-11-18", "18:00", "19:00"), at("16:59")).kind).toBe(
      "planned",
    );
  });

  it("вчерашнее занятие прошло", () => {
    expect(sessionPhaseOf(session("2026-11-17", "23:00", "23:30"), at("00:10")).kind).toBe("past");
  });

  it("отменённое идущее — отменено", () => {
    expect(
      sessionPhaseOf(session("2026-11-18", "18:00", "19:00", "CANCELLED"), at("18:30")).kind,
    ).toBe("cancelled");
  });

  it("завтрашнее раннее занятие не считается скорым", () => {
    expect(sessionPhaseOf(session("2026-11-19", "00:10", "01:00"), at("23:50")).kind).toBe(
      "planned",
    );
  });
});
