import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ArchiveTariffPlanRequestSchema,
  CreateTariffPlanRequestSchema,
  UpdateTariffPlanRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

const monthly = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000201",
  name: "Месяц",
  sessions: 8,
  durationValue: 1,
  durationUnit: "MONTHS",
  price: { minorUnits: 350000, currency: "RUB" },
  archived: false,
};
const trial = {
  ...monthly,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000202",
  name: "Пробный",
  sessions: null,
  durationValue: 7,
  durationUnit: "DAYS",
  price: { minorUnits: 50050, currency: "RUB" },
  archived: true,
};

/** Сервер с тарифами в памяти; валюта организации — рубль. */
function tariffsServer() {
  let list: Record<string, unknown>[] = [monthly, trial];
  return appServer({
    "org/settings": () => json({ name: "Лига", timezone: "Europe/Moscow", currency: "RUB" }),
    "tariffs/list": () => json({ tariffs: list }),
    "tariffs/create": ({ body }) => {
      list = [...list, { ...CreateTariffPlanRequestSchema.parse(body), archived: false }];
      return empty();
    },
    "tariffs/update": ({ body }) => {
      const tariff = UpdateTariffPlanRequestSchema.parse(body);
      list = list.map((t) => (t["id"] === tariff.id ? { ...tariff, archived: false } : t));
      return empty();
    },
    "tariffs/archive": ({ body }) => {
      const { id, archived } = ArchiveTariffPlanRequestSchema.parse(body);
      list = list.map((t) => (t["id"] === id ? { ...t, archived } : t));
      return empty();
    },
  });
}

describe("тарифы", () => {
  it("показывают условия и стоимость в валюте организации, архивный отмечен", async () => {
    openApp("/settings/tariffs", tariffsServer().fetch);

    const card = (await screen.findByRole("heading", { name: "Месяц" })).closest("article");
    expect(card).not.toBeNull();
    if (card !== null) {
      expect(card).toHaveTextContent("8 занятий · 1 мес.");
      expect(card.textContent.replace(/\s/g, " ")).toContain("3 500,00 ₽");
    }
    const archived = screen.getByRole("heading", { name: "Пробный" }).closest("article");
    expect(archived).toHaveTextContent("Безлимит · 7 дн.");
    expect(archived).toHaveTextContent(ru["tariffs.archived"]);
  });

  it("создаёт безлимитный тариф со стоимостью в копейках", async () => {
    const api = tariffsServer();
    openApp("/settings/tariffs", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tariffs.create"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["tariffs.create"] });
    await user.type(within(dialog).getByLabelText(ru["tariffs.name"]), "Год");
    await user.click(within(dialog).getByLabelText(ru["tariffs.unlimited"]));
    expect(within(dialog).queryByLabelText(ru["tariffs.sessions"])).not.toBeInTheDocument();
    await user.clear(within(dialog).getByLabelText(ru["tariffs.duration"]));
    await user.type(within(dialog).getByLabelText(ru["tariffs.duration"]), "12");
    await user.type(within(dialog).getByLabelText("Стоимость, ₽"), "30 000,5");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("heading", { name: "Год" })).toBeVisible();
    const [created] = api
      .to("tariffs/create")
      .map((r) => CreateTariffPlanRequestSchema.parse(r.body));
    expect(created).toMatchObject({
      name: "Год",
      sessions: null,
      durationValue: 12,
      durationUnit: "MONTHS",
      price: { minorUnits: 3000050, currency: "RUB" },
    });
  });

  it("не отправляет форму с ошибками", async () => {
    const api = tariffsServer();
    openApp("/settings/tariffs", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tariffs.create"] }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(ru["tariffs.name"]), "Ошибочный");
    await user.type(within(dialog).getByLabelText(ru["tariffs.sessions"]), "0");
    await user.type(within(dialog).getByLabelText("Стоимость, ₽"), "10,555");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await within(dialog).findByText(ru["error.positiveInteger"])).toBeVisible();
    expect(within(dialog).getByText(ru["error.invalidAmount"])).toBeVisible();
    expect(api.to("tariffs/create")).toHaveLength(0);
  });

  it("редактирует тариф с предзаполненной формой", async () => {
    const api = tariffsServer();
    openApp("/settings/tariffs", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить: Месяц" }));
    const dialog = await screen.findByRole("dialog", { name: ru["tariffs.edit"] });
    expect(within(dialog).getByLabelText(ru["tariffs.sessions"])).toHaveValue("8");
    expect(within(dialog).getByLabelText("Стоимость, ₽")).toHaveValue("3500");
    expect(within(dialog).getByRole("radio", { name: ru["tariffs.months"] })).toBeChecked();
    await user.click(within(dialog).getByRole("radio", { name: ru["tariffs.days"] }));
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByText("8 занятий · 1 дн.")).toBeVisible();
    expect(api.to("tariffs/update").map((r) => r.body)).toEqual([
      {
        id: monthly.id,
        name: "Месяц",
        sessions: 8,
        durationValue: 1,
        durationUnit: "DAYS",
        price: { minorUnits: 350000, currency: "RUB" },
      },
    ]);
  });

  it("переносит в архив и восстанавливает", async () => {
    const api = tariffsServer();
    openApp("/settings/tariffs", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "В архив: Месяц" }));
    expect(await screen.findByRole("button", { name: "Восстановить: Месяц" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Восстановить: Пробный" }));
    expect(await screen.findByRole("button", { name: "В архив: Пробный" })).toBeVisible();
    expect(api.to("tariffs/archive").map((r) => r.body)).toEqual([
      { id: monthly.id, archived: true },
      { id: trial.id, archived: false },
    ]);
  });
});
