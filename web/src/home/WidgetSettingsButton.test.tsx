import { cleanup, fireEvent, screen, waitFor, within } from "@testing-library/react";
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

function server(dashboard = defaultDashboardSettings()) {
  let display: DisplaySettings = DisplaySettingsSchema.parse({
    clients: { columns: [], sort: null, savedViews: [] },
    groups: { columns: [], sort: null, savedViews: [] },
    employees: { columns: [], sort: null, savedViews: [] },
    tasks: { columns: [], sort: null, savedViews: [] },
    dashboard,
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
  it("оставляет выключенные строки на месте и позволяет включить виджет при всех выключенных", async () => {
    const fake = server();
    openApp("/", fake.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    const dialog = screen.getByRole("dialog");
    const switches = within(dialog).getAllByRole("switch");
    const handles = within(dialog).getAllByRole("button", { name: /Переместить/ });
    const labels = switches.map((item) => item.getAttribute("aria-label"));
    for (const item of switches) await user.click(item);
    expect(within(dialog).getAllByRole("switch")).toEqual(switches);
    expect(
      within(dialog)
        .getAllByRole("switch")
        .map((item) => item.getAttribute("aria-label")),
    ).toEqual(labels);
    expect(within(dialog).getAllByRole("button", { name: /Переместить/ })).toEqual(handles);
    for (const item of switches) {
      expect(item).not.toBeChecked();
      expect(item).toBeEnabled();
    }
    expect(within(dialog).getByText("Должники")).toHaveClass("text-muted-foreground");
    expect(within(dialog).queryByText("Скрытые виджеты")).not.toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    cleanup();
    openApp("/", fake.fetch);
    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    expect(
      screen.getAllByRole("switch").every((item) => item.getAttribute("aria-checked") === "false"),
    ).toBe(true);
    await user.click(screen.getByRole("switch", { name: "Показывать Должники" }));
    await user.keyboard("{Escape}");
    expect(await screen.findByRole("heading", { name: "Должники" })).toBeInTheDocument();
    expect(screen.getAllByRole("heading")).toHaveLength(2);
  });

  it("нормализует старый кэш при первом изменении и сохраняет позиции нескольких скрытых виджетов", async () => {
    const initial = defaultDashboardSettings();
    const [a, b, c, d] = initial.widgets;
    if (a === undefined || b === undefined || c === undefined || d === undefined)
      throw new Error("Missing defaults");
    const widgets = initial.widgets.map((widget) =>
      widget.type === "debtors" ? { ...widget, title: "Долг", limit: 7 } : widget,
    );
    const fake = server({ widgets, layout: [d.id, a.id] });
    openApp("/", fake.fetch);
    const user = userEvent.setup();
    await screen.findByRole("heading", { name: "Незакрытые журналы" });
    expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual([
      "Главная",
      "Незакрытые журналы",
      "Занятия сегодня",
    ]);
    expect(fake.to("display-settings/update")).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Настройки главной" }));
    const handle = screen.getByRole("button", {
      name: "Переместить «Должники» (стрелки вверх и вниз)",
    });
    handle.focus();
    await user.keyboard("{ArrowDown}");
    expect(handle).toHaveFocus();
    expect(screen.getByRole("switch", { name: "Показывать Должники" })).not.toBeChecked();
    await waitFor(() => {
      expect(fake.display().dashboard.widgets.map((widget) => widget.id)).toEqual([
        d.id,
        c.id,
        b.id,
        a.id,
      ]);
      expect(fake.display().dashboard.layout).toEqual([d.id, a.id]);
    });
    await user.click(screen.getByRole("switch", { name: "Показывать Должники" }));
    await user.keyboard("{Escape}");
    cleanup();
    openApp("/", fake.fetch);
    await screen.findByRole("heading", { name: "Долг" });
    expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual([
      "Главная",
      "Незакрытые журналы",
      "Долг",
      "Занятия сегодня",
    ]);
    await user.click(screen.getByRole("button", { name: "Настройки главной" }));
    const dialog = screen.getByRole("dialog");
    expect(
      within(dialog)
        .getAllByRole("switch")
        .map((item) => item.getAttribute("aria-label")),
    ).toEqual([
      "Показывать Незакрытые журналы",
      "Показывать Дни рождения",
      "Показывать Должники",
      "Показывать Занятия сегодня",
    ]);
    expect(
      within(dialog).getByRole("switch", { name: "Показывать Дни рождения" }),
    ).not.toBeChecked();
    expect(fake.display().dashboard.widgets).toHaveLength(4);
    expect(fake.display().dashboard.widgets.find((widget) => widget.id === b.id)).toMatchObject({
      title: "Долг",
      limit: 7,
    });
  });

  it("перетаскивает виджет через несколько позиций и отменяет незавершённое перемещение", async () => {
    const initial = defaultDashboardSettings();
    const fake = server({ ...initial, layout: initial.layout.slice(1) });
    openApp("/", fake.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    const dialog = screen.getByRole("dialog");
    const handles = within(dialog).getAllByRole("button", { name: /Переместить/ });
    const source = handles[0];
    const target = handles[3];
    if (source === undefined || target === undefined) throw new Error("Missing drag handles");
    Object.defineProperty(source, "setPointerCapture", { value: () => undefined });
    Object.defineProperty(document, "elementFromPoint", {
      configurable: true,
      value: () => target,
    });
    try {
      fireEvent.pointerDown(source, { button: 0, pointerId: 1 });
      fireEvent.pointerMove(source, { pointerId: 1, clientX: 10, clientY: 100 });
      fireEvent.pointerCancel(source, { pointerId: 1 });
      expect(fake.to("display-settings/update")).toHaveLength(0);

      fireEvent.pointerDown(source, { button: 0, pointerId: 2 });
      fireEvent.pointerMove(source, { pointerId: 2, clientX: 10, clientY: 100 });
      fireEvent.pointerUp(source, { pointerId: 2 });
      await waitFor(() => {
        const dashboard = fake.display().dashboard;
        expect(
          dashboard.layout.map((id) => dashboard.widgets.find((widget) => widget.id === id)?.type),
        ).toEqual(["debtors", "birthdays", "unclosedSessions"]);
        expect(dashboard.widgets.map((widget) => widget.type)).toEqual([
          "debtors",
          "birthdays",
          "unclosedSessions",
          "sessions",
        ]);
      });
      expect(fake.to("display-settings/update")).toHaveLength(1);
      await user.click(within(dialog).getByRole("switch", { name: "Показывать Занятия сегодня" }));
      await user.keyboard("{Escape}");
      cleanup();
      openApp("/", fake.fetch);
      await screen.findByRole("heading", { name: "Занятия сегодня" });
      expect(screen.getAllByRole("heading").at(-1)).toHaveTextContent("Занятия сегодня");
    } finally {
      Reflect.deleteProperty(document, "elementFromPoint");
    }
  });

  it("настраивает новый виджет журналов в старых настройках и восстанавливает скрытый после перезагрузки", async () => {
    const widgets = defaultDashboardSettings().widgets.filter(
      (widget) => widget.type !== "unclosedSessions",
    );
    const fake = server({ widgets, layout: widgets.map((widget) => widget.id) });
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки: Незакрытые журналы" }));
    const dialog = screen.getByRole("dialog", { name: "Настройки: Незакрытые журналы" });
    await user.type(within(dialog).getByRole("textbox", { name: "Название виджета" }), "Журналы");
    await user.click(within(dialog).getByRole("button", { name: "Увеличить количество" }));
    await waitFor(() => {
      expect(
        fake.display().dashboard.widgets.find((widget) => widget.type === "unclosedSessions"),
      ).toMatchObject({ title: "Журналы", limit: 11 });
    });
    await user.click(within(dialog).getByRole("button", { name: "Скрыть виджет" }));
    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Журналы" })).not.toBeInTheDocument();
    });

    cleanup();
    openApp("/", fake.fetch);
    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    const visibility = await screen.findByRole("switch", {
      name: "Показывать Незакрытые журналы",
    });
    expect(visibility).not.toBeChecked();
    await user.click(visibility);
    await user.keyboard("{Escape}");
    expect(await screen.findByRole("heading", { name: "Журналы" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Настройки: Незакрытые журналы" }));
    expect(within(screen.getByRole("dialog")).getByText("Показывать: 11")).toBeInTheDocument();
  });

  it("скрывает новый виджет журналов из общих настроек до первого сохранения", async () => {
    const widgets = defaultDashboardSettings().widgets.filter(
      (widget) => widget.type !== "unclosedSessions",
    );
    const fake = server({ widgets, layout: widgets.map((widget) => widget.id) });
    openApp("/", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    const visibility = await screen.findByRole("switch", {
      name: "Показывать Незакрытые журналы",
    });
    await user.click(visibility);
    await waitFor(() => {
      expect(
        screen.getByRole("switch", { name: "Показывать Незакрытые журналы" }),
      ).not.toBeChecked();
      const dashboard = fake.display().dashboard;
      const widget = dashboard.widgets.find((item) => item.type === "unclosedSessions");
      expect(widget).toBeDefined();
      expect(dashboard.layout).not.toContain(widget?.id);
    });
  });

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
    cleanup();
    openApp("/", fake.fetch);
    await user.click(await screen.findByRole("button", { name: "Настройки главной" }));
    await user.click(screen.getByRole("switch", { name: "Показывать Дни рождения" }));
    await user.keyboard("{Escape}");

    expect(await screen.findByRole("heading", { name: "Моя команда" })).toBeInTheDocument();
    expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual([
      "Главная",
      "Занятия сегодня",
      "Должники",
      "Моя команда",
      "Незакрытые журналы",
    ]);
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
    within(dialog)
      .getByRole("button", { name: "Переместить «Занятия сегодня» (стрелки вверх и вниз)" })
      .focus();
    await user.keyboard("{ArrowDown}");
    await waitFor(() => {
      expect(
        fake
          .display()
          .dashboard.layout.map(
            (id) => fake.display().dashboard.widgets.find((widget) => widget.id === id)?.type,
          ),
      ).toEqual(["debtors", "sessions", "birthdays", "unclosedSessions"]);
    });

    await user.click(within(dialog).getByRole("switch", { name: "Показывать Должники" }));
    await waitFor(() => {
      expect(
        fake
          .display()
          .dashboard.layout.map(
            (id) => fake.display().dashboard.widgets.find((widget) => widget.id === id)?.type,
          ),
      ).toEqual(["sessions", "birthdays", "unclosedSessions"]);
    });
    await user.click(within(dialog).getByRole("switch", { name: "Показывать Должники" }));
    await waitFor(() => {
      expect(
        fake
          .display()
          .dashboard.layout.map(
            (id) => fake.display().dashboard.widgets.find((widget) => widget.id === id)?.type,
          ),
      ).toEqual(["debtors", "sessions", "birthdays", "unclosedSessions"]);
    });

    await user.keyboard("{Escape}");
    cleanup();
    openApp("/", fake.fetch);
    await screen.findByRole("heading", { name: "Занятия сегодня" });
    expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual([
      "Главная",
      "Должники",
      "Занятия сегодня",
      "Дни рождения",
      "Незакрытые журналы",
    ]);
  });
});
