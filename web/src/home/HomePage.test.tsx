import { act, cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ClientListRequestSchema,
  ClientListItemSchema,
  DisplaySettingsSchema,
  SessionIdSchema,
  type DisplaySettings,
} from "@/api/generated/contracts";
import { appServer, json, openApp } from "@/test/app";
import { defaultDashboardSettings } from "./dashboardSettings";

const sessionId = SessionIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000701");

const displaySettings: DisplaySettings = DisplaySettingsSchema.parse({
  clients: { columns: [], sort: null, savedViews: [] },
  groups: { columns: [], sort: null, savedViews: [] },
  employees: { columns: [], sort: null, savedViews: [] },
  tasks: { columns: [], sort: null, savedViews: [] },
  dashboard: defaultDashboardSettings(),
});

function server(settings = displaySettings) {
  return appServer({
    "display-settings": () => json(settings),
    "home/today-sessions": () =>
      json({
        date: "2026-09-27",
        sessions: [
          {
            sessionId,
            groupName: "Юниоры У10",
            startTime: "10:00",
            endTime: "11:00",
            hallName: "Зал А",
          },
        ],
      }),
    "clients/list": () => json({ clients: [], total: 0 }),
  });
}

afterEach(() => {
  vi.useRealTimers();
});

describe("главная", () => {
  it.each([
    ["TOMORROW", "todayTomorrow", "2026-11-19", "ДР · Сегодня и завтра"],
    ["WEEK", "weekAhead", "2026-11-25", "ДР · На неделю вперёд"],
  ] as const)(
    "Показать всех для %s сохраняет период и порядок после перезагрузки",
    async (window, filter, to, chip) => {
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(new Date(2026, 10, 18, 12));
      const widget = defaultDashboardSettings().widgets.find((item) => item.type === "birthdays");
      if (widget?.type !== "birthdays") {
        throw new Error("Нет виджета дней рождения");
      }
      const first = ClientListItemSchema.parse({
        id: "0199a0b2-7c3e-7d2a-9f10-000000000901",
        name: "Яна",
        birthday: "2000-11-18",
        avatarId: null,
        gender: "FEMALE",
        groups: [],
        contacts: [],
        customFields: [],
        state: "ACTIVE",
        memberships: [],
        unpaidSessions: 0,
        balance: { minorUnits: 0, currency: "RUB" },
      });
      const last = {
        ...first,
        id: "0199a0b2-7c3e-7d2a-9f10-000000000902",
        name: "Анна",
        birthday: `1990-${to.slice(5)}`,
      };
      const fake = appServer({
        "display-settings": () =>
          json({
            ...displaySettings,
            dashboard: { widgets: [{ ...widget, window, limit: 1 }], layout: [widget.id] },
          }),
        "custom-fields/list": () => json([]),
        "clients/list": ({ body }) => {
          const request = ClientListRequestSchema.parse(body);
          return json({ clients: request.limit === 1 ? [first] : [first, last], total: 2 });
        },
      });
      const user = userEvent.setup();
      const { history } = openApp("/", fake.fetch);
      await user.click(await screen.findByRole("link", { name: /Показать всех/ }));
      await screen.findByRole("table");
      expect(history.location.pathname).toBe("/clients");
      expect(history.location.search).toContain(`birthday=${filter}`);
      expect(history.location.search).toContain("sort=nextBirthday");
      expect(screen.getByText(chip)).toBeVisible();
      expect(within(screen.getByRole("table")).getByText("Анна")).toBeVisible();
      const request = ClientListRequestSchema.parse(fake.to("clients/list").at(-1)?.body);
      expect(request).toMatchObject({
        birthday: { from: "2026-11-18", to },
        sort: [
          { field: "NEXT_BIRTHDAY", direction: "Asc" },
          { field: "NAME", direction: "Asc" },
        ],
      });
      const address = history.location.href;
      cleanup();
      openApp(address, fake.fetch);
      await screen.findByRole("table");
      await waitFor(() => {
        expect(ClientListRequestSchema.parse(fake.to("clients/list").at(-1)?.body)).toEqual(
          request,
        );
      });
      expect(screen.getByText(chip)).toBeVisible();
    },
  );
  it("клик по занятию в блоке «Сегодня» открывает его карточку", async () => {
    const { history } = openApp("/", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByText("Юниоры У10"));

    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);
  });
  it("оставляет обычный заголовок и настройки при всех выключенных виджетах", async () => {
    openApp(
      "/",
      server({ ...displaySettings, dashboard: { ...displaySettings.dashboard, layout: [] } }).fetch,
    );
    const user = userEvent.setup();
    const settingsButton = await screen.findByRole("button", { name: "Настройки главной" });
    expect(screen.getByRole("heading", { name: "Главная" })).toBeVisible();
    expect(
      screen.queryByText(/Доброе утро|Добрый день|Добрый вечер|Здравствуйте/),
    ).not.toBeInTheDocument();
    expect(document.querySelector("time")).not.toBeInTheDocument();
    expect(document.querySelector("main section")?.children).toHaveLength(2);
    await user.click(settingsButton);
    expect(screen.getByRole("dialog", { name: "Настройки главной" })).toBeVisible();
  });

  it("обновляет состояния вечерней ленты в 18:30 без перезагрузки", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 10, 18, 17, 40));
    const fake = appServer({
      "display-settings": () => json(displaySettings),
      "home/today-sessions": () =>
        json({
          date: "2026-11-18",
          sessions: [
            ["16:00", "17:00"],
            ["17:00", "18:30"],
            ["18:00", "19:30"],
            ["19:30", "21:00"],
          ].map(([startTime, endTime], index) => ({
            sessionId: `0199a0b2-7c3e-7d2a-9f10-00000000070${String(index)}`,
            groupName: `Группа ${String(index)}`,
            startTime,
            endTime,
            hallName: "Зал А",
          })),
        }),
      "clients/list": () => json({ clients: [], total: 0 }),
      "home/unclosed-sessions": () => json({ sessions: [], total: 0 }),
    });
    openApp("/", fake.fetch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    const rows = [0, 1, 2, 3].map((index) =>
      screen.getByRole("link", { name: new RegExp(`Группа ${String(index)}`) }),
    );
    expect(rows.map((row) => row.getAttribute("data-state"))).toEqual([
      "past",
      "live",
      "next",
      "none",
    ]);
    expect(within(rows[1] ?? document.body).getByText("идёт")).toBeVisible();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50 * 60_000);
    });
    expect(rows.map((row) => row.getAttribute("data-state"))).toEqual([
      "past",
      "past",
      "live",
      "next",
    ]);
    expect(fake.to("home/today-sessions")).toHaveLength(1);
  });

  it("в полночь обновляет метки и диапазон дней рождения, убирает подсветку вчерашнего ответа", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 10, 18, 23, 59));
    const birthday = ClientListItemSchema.parse({
      id: "0199a0b2-7c3e-7d2a-9f10-000000000902",
      name: "Ева Кузнецова",
      avatarId: null,
      birthday: "2017-11-18",
      gender: "FEMALE",
      groups: [],
      balance: { minorUnits: 0, currency: "RUB" },
      customFields: [],
      contacts: [],
      state: "ACTIVE",
      memberships: [],
      unpaidSessions: 0,
    });
    const fake = appServer({
      "display-settings": () =>
        json({
          ...displaySettings,
          dashboard: {
            ...displaySettings.dashboard,
            widgets: displaySettings.dashboard.widgets.map((widget) =>
              widget.type === "birthdays" ? { ...widget, window: "WEEK" } : widget,
            ),
          },
        }),
      "home/today-sessions": () =>
        json({
          date: "2026-11-18",
          sessions: [
            {
              sessionId,
              groupName: "Вечерняя группа",
              startTime: "22:00",
              endTime: "23:00",
              hallName: "Зал А",
            },
          ],
        }),
      "clients/list": ({ body }) => {
        const request = ClientListRequestSchema.parse(body);
        return json({
          clients:
            request.birthday === undefined
              ? []
              : [
                  birthday,
                  {
                    ...birthday,
                    id: "0199a0b2-7c3e-7d2a-9f10-000000000903",
                    name: "Лев Орлов",
                    birthday: "2014-11-19",
                  },
                ],
          total: request.birthday === undefined ? 0 : 2,
        });
      },
      "home/unclosed-sessions": () =>
        json({
          sessions: [18, 17].map((day) => ({
            sessionId: `0199a0b2-7c3e-7d2a-9f10-0000000008${String(day)}`,
            groupName: `Журнал ${String(day)}`,
            date: `2026-11-${String(day)}`,
            startTime: "16:00",
            endTime: "17:00",
            hallName: "Зал А",
          })),
          total: 2,
        }),
    });
    openApp("/", fake.fetch);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    const eva = screen.getByRole("link", { name: /Ева Кузнецова/ });
    expect(within(eva).getByText("сегодня")).toBeVisible();
    const oldSession = screen.getByRole("link", { name: /Вечерняя группа/ });
    expect(oldSession).toHaveAttribute("data-state", "past");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(screen.getByRole("link", { name: /Журнал 18/ })).toHaveTextContent("вчера");
    expect(screen.getByRole("link", { name: /Журнал 17/ })).toHaveTextContent("2 дня назад");
    expect(screen.getByRole("link", { name: /Ева Кузнецова/ })).not.toHaveTextContent("сегодня");
    expect(screen.getByRole("link", { name: /Лев Орлов/ })).toHaveTextContent("сегодня");
    expect(oldSession).toHaveAttribute("data-state", "none");
    const requests = fake
      .to("clients/list")
      .map((request) => ClientListRequestSchema.parse(request.body))
      .filter((request) => request.birthday !== undefined);
    expect(requests.map((request) => request.birthday)).toEqual([
      { from: "2026-11-18", to: "2026-11-25" },
      { from: "2026-11-19", to: "2026-11-26" },
    ]);
    expect(
      requests.every(
        (request) =>
          JSON.stringify(request.sort) ===
          JSON.stringify([
            { field: "NEXT_BIRTHDAY", direction: "Asc" },
            { field: "NAME", direction: "Asc" },
          ]),
      ),
    ).toBe(true);
  });
});
