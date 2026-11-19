import { describe, expect, it } from "vitest";
import { LocalDateSchema, LocalTimeSchema } from "@/api/generated/contracts";
import { isBirthdayToday, journalAgeDays, sessionStates, upcomingAge } from "./homeTime";

const date = (value: string) => LocalDateSchema.parse(value);
const today = date("2026-11-18");
const session = (start: string, end: string) => ({
  startTime: LocalTimeSchema.parse(start),
  endTime: LocalTimeSchema.parse(end),
});
const sessions = [
  session("16:00", "17:00"),
  session("17:00", "18:30"),
  session("18:00", "19:30"),
  session("19:30", "21:00"),
];

describe("время главной", () => {
  it("вечер буднего дня и точная граница окончания", () => {
    expect(sessionStates(sessions, today, today, new Date(2026, 10, 18, 17, 40))).toEqual([
      "past",
      "live",
      "next",
      "none",
    ]);
    expect(sessionStates(sessions, today, today, new Date(2026, 10, 18, 18, 30))).toEqual([
      "past",
      "past",
      "live",
      "next",
    ]);
  });
  it("подсвечивает все параллельные занятия и ближайшие начала", () => {
    expect(
      sessionStates(
        [
          session("18:00", "19:00"),
          session("17:00", "18:30"),
          session("17:30", "18:00"),
          session("18:00", "19:30"),
        ],
        today,
        today,
        new Date(2026, 10, 18, 17, 40),
      ),
    ).toEqual(["next", "live", "live", "next"]);
  });
  it("все занятия прошли, пустая лента, дата ответа не совпадает", () => {
    const now = new Date(2026, 10, 18, 21, 30);
    expect(sessionStates(sessions, today, today, now)).toEqual(["past", "past", "past", "past"]);
    expect(sessionStates([], today, today, now)).toEqual([]);
    expect(sessionStates(sessions, date("2026-11-17"), today, now)).toEqual([
      "none",
      "none",
      "none",
      "none",
    ]);
  });
  it("журналы разного возраста, граница года и переход летнего времени", () => {
    expect(
      ["2026-11-18", "2026-11-17", "2026-11-14"].map((at) => journalAgeDays(date(at), today)),
    ).toEqual([0, 1, 4]);
    expect(journalAgeDays(date("2025-12-31"), date("2026-01-01"))).toBe(1);
    expect(journalAgeDays(date("2026-03-28"), date("2026-03-30"))).toBe(2);
  });
  it("возраст в ближайший день рождения, в том числе после Нового года", () => {
    expect(upcomingAge(date("1995-11-18"), today)).toBe(31);
    expect(upcomingAge(date("1995-11-20"), today)).toBe(31);
    expect(upcomingAge(date("2000-01-02"), date("2026-12-30"))).toBe(27);
  });

  it("день рождения сегодня, другой месяц и 29 февраля", () => {
    expect(isBirthdayToday(date("1995-11-18"), today)).toBe(true);
    expect(isBirthdayToday(date("1995-10-18"), today)).toBe(false);
    expect(isBirthdayToday(date("2000-02-29"), date("2024-02-29"))).toBe(true);
    expect(isBirthdayToday(date("2000-02-29"), date("2026-02-28"))).toBe(false);
    expect(isBirthdayToday(date("2000-02-29"), date("2026-03-01"))).toBe(false);
  });
});
