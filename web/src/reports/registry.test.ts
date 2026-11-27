import { describe, expect, it } from "vitest";
import { visibleGroups, visibleReports } from "./registry";

describe("visibleReports", () => {
  it("без права на отчёты не показывает ничего", () => {
    expect(visibleReports(["CAN_VIEW_FINANCE", "CAN_VIEW_CLIENT_BALANCE"])).toEqual([]);
  });

  it("без права на финансы нет выручки, доходов и расходов и филиалов", () => {
    const ids = visibleReports(["CAN_VIEW_REPORTS", "CAN_VIEW_CLIENT_BALANCE"]).map((r) => r.id);
    expect(ids).not.toContain("revenue");
    expect(ids).not.toContain("pnl");
    expect(ids).not.toContain("branches");
    expect(ids).toContain("debts");
  });

  it("без права на баланс клиента нет долгов", () => {
    const ids = visibleReports(["CAN_VIEW_REPORTS", "CAN_VIEW_FINANCE"]).map((r) => r.id);
    expect(ids).not.toContain("debts");
    expect(ids).toContain("revenue");
  });

  it("со всеми правами показывает все девять отчётов", () => {
    expect(
      visibleReports(["CAN_VIEW_REPORTS", "CAN_VIEW_FINANCE", "CAN_VIEW_CLIENT_BALANCE"]),
    ).toHaveLength(9);
  });
});

describe("visibleGroups", () => {
  it("скрывает группу без карточек", () => {
    expect(visibleGroups(["CAN_VIEW_REPORTS"]).map((g) => g.group)).toEqual([
      "clients",
      "team",
      "compare",
    ]);
  });
});
