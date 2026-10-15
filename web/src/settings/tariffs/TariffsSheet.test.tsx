import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  ArchiveTariffPlanRequestSchema,
  CreateTariffPlanRequestSchema,
  UpdateTariffPlanRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

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

/**
 * Сервер с тарифами [initial] в памяти; валюта организации — рубль. [failSave] — сохранение
 * и архивация тарифа отклоняются.
 */
function tariffsServer({
  initial = [monthly, trial],
  failSave = false,
}: { initial?: readonly Record<string, unknown>[]; failSave?: boolean } = {}) {
  let list = initial;
  const conflict = () => json({ code: "TARIFF_CONFLICT", message: "Тариф не сохранён" }, 409);
  return appServer({
    "org/settings": () => json({ name: "Лига", timezone: "Europe/Moscow", currency: "RUB" }),
    "tariffs/list": () => json({ tariffs: list }),
    "tariffs/create": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      list = [...list, { ...CreateTariffPlanRequestSchema.parse(body), archived: false }];
      return empty();
    },
    "tariffs/update": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      const tariff = UpdateTariffPlanRequestSchema.parse(body);
      list = list.map((t) => (t["id"] === tariff.id ? { ...t, ...tariff } : t));
      return empty();
    },
    "tariffs/archive": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      const { id, archived } = ArchiveTariffPlanRequestSchema.parse(body);
      list = list.map((t) => (t["id"] === id ? { ...t, archived } : t));
      return empty();
    },
  });
}

const PANEL = "/settings?panel=tariffs";

/** Панель с заголовком [name]. */
const sheet = (name: string) => within(screen.getByRole("dialog", { name }));

/** Кнопка строки тарифа [name]. */
const row = (name: string) => screen.findByRole("button", { name: `Изменить «${name}»` });

/** Текст элемента [element] с обычными пробелами вместо неразрывных. */
const text = (element: HTMLElement) => element.textContent.replace(/\s/g, " ");

describe("панель тарифов", () => {
  it("показывает строку с условиями и стоимостью в валюте организации", async () => {
    openApp(PANEL, tariffsServer().fetch);

    const month = await row("Месяц");
    expect(month).toHaveTextContent("8 занятий · 1 мес.");
    expect(text(month)).toContain("3 500,00 ₽");
  });

  it("без тарифов показывает пустое состояние и кнопку создания", async () => {
    openApp(PANEL, tariffsServer({ initial: [] }).fetch);

    expect(await screen.findByText(ru["tariffs.empty"])).toBeVisible();
    expect(
      sheet(ru["tariffs.title"]).getByRole("button", { name: ru["tariffs.create"] }),
    ).toBeVisible();
    expect(screen.queryByRole("button", { name: /Архив/ })).toBeNull();
  });

  it("сворачивает архивные тарифы под активными и раскрывает по нажатию", async () => {
    openApp(PANEL, tariffsServer().fetch);
    const user = userEvent.setup();

    await row("Месяц");
    const group = screen.getByRole("button", { name: "Архив (1)" });
    expect(group).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("button", { name: "Изменить «Пробный»" })).toBeNull();

    await user.click(group);

    const archived = await row("Пробный");
    expect(archived).toHaveTextContent("Безлимит · 7 дн.");
    expect(archived).toHaveTextContent(ru["tariffs.archived"]);
  });

  it("без архивных тарифов блока архива нет", async () => {
    openApp(PANEL, tariffsServer({ initial: [monthly] }).fetch);

    await row("Месяц");
    expect(screen.queryByRole("button", { name: /Архив/ })).toBeNull();
  });

  it("создаёт безлимитный тариф со стоимостью в копейках", async () => {
    const api = tariffsServer();
    openApp(PANEL, api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tariffs.create"] }));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.create"] });
    await user.type(within(editor).getByLabelText(ru["tariffs.name"]), "Год");
    await user.click(within(editor).getByLabelText(ru["tariffs.unlimited"]));
    expect(within(editor).queryByLabelText(ru["tariffs.sessions"])).not.toBeInTheDocument();
    await user.clear(within(editor).getByLabelText(ru["tariffs.duration"]));
    await user.type(within(editor).getByLabelText(ru["tariffs.duration"]), "12");
    await user.type(within(editor).getByLabelText("Стоимость, ₽"), "30 000,5");
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await row("Год")).toHaveTextContent("Безлимит · 12 мес.");
    expect(screen.queryByRole("dialog", { name: ru["tariffs.create"] })).toBeNull();
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

  it("изменяет тариф с предзаполненной формой", async () => {
    const api = tariffsServer();
    openApp(PANEL, api.fetch);
    const user = userEvent.setup();

    await user.click(await row("Месяц"));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.edit"] });
    expect(within(editor).getByLabelText(ru["tariffs.sessions"])).toHaveValue("8");
    expect(within(editor).getByLabelText("Стоимость, ₽")).toHaveValue("3500");
    expect(within(editor).getByRole("radio", { name: ru["tariffs.months"] })).toBeChecked();
    await user.click(within(editor).getByRole("radio", { name: ru["tariffs.days"] }));
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

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
});

