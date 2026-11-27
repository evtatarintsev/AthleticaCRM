import { describe, expect, it } from "vitest";
import { defaultReportSearch, reportFilter, ReportSearchSchema } from "./reportSearch";

describe("ReportSearchSchema", () => {
  it("неверные параметры заменяются значениями по умолчанию", () => {
    expect(
      ReportSearchSchema.parse({
        period: "year",
        compare: "x",
        view: "chart",
        branch: "nope",
        from: "2026-13-01",
      }),
    ).toEqual(defaultReportSearch);
    expect(defaultReportSearch).toMatchObject({ period: "month", view: "cards" });
  });

  it("свой период с началом позже конца не применяется", () => {
    const search = ReportSearchSchema.parse({
      period: "custom",
      from: "2026-11-20",
      to: "2026-11-10",
    });
    expect(search.period).toBe("month");
    expect(search.from).toBeUndefined();
  });

  it("свой период длиннее 366 дней не применяется", () => {
    expect(
      ReportSearchSchema.parse({ period: "custom", from: "2025-01-01", to: "2026-01-01" }).period,
    ).toBe("custom");
    expect(
      ReportSearchSchema.parse({ period: "custom", from: "2025-01-01", to: "2026-01-02" }).period,
    ).toBe("month");
  });

  it("свой период с датами и режимом сравнения сохраняется и уходит в запрос", () => {
    const search = ReportSearchSchema.parse({
      period: "custom",
      from: "2026-11-01",
      to: "2026-11-10",
      compare: "month-before",
      view: "table",
    });
    expect(reportFilter(search)).toEqual({
      period: "custom",
      from: "2026-11-01",
      to: "2026-11-10",
      compare: "month-before",
      branchId: null,
    });
    expect(search.view).toBe("table");
  });

  it("даты без своего периода отбрасываются", () => {
    expect(ReportSearchSchema.parse({ period: "30d", from: "2026-11-01" }).from).toBeUndefined();
  });
});
