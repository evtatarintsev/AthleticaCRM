import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  InstantSchema,
  type TelegramGatewayBalanceSchema,
  type TelegramGatewaySettingsResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, json, openApp, session } from "@/test/app";

/** Подключённая интеграция с маской `••••1234`; [balance] — остаток из последнего ответа. */
function connected(
  balance: TelegramGatewayBalanceSchema | null = null,
): TelegramGatewaySettingsResponse {
  return { integration: { tokenMask: "••••1234", enabled: true, balance } };
}

/** Сервер с настройками [initial]; токен `revoked` отклоняется как недействительный. */
function gatewayServer(initial: TelegramGatewaySettingsResponse = { integration: null }) {
  let settings = initial;
  return appServer({
    "auth/me": () => json(session("Иван Петров", null, center, ["CAN_MANAGE_TELEGRAM_GATEWAY"])),
    "telegram-gateway/settings": () => json(settings),
    "telegram-gateway/save-token": ({ body }) => {
      if (JSON.stringify(body).includes("revoked")) {
        return json(
          {
            code: "TELEGRAM_GATEWAY_TOKEN_INVALID",
            message: "Telegram Gateway не принял токен",
            fields: null,
          },
          400,
        );
      }
      settings = connected();
      return json(settings);
    },
    "telegram-gateway/set-enabled": () => json(settings),
    "telegram-gateway/test-code": () => json({ outcome: "SENT", reason: null }),
  });
}

/** Открывает панель Telegram Gateway и возвращает её диалог. */
async function openSheet(fetch: Parameters<typeof openApp>[1]) {
  openApp("/settings?panel=telegram-gateway", fetch);
  return screen.findByRole("dialog", { name: ru["telegramGateway.title"] });
}

describe("Telegram Gateway", () => {
  it("пункт скрыт без права CAN_MANAGE_TELEGRAM_GATEWAY", async () => {
    openApp("/settings", appServer({}).fetch);
    expect(
      await screen.findByRole("heading", { level: 1, name: ru["settings.title"] }),
    ).toBeVisible();
    expect(screen.queryByText(ru["settings.itemTelegramGateway"])).not.toBeInTheDocument();
  });

  it("пункт настроек открывает панель", async () => {
    const user = userEvent.setup();
    const { history } = openApp("/settings", gatewayServer().fetch);

    await user.click(
      await screen.findByRole("link", { name: new RegExp(ru["settings.itemTelegramGateway"]) }),
    );

    expect(history.location.search).toContain("panel=telegram-gateway");
    expect(await screen.findByRole("dialog", { name: ru["telegramGateway.title"] })).toBeVisible();
  });

  it("после подключения показывается маска вместо токена, остаток скрыт", async () => {
    const api = gatewayServer();
    const dialog = await openSheet(api.fetch);
    const user = userEvent.setup();

    await user.type(
      await within(dialog).findByLabelText(ru["telegramGateway.token"]),
      "gw-token-abcd1234",
    );
    await user.click(within(dialog).getByRole("button", { name: ru["telegramGateway.connect"] }));

    expect(await within(dialog).findByText("••••1234")).toBeVisible();
    expect(within(dialog).queryByDisplayValue("gw-token-abcd1234")).toBeNull();
    expect(within(dialog).queryByText(/Остаток/)).toBeNull();
    expect(api.to("telegram-gateway/save-token").at(-1)?.body).toEqual({
      token: "gw-token-abcd1234",
    });
  });

  it("недействительный токен — ошибка, поле остаётся открытым", async () => {
    const dialog = await openSheet(gatewayServer().fetch);
    const user = userEvent.setup();

    await user.type(await within(dialog).findByLabelText(ru["telegramGateway.token"]), "revoked");
    await user.click(within(dialog).getByRole("button", { name: ru["telegramGateway.connect"] }));

    expect(await within(dialog).findByText("Telegram Gateway не принял токен")).toBeVisible();
    expect(within(dialog).getByLabelText(ru["telegramGateway.token"])).toBeVisible();
  });

  it("остаток показывается после первого платного ответа", async () => {
    const dialog = await openSheet(
      gatewayServer(
        connected({
          amount: { minorUnits: 427, currency: "USD" },
          at: InstantSchema.parse("2026-11-22T10:15:30Z"),
        }),
      ).fetch,
    );

    expect(await within(dialog).findByText(/Остаток: .*4,27/)).toBeVisible();
  });

  it("тестовая отправка показывает исход", async () => {
    const api = gatewayServer(connected());
    const dialog = await openSheet(api.fetch);
    const user = userEvent.setup();

    await user.type(
      await within(dialog).findByLabelText(ru["telegramGateway.testPhone"]),
      "+7 900 123-45-67",
    );
    await user.click(
      within(dialog).getByRole("button", { name: ru["telegramGateway.sendTestCode"] }),
    );

    expect(await within(dialog).findByRole("status")).toHaveTextContent(
      ru["telegramGateway.outcome.sent"],
    );
    await waitFor(() => {
      expect(api.to("telegram-gateway/test-code").at(-1)?.body).toEqual({
        phone: "+7 900 123-45-67",
      });
    });
  });
});
