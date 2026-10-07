import { describe, expect, it } from "vitest";
import { LocalDateSchema } from "@/api/generated/contracts";
import {
  dayPosition,
  defaultSessionsWindow,
  shiftSessionsWindow,
  WINDOW_LENGTH_DAYS,
} from "./groupSessionsWindow";

const date = (value: string) => LocalDateSchema.parse(value);

describe("окно блока занятий группы", () => {
  it("по умолчанию — от 14 дней назад до 28 дней вперёд", () => {
    expect(defaultSessionsWindow(date("2026-10-07"))).toEqual({
      from: date("2026-09-23"),
      to: date("2026-11-04"),
    });
    expect(WINDOW_LENGTH_DAYS).toBe(43);
  });

  it("сдвигается на свою длину назад и вперёд без пропусков и наложений", () => {
    const window = defaultSessionsWindow(date("2026-10-07"));

    const back = shiftSessionsWindow(window, -1);
    const forward = shiftSessionsWindow(window, 1);

    expect(back).toEqual({ from: date("2026-08-11"), to: date("2026-09-22") });
    expect(forward).toEqual({ from: date("2026-11-05"), to: date("2026-12-17") });
  });

  it("относит дату к прошлому, сегодняшнему дню или будущему", () => {
    const today = date("2026-10-07");

    expect(dayPosition(date("2026-10-06"), today)).toBe("past");
    expect(dayPosition(today, today)).toBe("today");
    expect(dayPosition(date("2026-10-08"), today)).toBe("future");
  });
});
