import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ReportsTableExportRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp, session, type FakeHandler } from "@/test/app";

const everything = ["CAN_VIEW_REPORTS", "CAN_VIEW_FINANCE", "CAN_VIEW_CLIENT_BALANCE"];

const emptyTable = () =>
  json({
    period: { from: "2026-11-01", to: "2026-11-27" },
    basePeriod: { from: "2026-10-01", to: "2026-10-27" },
    rows: [],
  });

/** Сервер с сессией с правами [permissions] и таблицей показателей [table]. */
function reportsServer(
  permissions: readonly string[],
  table: FakeHandler = emptyTable,
  extra: Readonly<Record<string, FakeHandler>> = {},
) {
  return appServer({
    "auth/me": () => json(session("Иван Петров", null, undefined, permissions)),
    "reports/table": table,
    ...extra,
  });
}

/** Названия карточек отчётов на странице. */
function cardTitles(): readonly string[] {
  return screen.getAllByRole("heading", { level: 3 }).map((h) => h.textContent);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("раздел «Отчёты»", () => {
  it("со всеми правами видны девять заглушек в четырёх группах", async () => {
    openApp("/reports", reportsServer(everything).fetch);
    expect(await screen.findByRole("heading", { name: ru["reports.group.money"] })).toBeVisible();
    expect(cardTitles()).toHaveLength(9);
    expect(screen.getAllByText(ru["reports.soon"])).toHaveLength(9);
    expect(screen.getByRole("heading", { name: ru["reports.group.compare"] })).toBeVisible();
    expect(screen.queryByRole("link", { name: /Выручка/ })).not.toBeInTheDocument();
    expect(await screen.findByText(/1 ноября – 27 ноября/)).toBeInTheDocument();
  });

  it("только с правом на отчёты — пять заглушек без денежных отчётов", async () => {
    openApp("/reports", reportsServer(["CAN_VIEW_REPORTS"]).fetch);
    expect(await screen.findByRole("heading", { name: ru["reports.group.clients"] })).toBeVisible();
    expect(cardTitles()).toEqual([
      ru["reports.sales.name"],
      ru["reports.attendance.name"],
      ru["reports.groups.name"],
      ru["reports.coaches.name"],
      ru["reports.directions.name"],
    ]);
    expect(
      screen.queryByRole("heading", { name: ru["reports.group.money"] }),
    ).not.toBeInTheDocument();
  });

  it("без права — пункта нет, прямой переход отвечает «нет доступа» без запросов", async () => {
    const api = reportsServer([]);
    openApp("/reports", api.fetch);
    expect(await screen.findByText(ru["reports.noAccess"])).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: ru["nav.reports"] })).not.toBeInTheDocument();
    expect(api.to("reports/table")).toHaveLength(0);
  });

  it("с правом пункт «Отчёты» есть в навигации", async () => {
    openApp("/", reportsServer(["CAN_VIEW_REPORTS"]).fetch);
    const [link] = await screen.findAllByRole("link", { name: ru["nav.reports"] });
    expect(link).toHaveAttribute("href", "/reports");
  });

  it("таблица без строк — пустое состояние, выгрузка недоступна", async () => {
    const user = userEvent.setup();
    const { history } = openApp("/reports", reportsServer(everything).fetch);
    await user.click(await screen.findByRole("button", { name: ru["reports.view.table"] }));
    expect(await screen.findByText(ru["reports.table.empty"])).toBeInTheDocument();
    expect(screen.getByRole("button", { name: ru["reports.export.xlsx"] })).toBeDisabled();
    expect(screen.getByRole("button", { name: ru["reports.export.csv"] })).toBeDisabled();
    expect(history.location.search).toContain("view=table");
  });

  it("выгрузка таблицы уходит с текущим фильтром и выбранным форматом", async () => {
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:x");
    vi.spyOn(URL, "revokeObjectURL").mockReturnValue(undefined);
    const api = reportsServer(
      everything,
      () =>
        json({
          period: { from: "2026-10-29", to: "2026-11-27" },
          basePeriod: { from: "2026-09-29", to: "2026-10-28" },
          rows: [
            {
              reportId: "groups",
              label: "Активные клиенты",
              value: { type: "count", count: 352 },
              base: { type: "count", count: 338 },
              better: "up",
            },
          ],
        }),
      { "reports/table/export": () => new Response(new Blob(["x"]), { status: 200 }) },
    );
    openApp("/reports?view=table&period=30d", api.fetch);
    const user = userEvent.setup();
    const row = (await screen.findByText("Активные клиенты")).closest("tr");
    expect(row).not.toBeNull();
    if (row !== null) {
      expect(within(row).getByText("▲ 4,1%")).toBeInTheDocument();
    }
    await user.click(screen.getByRole("button", { name: ru["reports.export.xlsx"] }));
    await waitFor(() => {
      expect(api.to("reports/table/export")).toHaveLength(1);
    });
    const request = ReportsTableExportRequestSchema.parse(api.to("reports/table/export")[0]?.body);
    expect(request.format).toBe("xlsx");
    expect(request.filter?.period).toBe("30d");
  });

  it("экран заглушки или неизвестного отчёта — «не найдено»", async () => {
    openApp("/reports/revenue", reportsServer(everything).fetch);
    expect(await screen.findByText(ru["reports.notFound"])).toBeInTheDocument();
  });

  it("экран отчёта без права на отчёты — «нет доступа»", async () => {
    openApp("/reports/groups", reportsServer([]).fetch);
    expect(await screen.findByText(ru["reports.noAccess"])).toBeInTheDocument();
  });
});
