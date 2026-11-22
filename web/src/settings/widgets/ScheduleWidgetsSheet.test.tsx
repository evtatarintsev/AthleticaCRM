import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  CreateScheduleWidgetRequestSchema,
  type ScheduleWidgetDetailResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, json, openApp, session } from "@/test/app";

const adults = { id: "0199a0b2-0000-7000-8000-600000000001", name: "Взрослые" };

/** Сессия с правом управления виджетами. */
const manager = () => json(session("Иван Петров", null, center, ["CAN_MANAGE_WIDGETS"]));

/** Поддельный сервер виджетов: хранит один виджет, созданный запросом `create`. */
function widgetsServer() {
  let widget: ScheduleWidgetDetailResponse | null = null;
  let keys = 0;
  const nextKey = () => {
    keys += 1;
    return `key-${String(keys)}`;
  };
  return appServer({
    "auth/me": manager,
    "groups/list-for-select": () => json([adults]),
    "schedule-widgets/list": () =>
      json({
        widgets:
          widget === null ? [] : [{ id: widget.id, name: widget.name, enabled: widget.enabled }],
      }),
    "schedule-widgets/create": ({ body }) => {
      const request = CreateScheduleWidgetRequestSchema.parse(body);
      widget = {
        id: request.id,
        name: request.name,
        publicKey: nextKey(),
        enabled: request.enabled ?? true,
        horizon: request.horizon ?? 30,
        showCoaches: request.showCoaches ?? false,
        showHall: request.showHall ?? true,
        showDisciplines: request.showDisciplines ?? true,
        excludedGroupIds: request.excludedGroupIds ?? [],
      };
      return json(widget);
    },
    "schedule-widgets/detail": () => json(widget),
    "schedule-widgets/regenerate-key": () => {
      if (widget !== null) {
        widget = { ...widget, publicKey: nextKey() };
      }
      return json(widget);
    },
  });
}

describe("виджеты расписания в настройках", () => {
  it("пункт скрыт без права CAN_MANAGE_WIDGETS и виден с ним", async () => {
    openApp("/settings", appServer({}).fetch);
    expect(
      await screen.findByRole("heading", { level: 1, name: ru["settings.title"] }),
    ).toBeVisible();
    expect(screen.queryByText(ru["settings.itemScheduleWidgets"])).not.toBeInTheDocument();
  });

  it("пункт открывает панель виджетов", async () => {
    openApp(
      "/settings",
      appServer({ "auth/me": manager, "schedule-widgets/list": () => json({ widgets: [] }) }).fetch,
    );
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("link", { name: new RegExp(ru["settings.itemScheduleWidgets"]) }),
    );

    const sheet = await screen.findByRole("dialog", { name: ru["widgets.title"] });
    expect(await within(sheet).findByText(ru["widgets.empty"])).toBeVisible();
  });

  it("создание отправляет настройки, а код iframe следует за перевыпуском ключа", async () => {
    const server = widgetsServer();
    openApp("/settings?panel=schedule-widgets", server.fetch);
    const user = userEvent.setup();

    const list = await screen.findByRole("dialog", { name: ru["widgets.title"] });
    await user.click(within(list).getByRole("button", { name: ru["widgets.create"] }));
    const form = await screen.findByRole("dialog", { name: ru["widgets.createTitle"] });
    await user.type(within(form).getByLabelText(ru["widgets.name"], { exact: false }), "Сайт");
    const horizon = within(form).getByLabelText(ru["widgets.horizon"], { exact: false });
    await user.clear(horizon);
    await user.type(horizon, "14");
    await user.click(within(form).getByRole("checkbox", { name: ru["widgets.showCoaches"] }));
    await user.click(within(form).getByRole("button", { name: ru["groups.detail.editSection"] }));
    const picker = await screen.findByRole("dialog", { name: ru["widgets.excludedGroupsPicker"] });
    await user.click(await within(picker).findByRole("checkbox", { name: adults.name }));
    await user.click(within(picker).getByRole("button", { name: ru["action.done"] }));
    await user.click(within(form).getByRole("button", { name: ru["action.create"] }));

    const card = await screen.findByRole("dialog", { name: ru["widgets.editTitle"] });
    const request = CreateScheduleWidgetRequestSchema.parse(
      server.to("schedule-widgets/create")[0]?.body,
    );
    expect(request).toMatchObject({
      name: "Сайт",
      enabled: true,
      horizon: 14,
      showCoaches: true,
      showHall: true,
      showDisciplines: true,
      excludedGroupIds: [adults.id],
    });
    const embed = await within(card).findByRole("region", { name: ru["widgets.embedTitle"] });
    expect(embed.textContent).toContain('<iframe src="http://localhost:3000/widget/key-1"');
    expect(within(embed).getByTitle(ru["widgets.iframeTitle"])).toHaveAttribute(
      "src",
      "http://localhost:3000/widget/key-1",
    );

    await user.click(within(embed).getByRole("button", { name: ru["widgets.regenerateKey"] }));
    const confirm = await screen.findByRole("dialog", { name: ru["widgets.regenerateTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["widgets.regenerateKey"] }));

    await waitFor(() => {
      expect(embed.textContent).toContain("/widget/key-2");
    });
    expect(embed.textContent).not.toContain("/widget/key-1");
  });
});
