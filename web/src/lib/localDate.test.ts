import { describe, expect, it } from "vitest";
import { LocalDateSchema } from "@/api/generated/contracts";
import { addDays, dayOfWeekOf, mondayOf, weekDates } from "./localDate";

const date = (value: string) => LocalDateSchema.parse(value);

describe("addDays", () => {
  it("сдвигает дату вперёд и назад без сдвига часового пояса", () => {
    expect(addDays(date("2025-01-31"), 1)).toBe("2025-02-01");
    expect(addDays(date("2025-02-01"), -1)).toBe("2025-01-31");
  });
});

describe("dayOfWeekOf", () => {
  it("определяет день недели даты", () => {
    expect(dayOfWeekOf(date("2025-06-02"))).toBe("MONDAY");
    expect(dayOfWeekOf(date("2025-06-08"))).toBe("SUNDAY");
  });
});

describe("mondayOf", () => {
  it("находит понедельник недели для любого дня, включая воскресенье", () => {
    expect(mondayOf(date("2025-06-04"))).toBe("2025-06-02");
    expect(mondayOf(date("2025-06-08"))).toBe("2025-06-02");
    expect(mondayOf(date("2025-06-02"))).toBe("2025-06-02");
  });
});

describe("weekDates", () => {
  it("возвращает семь дат недели от понедельника", () => {
    expect(weekDates(date("2025-06-02"))).toEqual([
      "2025-06-02",
      "2025-06-03",
      "2025-06-04",
      "2025-06-05",
      "2025-06-06",
      "2025-06-07",
      "2025-06-08",
    ]);
  });
});
