import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BarsChart, type BarPoint } from "./BarsChart";

const data: readonly BarPoint[] = [
  { key: "2026-11-01", label: "1", value: 12000, tooltip: "1 ноября: 12 000 ₽" },
  { key: "2026-11-02", label: "2", value: -800, tooltip: "2 ноября: −800 ₽" },
];

describe("BarsChart", () => {
  it("пересказывает график в aria-label", () => {
    render(<BarsChart data={data} color={() => "red"} ariaLabel="Выручка по дням" width={400} />);
    expect(screen.getByRole("img", { name: "Выручка по дням" })).toBeInTheDocument();
  });

  it("показывает готовую строку подсказки для активного столбца", () => {
    const { container } = render(
      <BarsChart data={data} color={() => "red"} ariaLabel="Выручка по дням" width={400} />,
    );
    const surface = container.querySelector(".recharts-wrapper svg");
    expect(surface).not.toBeNull();
    if (surface !== null) {
      fireEvent.focus(surface);
      fireEvent.keyDown(surface, { key: "ArrowRight" });
    }
    expect(screen.getByText("2 ноября: −800 ₽")).toBeInTheDocument();
  });
});