describe("панель тарифа", () => {
  it("не отправляет форму с ошибками", async () => {
    const api = tariffsServer();
    openApp(PANEL, api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tariffs.create"] }));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.create"] });
    await user.type(within(editor).getByLabelText(ru["tariffs.name"]), "Ошибочный");
    await user.type(within(editor).getByLabelText(ru["tariffs.sessions"]), "0");
    await user.type(within(editor).getByLabelText("Стоимость, ₽"), "10,555");
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await within(editor).findByText(ru["error.positiveInteger"])).toBeVisible();
    expect(within(editor).getByText(ru["error.invalidAmount"])).toBeVisible();
    expect(api.to("tariffs/create")).toHaveLength(0);
  });

  it("закрытие после изменения условий спрашивает подтверждение", async () => {
    openApp(PANEL, tariffsServer().fetch);
    const user = userEvent.setup();

    await user.click(await row("Месяц"));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.edit"] });
    await user.type(within(editor).getByLabelText(ru["tariffs.duration"]), "2");
    await user.keyboard("{Escape}");

    expect(await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeVisible();
  });

  it("при ошибке сервера остаётся открытой с введёнными данными", async () => {
    openApp(PANEL, tariffsServer({ failSave: true }).fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tariffs.create"] }));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.create"] });
    await user.type(within(editor).getByLabelText(ru["tariffs.name"]), "Год");
    await user.type(within(editor).getByLabelText(ru["tariffs.sessions"]), "100");
    await user.type(within(editor).getByLabelText("Стоимость, ₽"), "30000");
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await within(editor).findByRole("alert")).toBeVisible();
    expect(within(editor).getByLabelText(ru["tariffs.name"])).toHaveValue("Год");
  });

  it("в создании нет действий архива", async () => {
    openApp(PANEL, tariffsServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tariffs.create"] }));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.create"] });

    expect(within(editor).queryByRole("button", { name: ru["tariffs.archive"] })).toBeNull();
    expect(within(editor).queryByRole("button", { name: ru["tariffs.restore"] })).toBeNull();
  });

  it("переносит в архив, оставаясь открытой и не сбрасывая правки", async () => {
    const api = tariffsServer();
    openApp(PANEL, api.fetch);
    const user = userEvent.setup();

    await user.click(await row("Месяц"));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.edit"] });
    const name = within(editor).getByLabelText(ru["tariffs.name"]);
    await user.clear(name);
    await user.type(name, "Месяц плюс");
    await user.click(within(editor).getByRole("button", { name: ru["tariffs.archive"] }));

    expect(
      await within(editor).findByRole("button", { name: ru["tariffs.restore"] }),
    ).toBeVisible();
    await waitFor(() => {
      expect(toast.success).toHaveBeenLastCalledWith(ru["tariffs.archivedToast"]);
    });
    expect(within(editor).getByLabelText(ru["tariffs.name"])).toHaveValue("Месяц плюс");
    expect(
      await screen.findByRole("button", { name: "Архив (2)", hidden: true }),
    ).toBeInTheDocument();
    expect(api.to("tariffs/archive").map((r) => r.body)).toEqual([
      { id: monthly.id, archived: true },
    ]);
  });

  it("восстанавливает архивный тариф", async () => {
    const api = tariffsServer();
    openApp(PANEL, api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Архив (1)" }));
    await user.click(await row("Пробный"));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.edit"] });
    await user.click(within(editor).getByRole("button", { name: ru["tariffs.restore"] }));

    expect(
      await within(editor).findByRole("button", { name: ru["tariffs.archive"] }),
    ).toBeVisible();
    await waitFor(() => {
      expect(toast.success).toHaveBeenLastCalledWith(ru["tariffs.restoredToast"]);
    });
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: /Архив \(/, hidden: true })).toBeNull();
    });
    expect(api.to("tariffs/archive").map((r) => r.body)).toEqual([
      { id: trial.id, archived: false },
    ]);
  });

  it("при ошибке архивации показывает сообщение и не меняет состояние", async () => {
    openApp(PANEL, tariffsServer({ failSave: true }).fetch);
    const user = userEvent.setup();

    await user.click(await row("Месяц"));
    const editor = await screen.findByRole("dialog", { name: ru["tariffs.edit"] });
    await user.click(within(editor).getByRole("button", { name: ru["tariffs.archive"] }));

    expect(await within(editor).findByRole("alert")).toBeVisible();
    expect(within(editor).getByRole("button", { name: ru["tariffs.archive"] })).toBeVisible();
  });
});
