import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientAppBulkInviteRequestSchema,
  type ClientAppSettingsResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, json, openApp, session } from "@/test/app";

/** Сервер настроек кабинета [initial]; включение без способа доставки отклоняется, как на сервере. */
function cabinetServer(initial: ClientAppSettingsResponse) {
  let settings = initial;
  return appServer({
    "auth/me": () => json(session("Иван Петров", null, center, ["CAN_MANAGE_CLIENT_APP"])),
    "client-app-admin/settings": () => json(settings),
    "client-app-admin/set-enabled": () => {
      if (!settings.codeTransportAvailable) {
        return json(
          {
            code: "CLIENT_APP_NO_CODE_TRANSPORT",
            message: "Подключите Telegram Gateway или SMS-канал",
            fields: null,
          },
          400,
        );
      }
      settings = { ...settings, enabled: !settings.enabled };
      return json(settings);
    },
    "client-app-admin/invite-preview": () => json({ ready: 202, withoutPhone: 12 }),
    "client-app-admin/bulk-invite": () => json({ invited: 202, notified: true }),
    "groups/list": () => json({ groups: [], total: 0 }),
  });
}

/** Открывает панель кабинета и возвращает её диалог. */
async function openSheet(fetch: Parameters<typeof openApp>[1]) {
  openApp("/settings?panel=client-app", fetch);
  return screen.findByRole("dialog", { name: ru["clientApp.title"] });
}

describe("настройки «Клиентский кабинет»", () => {
  it("пункт скрыт без права CAN_MANAGE_CLIENT_APP", async () => {
    openApp("/settings", appServer({}).fetch);
    expect(
      await screen.findByRole("heading", { level: 1, name: ru["settings.title"] }),
    ).toBeVisible();
    expect(screen.queryByText(ru["settings.itemClientApp"])).not.toBeInTheDocument();
  });

  it("включение без способа доставки показывает ошибку со ссылками на настройки", async () => {
    const user = userEvent.setup();
    const dialog = await openSheet(
      cabinetServer({ enabled: false, codeTransportAvailable: false }).fetch,
    );

    await user.click(await within(dialog).findByRole("switch"));

    expect(await within(dialog).findByText(/Подключите Telegram Gateway/)).toBeVisible();
    expect(
      within(dialog).getByRole("link", { name: ru["settings.itemTelegramGateway"] }),
    ).toBeVisible();
    expect(within(dialog).getByRole("link", { name: ru["settings.itemChannels"] })).toBeVisible();
  });

  it("включённый кабинет без способа доставки предупреждает", async () => {
    const dialog = await openSheet(
      cabinetServer({ enabled: true, codeTransportAvailable: false }).fetch,
    );

    expect(await within(dialog).findByText(ru["clientApp.noCodeTransport"])).toBeVisible();
  });

  it("массовое приглашение показывает счётчики и приглашает всех", async () => {
    const user = userEvent.setup();
    const server = cabinetServer({ enabled: true, codeTransportAvailable: true });
    const dialog = await openSheet(server.fetch);

    expect(await within(dialog).findByText(/Готовы 202 клиента/)).toBeVisible();
    expect(within(dialog).getByText(/без телефона 12 клиентов/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: ru["clientApp.bulkInvite"] }));

    expect(await within(dialog).findByText("Приглашены 202 клиента")).toBeVisible();
    await waitFor(() => {
      expect(server.to("client-app-admin/bulk-invite")).toHaveLength(1);
    });
    expect(
      ClientAppBulkInviteRequestSchema.parse(server.to("client-app-admin/bulk-invite")[0]?.body),
    ).toEqual({ groupIds: null });
  });
});
