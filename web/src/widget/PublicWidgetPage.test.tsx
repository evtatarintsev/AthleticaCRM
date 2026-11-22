import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { fakeServer, json, openApp } from "@/test/app";
import { stubMatchMedia } from "@/test/media";

/** Занятие публичного расписания на дату [date] с правками [overrides]. */
const session = (date: string, overrides: Record<string, unknown> = {}) => ({
  date,
  startTime: "18:00",
  endTime: "19:00",
  groupName: "Взрослые",
  hall: "Большой зал",
  disciplines: ["Бокс"],
  coaches: null,
  cancelled: false,
  ...overrides,
});

/**
 * Поддельный сервер виджета: сегодня четверг 26.11.2026, период показа — с понедельника 23.11
 * по 06.12, то есть ровно две недели. [sessions] — занятия периода.
 */
function widgetServer(sessions: readonly unknown[]) {
  return fakeServer({
    "public/schedule-widget": () =>
      json({ today: "2026-11-26", periodStart: "2026-11-23", periodEnd: "2026-12-06", sessions }),
  });
}

describe("публичная страница виджета", () => {
  it("на широком экране показывает неделю и листает только в пределах периода", async () => {
    stubMatchMedia(true);
    const server = widgetServer([
      session("2026-11-23"),
      session("2026-11-26", { groupName: "Дети", cancelled: true }),
      session("2026-12-01", { groupName: "Юниоры" }),
    ]);
    openApp("/widget/secret-key", server.fetch);
    const user = userEvent.setup();

    expect(await screen.findByText("Взрослые")).toBeVisible();
    const cancelled = screen.getByText("Дети").closest("li");
    expect(cancelled).not.toBeNull();
    expect(within(cancelled ?? document.body).getByText(ru["schedule.cancelled"])).toBeVisible();
    expect(screen.getByRole("button", { name: ru["schedule.prevWeek"] })).toBeDisabled();
    expect(screen.queryByText("Юниоры")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: ru["schedule.nextWeek"] }));

    expect(await screen.findByText("Юниоры")).toBeVisible();
    expect(screen.getByRole("button", { name: ru["schedule.nextWeek"] })).toBeDisabled();
    expect(server.to("public/schedule-widget")[0]?.query.get("key")).toBe("secret-key");
    expect(server.to("auth/me")).toHaveLength(0);
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });

  it("выключенные поля не выводятся, включённые — выводятся", async () => {
    stubMatchMedia(true);
    openApp(
      "/widget/k",
      widgetServer([
        session("2026-11-24", { hall: null, disciplines: null, coaches: null }),
        session("2026-11-25", { groupName: "Дети", hall: "Малый зал", coaches: ["Анна"] }),
      ]).fetch,
    );

    const bare = (await screen.findByText("Взрослые")).closest("li");
    expect(bare?.textContent).not.toContain("зал");
    expect(bare?.textContent).not.toContain("Бокс");
    const full = screen.getByText("Дети").closest("li");
    expect(full?.textContent).toContain("Малый зал");
    expect(full?.textContent).toContain("Анна");
    expect(full?.textContent).toContain("Бокс");
  });

  it("на узком экране показывает день и сообщает о дне без занятий", async () => {
    stubMatchMedia(false);
    openApp("/widget/k", widgetServer([session("2026-11-26"), session("2026-11-24")]).fetch);
    const user = userEvent.setup();

    expect(await screen.findByText("Взрослые")).toBeVisible();
    expect(screen.getAllByText("Взрослые")).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: ru["schedule.nextDay"] }));

    expect(await screen.findByText(ru["widget.emptyDay"])).toBeVisible();
  });

  it("недоступный виджет показывает «расписание недоступно»", async () => {
    stubMatchMedia(true);
    openApp(
      "/widget/unknown",
      fakeServer({
        "public/schedule-widget": () =>
          json({ code: "WIDGET_UNAVAILABLE", message: "Расписание недоступно" }, 400),
      }).fetch,
    );

    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent(ru["widget.unavailable"]);
    });
  });
});
