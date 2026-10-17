import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  DashboardWidgetIdSchema,
  DisplaySettingsSchema,
  SessionIdSchema,
  type DashboardSettings,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";
import { withDashboardDefaults } from "./dashboardSettings";

const sessionId = SessionIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000801");
const widgetId = DashboardWidgetIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000802");
const sessionsWidgetId = DashboardWidgetIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000803");

/** Настройки главной, где показан только виджет незакрытых журналов. */
const dashboard: DashboardSettings = {
  widgets: [
    {
      type: "unclosedSessions",
      id: widgetId,
      title: null,
      limit: 1,
    },
  ],
  layout: [widgetId],
};

/** Сервер главной: незакрытые занятия отдаёт [unclosed]. */
function server(unclosed: () => Response) {
  return appServer({
    "display-settings": () =>
      json(
        DisplaySettingsSchema.parse({
          clients: { columns: [], sort: null, savedViews: [] },
          groups: { columns: [], sort: null, savedViews: [] },
          employees: { columns: [], sort: null, savedViews: [] },
          tasks: { columns: [], sort: null, savedViews: [] },
          dashboard,
        }),
      ),
    "home/unclosed-sessions": unclosed,
    "home/today-sessions": () => json({ date: "2026-10-17", sessions: [] }),
    "clients/list": () => json({ clients: [], total: 0 }),
  });
}

describe("виджет «Незакрытые журналы»", () => {
  it("показывает занятия со ссылкой на карточку и общее число сверх лимита", async () => {
    const api = server(() =>
      json({
        sessions: [
          {
            sessionId,
            groupName: "Йога",
            date: "2026-10-16",
            startTime: "18:00",
            endTime: "19:00",
            hallName: "Зал А",
          },
        ],
        total: 3,
      }),
    );
    const { history } = openApp("/", api.fetch);
    const user = userEvent.setup();

    expect(await screen.findByText("Всего незакрытых: 3")).toBeVisible();
    expect(api.to("home/unclosed-sessions")[0]?.query.get("limit")).toBe("1");
    await user.click(screen.getByText("Йога"));
    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);
  });

  it("пустой список", async () => {
    openApp("/", server(() => json({ sessions: [], total: 0 })).fetch);

    expect(await screen.findByText(ru["home.unclosedSessionsEmpty"])).toBeVisible();
    expect(screen.queryByText(/Всего незакрытых/)).toBeNull();
  });

  it("ошибка загрузки", async () => {
    openApp("/", server(() => json({ code: "NOT_FOUND", message: "", fields: null }, 404)).fetch);

    expect(await screen.findByText(ru["home.unclosedSessionsError"])).toBeVisible();
  });

  it("добавляется к настройкам, сохранённым до его появления", () => {
    const completed = withDashboardDefaults({
      widgets: [{ type: "sessions", id: sessionsWidgetId, title: null }],
      layout: [sessionsWidgetId],
    });

    expect(completed.widgets.at(-1)?.type).toBe("unclosedSessions");
    expect(completed.layout.at(-1)).toBe(completed.widgets.at(-1)?.id);
  });
});
