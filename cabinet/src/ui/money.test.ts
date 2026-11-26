import { describe, expect, it } from "vitest";
import { formatMoney } from "./money";

describe("formatMoney", () => {
  it("рубли с копейками и знаком", () => {
    expect(formatMoney({ minorUnits: 50_000, currency: "RUB" }, "ru", true)).toBe("+500,00 ₽");
    expect(formatMoney({ minorUnits: -120_050, currency: "RUB" }, "ru")).toBe("-1 200,50 ₽");
  });
});
