import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  DashboardSettingsSchema,
  DashboardWidgetSchema,
  DisplaySettingsInputSchema,
  DisplaySettingsSchema,
  type DisplaySettings,
} from "@/api/generated/contracts";
import { appServer, json, openApp } from "@/test/app";
import { defaultDashboardSettings } from "./dashboardSettings";

function server() {
  let display: DisplaySettings = DisplaySettingsSchema.parse({
    clients: { columns: [], sort: null, savedViews: [] },
    groups: { columns: [], sort: null, savedViews: [] },
    employees: { columns: [], sort: null, savedViews: [] },
    tasks: { columns: [], sort: null, savedViews: [] },
    dashboard: defaultDashboardSettings(),
  });
  const fake = appServer({
    "display-settings": () => json(display),
    "display-settings/update": ({ body }) => {
      const input = DisplaySettingsInputSchema.parse(body);
      const dashboard =
        input.dashboard === undefined
          ? display.dashboard
          : DashboardSettingsSchema.parse({
              ...display.dashboard,
              ...input.dashboard,
              widgets:
                input.dashboard.widgets?.map((widget) => DashboardWidgetSchema.parse(widget)) ??
                display.dashboard.widgets,
            });
      display = DisplaySettingsSchema.parse({ ...display, ...input, dashboard });
      return json(display);
    },
    "home/today-sessions": () => json({ date: "2026-10-16", sessions: [] }),
    "clients/list": () => json({ clients: [], total: 0 }),
  });
  return { ...fake, display: () => display };
}

describe("индивидуальные настройки виджетов главной", () => {
  it("из шестерёнки дней рождения меняет название, период и лимит", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки: Дни рождения" }));
    const dialog = screen.getByRole("dialog", { name: "Настройки: Дни рождения" });
    const title = within(dialog).getByRole("textbox", { name: "Название виджета" });
    await user.type(title, "Юбилеи");
    await user.selectOptions(
      within(dialog).getByRole("combobox", { name: "День рождения" }),
      "WEEK",
    );
    await user.click(within(dialog).getByRole("button", { name: "Увеличить количество" }));

    await waitFor(() => {
      const widget = fake.display().dashboard.widgets.find((item) => item.type === "birthdays");
      expect(widget).toMatchObject({ title: "Юбилеи", window: "WEEK", limit: 11 });
    });
    expect(fake.to("display-settings/update").length).toBeGreaterThan(1);
  });

  it("из шестерёнки должников меняет название и лимит", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки: Должники" }));
    const dialog = screen.getByRole("dialog", { name: "Настройки: Должники" });
    await user.type(within(dialog).getByRole("textbox", { name: "Название виджета" }), "Долг");
    await user.click(within(dialog).getByRole("button", { name: "Увеличить количество" }));

    await waitFor(() => {
      const widget = fake.display().dashboard.widgets.find((item) => item.type === "debtors");
      expect(widget).toMatchObject({ title: "Долг", limit: 11 });
    });
  });

  it("у занятий сегодня доступны название и клавиатурное закрытие панели", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки: Занятия сегодня" }));
    const dialog = screen.getByRole("dialog", { name: "Настройки: Занятия сегодня" });
    expect(within(dialog).queryByRole("combobox")).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: "Увеличить количество" }),
    ).not.toBeInTheDocument();
    expect(dialog).toHaveClass("max-w-sm", "overflow-y-auto");

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("скрывает карточку и восстанавливает её с названием и параметрами", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки: Дни рождения" }));
    const birthdayDialog = screen.getByRole("dialog", { name: "Настройки: Дни рождения" });
    await user.type(
      within(birthdayDialog).getByRole("textbox", { name: "Название виджета" }),
      "Моя команда",
    );
    await user.selectOptions(
      within(birthdayDialog).getByRole("combobox", { name: "День рождения" }),
      "WEEK",
    );
    await user.click(within(birthdayDialog).getByRole("button", { name: "Увеличить количество" }));
    await waitFor(() => {
      const widget = fake.display().dashboard.widgets.find((item) => item.type === "birthdays");
      expect(widget).toMatchObject({ title: "Моя команда", window: "WEEK", limit: 11 });
    });
    await user.click(within(birthdayDialog).getByRole("button", { name: "Скрыть виджет" }));

    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Моя команда" })).not.toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: "Настройки главной" }));
    await user.click(screen.getByRole("checkbox", { name: "Показывать Дни рождения" }));
    await user.keyboard("{Escape}");

    expect(await screen.findByRole("heading", { name: "Моя команда" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Настройки: Дни рождения" }));
    const restoredDialog = screen.getByRole("dialog", { name: "Настройки: Дни рождения" });
    expect(within(restoredDialog).getByRole("textbox", { name: "Название виджета" })).toHaveValue(
      "Моя команда",
    );
    expect(within(restoredDialog).getByRole("combobox", { name: "День рождения" })).toHaveValue(
      "WEEK",
    );
    expect(within(restoredDialog).getByText("Показывать: 11")).toBeInTheDocument();
    await waitFor(() => {
      const widget = fake.display().dashboard.widgets.find((item) => item.type === "birthdays");
      expect(widget).toMatchObject({ title: "Моя команда", window: "WEEK", limit: 11 });
    });
  });

  it("в общих настройках показывает фиксированные названия и только порядок с видимостью", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки: Дни рождения" }));
    const widgetDialog = screen.getByRole("dialog", { name: "Настройки: Дни рождения" });
    await user.type(
      within(widgetDialog).getByRole("textbox", { name: "Название виджета" }),
      "Мой список",
    );
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "Настройки главной" }));

    const dialog = screen.getByRole("dialog", { name: "Настройки главной" });
    expect(within(dialog).getByText("Дни рождения")).toBeInTheDocument();
    expect(within(dialog).queryByText("Мой список")).not.toBeInTheDocument();
    expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();
    expect(within(dialog).queryByRole("combobox")).not.toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: "Увеличить количество" }),
    ).not.toBeInTheDocument();
  });

  it("сохраняет порядок и включает скрытый виджет обратно из общих настроек", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    const dialog = screen.getByRole("dialog", { name: "Настройки главной" });
    await user.click(within(dialog).getByRole("button", { name: "Опустить «Занятия сегодня»" }));
    await waitFor(() => {
      expect(
        fake
          .display()
          .dashboard.layout.map(
            (id) => fake.display().dashboard.widgets.find((widget) => widget.id === id)?.type,
          ),
      ).toEqual(["debtors", "sessions", "birthdays"]);
    });

    await user.click(within(dialog).getByRole("checkbox", { name: "Показывать Должники" }));
    await waitFor(() => {
      expect(
        fake
          .display()
          .dashboard.layout.map(
            (id) => fake.display().dashboard.widgets.find((widget) => widget.id === id)?.type,
          ),
      ).toEqual(["sessions", "birthdays"]);
    });
    await user.click(within(dialog).getByRole("checkbox", { name: "Показывать Должники" }));
    await waitFor(() => {
      expect(
        fake
          .display()
          .dashboard.layout.map(
            (id) => fake.display().dashboard.widgets.find((widget) => widget.id === id)?.type,
          ),
      ).toEqual(["sessions", "birthdays", "debtors"]);
    });

    await user.keyboard("{Escape}");
    cleanup();
    openApp("/", fake.fetch);
    await screen.findByRole("heading", { name: "Занятия сегодня" });
    expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual([
      "Главная",
      "Занятия сегодня",
      "Дни рождения",
      "Должники",
    ]);
  });
});
