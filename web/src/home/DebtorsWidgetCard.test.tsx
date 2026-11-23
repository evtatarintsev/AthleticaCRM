import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ClientListItemSchema, DisplaySettingsSchema } from "@/api/generated/contracts";
import { appServer, json, openApp } from "@/test/app";
import { defaultDashboardSettings } from "./dashboardSettings";

const clients = [
  "Марк Соколов",
  "Никита Лосев",
  "Артём Фёдоров",
  "Ульяна Тихонова",
  "Глеб Хохлов",
].map((name, index) =>
  ClientListItemSchema.parse({
    id: `0199a0b2-7c3e-7d2a-9f10-00000000090${String(index)}`,
    name,
    avatarId: null,
    birthday: null,
    gender: "MALE",
    groups: [],
    balance: { minorUnits: -480000, currency: "RUB" },
    customFields: [],
    contacts: [],
    state: "ACTIVE",
    memberships: [],
    unpaidSessions: 0,
  }),
);
function server() {
  const dashboard = defaultDashboardSettings();
  return appServer({
    "display-settings": () =>
      json(
        DisplaySettingsSchema.parse({
          clients: { columns: [], sort: null, savedViews: [] },
          groups: { columns: [], sort: null, savedViews: [] },
          employees: { columns: [], sort: null, savedViews: [] },
          tasks: { columns: [], sort: null, savedViews: [] },
          dashboard: {
            ...dashboard,
            layout: dashboard.widgets.filter((w) => w.type === "debtors").map((w) => w.id),
          },
        }),
      ),
    "clients/list": () => json({ clients, total: 7 }),
  });
}

describe("должники", () => {
  it("показывает инициалы, сумму отрицательным цветом и делает всю строку ссылкой", async () => {
    const fake = server();
    const { history } = openApp("/", fake.fetch);
    const link = await screen.findByRole("link", { name: /Марк Соколов/ }, { timeout: 5000 });
    expect(within(link).getByText("МС")).toBeVisible();
    const money = within(link).getByText(/4\s800/);
    expect(money).toHaveClass("text-destructive");
    expect(money.textContent.replace(/\s/g, " ")).toBe("-4 800,00 ₽");
    await userEvent.setup().click(money);
    expect(history.location.pathname).toBe(link.getAttribute("href"));
  });
  it("показывает пять строк, счётчик семь и сохраняет переход ко всем должникам", async () => {
    const { history } = openApp("/", server().fetch);
    await screen.findByRole("link", { name: /Марк Соколов/ }, { timeout: 5000 });
    const card = screen.getByRole("heading", { name: "Должники" }).parentElement?.parentElement;
    if (card === null || card === undefined) throw new Error("Missing card");
    expect(within(card).getAllByRole("listitem")).toHaveLength(5);
    expect(within(card).getByText("7")).toBeVisible();
    await userEvent.setup().click(within(card).getByRole("link", { name: "Показать всех (7)" }));
    expect(history.location.pathname).toBe("/clients");
    expect(history.location.search).toContain("debt=true");
  });
});
