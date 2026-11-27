import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ReportBetter, ReportValue } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { ru } from "@/i18n/ru";
import { renderPage } from "@/test/render";
import { ReportCard, type ReportCardState } from "./ReportCard";
import { reportChangeView } from "./reportFormat";
import { defaultReportSearch } from "./reportSearch";
import { reportById } from "./registry";

const debts = reportById("debts");

/** Карточка «Долгов» со значением [value] против [base] и правилом [better]. */
function DebtsCard({
  value,
  base,
  better,
}: {
  readonly value: ReportValue;
  readonly base: ReportValue | null;
  readonly better: ReportBetter;
}) {
  const i18n = useI18n();
  if (debts === undefined) {
    return null;
  }
  const state: ReportCardState = {
    status: "ready",
    value: "96 300 ₽",
    change: reportChangeView(i18n, value, base, better),
    spark: [1, 2, 3],
    warning: { text: "14 клиентов должны", level: "warning" },
  };
  return <ReportCard report={debts} search={defaultReportSearch} state={state} />;
}

const rub = (minorUnits: number): ReportValue => ({
  type: "amount",
  amount: { minorUnits, currency: "RUB" },
});

describe("ReportCard", () => {
  it("рост при better: down — красная плашка", async () => {
    renderPage(<DebtsCard value={rub(110_00)} base={rub(100_00)} better="down" />);
    const badge = await screen.findByText("▲ 10%");
    expect(badge).toHaveAttribute("data-tone", "bad");
  });

  it("снижение при better: down — зелёная плашка", async () => {
    renderPage(<DebtsCard value={rub(90_00)} base={rub(100_00)} better="down" />);
    expect(await screen.findByText("▼ 10%")).toHaveAttribute("data-tone", "good");
  });

  it("доля меняется в процентных пунктах", async () => {
    renderPage(
      <DebtsCard
        value={{ type: "percent", share: 0.833 }}
        base={{ type: "percent", share: 0.821 }}
        better="up"
      />,
    );
    expect(await screen.findByText("▲ 1,2 п.п.")).toHaveAttribute("data-tone", "good");
  });

  it("без прошлого периода изменения нет; карточка ведёт на отчёт с фильтрами", async () => {
    renderPage(<DebtsCard value={rub(100)} base={null} better="down" />);
    const link = await screen.findByRole("link", { name: /Долги клиентов/ });
    expect(link).toHaveAttribute("href", expect.stringContaining("/reports/debts?"));
    expect(link).toHaveAttribute("href", expect.stringContaining("period=month"));
    expect(screen.queryByText(/[▲▼]/)).not.toBeInTheDocument();
    expect(screen.getByText("14 клиентов должны")).toBeInTheDocument();
  });

  it("ошибка загрузки — «не удалось загрузить» и повтор", async () => {
    const onRetry = vi.fn<() => void>();
    if (debts === undefined) {
      throw new Error("нет отчёта debts");
    }
    renderPage(
      <ReportCard
        report={debts}
        search={defaultReportSearch}
        state={{ status: "error", onRetry }}
      />,
    );
    expect(await screen.findByText(ru["reports.card.failed"])).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: ru["reports.card.retry"] }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
