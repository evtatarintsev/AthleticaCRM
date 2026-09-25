import { describe, expect, it } from "vitest";
import { moneyEditText, parseMoney } from "./money";

describe("parseMoney", () => {
  it("переводит текст в минорные единицы без потери точности", () => {
    expect(parseMoney("1200", "RUB")).toEqual({ minorUnits: 120000, currency: "RUB" });
    expect(parseMoney("1 200,5", "RUB")).toEqual({ minorUnits: 120050, currency: "RUB" });
    expect(parseMoney("0.29", "USD")).toEqual({ minorUnits: 29, currency: "USD" });
    expect(parseMoney("7.", "EUR")).toEqual({ minorUnits: 700, currency: "EUR" });
  });

  it("отклоняет некорректные суммы", () => {
    ["", "abc", "-5", "1.234", "1,2,3", "99999999999999999"].forEach((text) => {
      expect(parseMoney(text, "RUB")).toBeNull();
    });
  });
});

describe("moneyEditText", () => {
  it("опускает нулевую дробь и группировку", () => {
    expect(moneyEditText({ minorUnits: 120000, currency: "RUB" })).toBe("1200");
    expect(moneyEditText({ minorUnits: 120050, currency: "RUB" })).toBe("1200.50");
    expect(moneyEditText({ minorUnits: 5, currency: "USD" })).toBe("0.05");
  });

  it("обратима с parseMoney", () => {
    [0, 1, 99, 100, 123456].forEach((minorUnits) => {
      const money = { minorUnits, currency: "RUB" as const };
      expect(parseMoney(moneyEditText(money), "RUB")).toEqual(money);
    });
  });
});
