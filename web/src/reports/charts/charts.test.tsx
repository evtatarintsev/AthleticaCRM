import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CompareBars } from "./CompareBars";
import { DonutChart } from "./DonutChart";
import { ShareBars } from "./ShareBars";
import { Sparkline } from "./Sparkline";

describe("Sparkline", () => {
  it("скрыт от экранных читалок", () => {
    render(<Sparkline values={[1, 2, 3]} group="money" />);
    expect(screen.getByTestId("sparkline")).toHaveAttribute("aria-hidden", "true");
  });
});

describe("CompareBars", () => {
  it("подписывает легенду и пересказывает график", () => {
    render(
      <CompareBars
        data={[{ key: "a", label: "Йога", value: 10, base: 8, tooltip: "Йога: 10 / 8" }]}
        group="compare"
        valueLabel="октябрь"
        baseLabel="сентябрь"
        ariaLabel="Выручка по направлениям"
        width={400}
      />,
    );
    expect(screen.getByRole("img", { name: "Выручка по направлениям" })).toBeInTheDocument();
    const legend = screen.getByRole("list");
    expect(within(legend).getByText("октябрь")).toBeInTheDocument();
    expect(within(legend).getByText("сентябрь")).toBeInTheDocument();
  });
});

describe("DonutChart", () => {
  it("показывает число в центре и легенду со значениями", () => {
    render(
      <DonutChart
        data={[
          { key: "cash", label: "Наличные", value: 30, valueLabel: "30 000 ₽" },
          { key: "card", label: "Карта", value: 70, valueLabel: "70 000 ₽" },
        ]}
        group="money"
        center="100 000 ₽"
        centerCaption="всего"
        ariaLabel="В какие кассы пришли деньги"
      />,
    );
    expect(screen.getByRole("img", { name: "В какие кассы пришли деньги" })).toBeInTheDocument();
    expect(screen.getByText("100 000 ₽")).toBeInTheDocument();
    expect(screen.getByText("всего")).toBeInTheDocument();
    const items = within(screen.getByRole("list")).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual(["Наличные30 000 ₽", "Карта70 000 ₽"]);
  });
});

describe("ShareBars", () => {
  it("рисует полосу шириной по доле и подписывает значение", () => {
    render(
      <ShareBars
        items={[{ key: "a", label: "Утро", share: 0.25, valueLabel: "25%" }]}
        group="team"
      />,
    );
    expect(screen.getByText("Утро")).toBeInTheDocument();
    expect(screen.getByText("25%")).toBeInTheDocument();
    const bar = screen.getByRole("listitem").querySelector("[style*='width']");
    expect(bar).toHaveStyle({ width: "25.0%" });
  });
});
