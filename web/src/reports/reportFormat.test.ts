import { describe, expect, it } from "vitest";
import type { ReportValue } from "@/api/generated/contracts";
import { reportChange } from "./reportFormat";

/** Сумма в рублях из минорных единиц [minorUnits]. */
function rub(minorUnits: number): ReportValue {
  return { type: "amount", amount: { minorUnits, currency: "RUB" } };
}

describe("reportChange", () => {
  it("рост суммы — относительное изменение", () => {
    expect(reportChange(rub(150), rub(100))).toBe(0.5);
  });

  it("рост от отрицательной суммы остаётся ростом", () => {
    expect(reportChange(rub(50), rub(-100))).toBe(1.5);
    expect(reportChange(rub(-200), rub(-100))).toBe(-1);
  });

  it("сравнивать не с чем", () => {
    expect(reportChange(rub(100), null)).toBeNull();
    expect(reportChange(rub(100), rub(0))).toBeNull();
  });
});
