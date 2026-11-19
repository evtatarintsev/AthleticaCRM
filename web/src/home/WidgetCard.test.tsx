import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { createApiClient } from "@/api/client";
import { DashboardWidgetDebtorsSchema } from "@/api/generated/contracts";
import { I18nProvider } from "@/i18n/I18nProvider";
import { createQueryClient } from "@/query/queries";
import { WidgetCard } from "./WidgetCard";

const widget = DashboardWidgetDebtorsSchema.parse({
  type: "debtors",
  id: "0199a0b2-7c3e-7d2a-9f10-000000000901",
  title: "Долги",
  limit: 5,
});
function card(state: "pending" | "error" | "empty" | "ready") {
  const api = createApiClient({
    fetch: () => Promise.reject(new Error("Unexpected API call")),
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  return render(
    <I18nProvider initialLocale="ru">
      <QueryClientProvider client={createQueryClient()}>
        <WidgetCard
          api={api}
          settings={{ widgets: [widget], layout: [widget.id] }}
          widget={widget}
          title="Долги"
          count={7}
          state={state}
          emptyKey="home.debtorsEmpty"
          errorKey="home.debtorsError"
          footer={<p>Футер</p>}
        >
          <p>Строки</p>
        </WidgetCard>
      </QueryClientProvider>
    </I18nProvider>,
  );
}

describe("каркас виджета", () => {
  it("пользовательское название сохраняет кошелёк, цвет и доступную кнопку настроек", async () => {
    const { container } = card("ready");
    expect(screen.getByRole("heading", { name: "Долги" })).toBeVisible();
    expect(container.querySelector(".lucide-wallet")).toHaveAttribute("aria-hidden", "true");
    expect(container.querySelector(".lucide-wallet")?.parentElement).toHaveClass("text-red-700");
    expect(screen.getByText("7")).toBeVisible();
    expect(screen.getByText("Строки")).toBeVisible();
    expect(screen.getByText("Футер")).toBeVisible();
    const button = screen.getByRole("button", { name: "Настройки: Долги" });
    await userEvent.setup().tab();
    expect(button).toHaveFocus();
    expect(button.className).toContain("focus-visible:ring");
    for (const icon of container.querySelectorAll("svg"))
      expect(icon).toHaveAttribute("aria-hidden", "true");
  });
  it.each(["pending", "error", "empty"] as const)("скрывает счётчик, тело и футер: %s", (state) => {
    const { container } = card(state);
    expect(screen.queryByText("7")).not.toBeInTheDocument();
    expect(screen.queryByText("Строки")).not.toBeInTheDocument();
    expect(screen.queryByText("Футер")).not.toBeInTheDocument();
    expect(container.querySelector(".lucide-check") !== null).toBe(state === "empty");
    if (state === "empty") {
      expect(screen.getByText("Должников нет")).toBeVisible();
      expect(container.querySelector(".lucide-check")).toHaveAttribute("aria-hidden", "true");
    }
    if (state === "error") expect(screen.getByText("Не удалось загрузить список")).toBeVisible();
  });
});
