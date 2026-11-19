import { describe, expect, it } from "vitest";
import { HallIdSchema, LocalDateSchema } from "@/api/generated/contracts";
import {
  dateForView,
  scheduleFiltersOf,
  ScheduleSearchSchema,
  scheduleSearchOf,
  scheduleViewOf,
  selectedDateOf,
  shiftedDate,
} from "./scheduleWeek";

const date = (value: string) => LocalDateSchema.parse(value);
const hall = HallIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000603");
const noFilters = scheduleFiltersOf({});

describe("адрес расписания", () => {
  it("ссылка на явно выбранный сегодняшний день открывает его и завтра", () => {
    const today = date("2026-11-18");
    const saved = scheduleSearchOf({ view: "day", date: today }, noFilters);

    expect(saved).toEqual({ view: "day", date: today });
    const tomorrow = ScheduleSearchSchema.parse(saved);
    expect(selectedDateOf(tomorrow, date("2026-11-19"))).toBe(today);
  });

  it("без даты выбран сегодняшний день организации", () => {
    expect(selectedDateOf({}, date("2026-11-19"))).toBe("2026-11-19");
  });

  it("невалидные режим и дата отбрасываются, фильтры остаются", () => {
    const search = ScheduleSearchSchema.parse({
      view: "month",
      date: "18.11.2026",
      hallIds: [hall],
    });

    expect(search).toEqual({ view: undefined, date: undefined, hallIds: [hall] });
  });

  it("сохраняет фильтры и опускает пустые", () => {
    expect(
      scheduleSearchOf(
        { view: "week", date: date("2026-11-18") },
        { ...noFilters, hallIds: [hall] },
      ),
    ).toEqual({ view: "week", date: "2026-11-18", hallIds: [hall] });
  });

  it("без режима в адресе — неделя на широком экране и день на узком", () => {
    expect(scheduleViewOf({}, true)).toBe("week");
    expect(scheduleViewOf({}, false)).toBe("day");
    expect(scheduleViewOf({ view: "day" }, true)).toBe("day");
  });
});

describe("навигация", () => {
  it("день сдвигается через границу недели", () => {
    expect(shiftedDate("day", date("2026-11-16"), -1)).toBe("2026-11-15");
    expect(shiftedDate("day", date("2026-11-22"), 1)).toBe("2026-11-23");
  });

  it("неделя сдвигается на семь дней от понедельника", () => {
    expect(shiftedDate("week", date("2026-11-18"), 1)).toBe("2026-11-23");
    expect(shiftedDate("week", date("2026-11-18"), -1)).toBe("2026-11-09");
  });

  it("неделя → день: сегодня, если оно в неделе, иначе понедельник", () => {
    const today = date("2026-11-18");
    expect(dateForView("week", "day", date("2026-11-20"), today)).toBe(today);
    expect(dateForView("week", "day", date("2026-11-25"), today)).toBe("2026-11-23");
  });

  it("день → неделя: неделя выбранного дня", () => {
    expect(dateForView("day", "week", date("2026-11-25"), date("2026-11-18"))).toBe("2026-11-25");
  });
});
