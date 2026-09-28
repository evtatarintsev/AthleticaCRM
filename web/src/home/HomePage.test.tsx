import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
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

function server() {
  return appServer({
    "display-settings": () => json(displaySettings),
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

describe("главная", () => {
  it("клик по занятию в блоке «Сегодня» открывает его карточку", async () => {
    const { history } = openApp("/", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByText("Юниоры У10"));

    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);
  });
});
