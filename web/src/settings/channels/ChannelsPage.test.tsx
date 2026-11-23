import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  CreateChannelIntegrationRequestSchema,
  SetChannelEnabledRequestSchema,
  UpdateChannelIntegrationRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

const bot = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  name: "Бот клуба",
  settings: { type: "telegram_bot", tokenMask: "••••a1B9", botUsername: "club_bot" },
  enabled: true,
  share: { url: "https://t.me/club_bot" },
};

const sms = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000502",
  name: "SMS-рассылка",
  settings: { type: "smsc_sms", login: "club" },
  enabled: true,
  share: null,
};

const aero = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000503",
  name: "SMS Aero",
  settings: {
    type: "sms_aero_sms",
    email: "club@example.com",
    sign: "Dinamo",
    apiKeyMask: "••••Xy7Q",
  },
  enabled: true,
  share: null,
};

/** Подпись из словаря [key] с подставленным именем канала [name]. */
function named(
  key: "channels.editChannel" | "channels.toggleChannel" | "channels.shareChannel",
  name: string,
): string {
  return ru[key].replace("{name}", name);
}

describe("каналы связи: создание в панели", () => {
  it("создаёт Telegram-бота через выбор провайдера", async () => {
    const server = appServer({
      "channels/list": () => json({ channels: [] }),
      "channels/create": () => empty(),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["channels.create"] }));
    const sheet = await screen.findByRole("dialog");
    await user.click(
      within(sheet).getByRole("button", { name: ru["channels.provider.telegram_bot"] }),
    );
    await user.type(within(sheet).getByLabelText(ru["channels.field.name"]), "Бот");
    await user.type(within(sheet).getByLabelText(ru["channels.field.botToken"]), " 123:abc ");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    const request = CreateChannelIntegrationRequestSchema.parse(
      server.to("channels/create")[0]?.body,
    );
    expect(request.name).toBe("Бот");
    expect(request.config).toEqual({ type: "telegram_bot", botToken: "123:abc" });
  });

  it("при недействительном токене показывает ошибку и не закрывает панель", async () => {
    const message = "Telegram не принял токен бота — проверьте его в BotFather";
    const server = appServer({
      "channels/list": () => json({ channels: [] }),
      "channels/create": () => json({ code: "TELEGRAM_TOKEN_INVALID", message, fields: null }, 400),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["channels.create"] }));
    const sheet = await screen.findByRole("dialog");
    await user.click(
      within(sheet).getByRole("button", { name: ru["channels.provider.telegram_bot"] }),
    );
    await user.type(within(sheet).getByLabelText(ru["channels.field.name"]), "Бот");
    await user.type(within(sheet).getByLabelText(ru["channels.field.botToken"]), "bad");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await within(sheet).findByText(message)).toBeVisible();
  });

  it("возврат к выбору провайдера из заполненной формы спрашивает подтверждение", async () => {
    openApp(
      "/settings/channels",
      appServer({ "channels/list": () => json({ channels: [] }) }).fetch,
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["channels.create"] }));
    const sheet = await screen.findByRole("dialog");
    await user.click(within(sheet).getByRole("button", { name: ru["channels.provider.smsc_sms"] }));
    await user.type(within(sheet).getByLabelText(ru["channels.field.login"]), "club");
    await user.click(within(sheet).getByRole("button", { name: ru["action.back"] }));
    await user.click(await screen.findByRole("button", { name: ru["editSheet.discardConfirm"] }));

    expect(
      await within(sheet).findByRole("button", { name: ru["channels.provider.twilio_sms"] }),
    ).toBeVisible();
  });

  it("создаёт канал SMS Aero", async () => {
    const server = appServer({
      "channels/list": () => json({ channels: [] }),
      "channels/create": () => empty(),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["channels.create"] }));
    const sheet = await screen.findByRole("dialog");
    await user.click(
      within(sheet).getByRole("button", { name: ru["channels.provider.sms_aero_sms"] }),
    );
    await user.type(within(sheet).getByLabelText(ru["channels.field.name"]), "SMS");
    await user.type(within(sheet).getByLabelText(ru["channels.field.email"]), " club@example.com ");
    await user.type(within(sheet).getByLabelText(ru["channels.field.apiKey"]), "key");
    await user.type(within(sheet).getByLabelText(ru["channels.field.sign"]), " Dinamo ");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    const request = CreateChannelIntegrationRequestSchema.parse(
      server.to("channels/create")[0]?.body,
    );
    expect(request.config).toEqual({
      type: "sms_aero_sms",
      email: "club@example.com",
      apiKey: "key",
      sign: "Dinamo",
    });
  });

  it("ошибка проверки SMS Aero оставляет панель открытой с введёнными данными", async () => {
    const message = "Подпись Dinamo ещё не одобрена в SMS Aero";
    const server = appServer({
      "channels/list": () => json({ channels: [] }),
      "channels/create": () =>
        json({ code: "SMS_AERO_SIGN_NOT_APPROVED", message, fields: null }, 400),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["channels.create"] }));
    const sheet = await screen.findByRole("dialog");
    await user.click(
      within(sheet).getByRole("button", { name: ru["channels.provider.sms_aero_sms"] }),
    );
    await user.type(within(sheet).getByLabelText(ru["channels.field.name"]), "SMS");
    await user.type(within(sheet).getByLabelText(ru["channels.field.email"]), "club@example.com");
    await user.type(within(sheet).getByLabelText(ru["channels.field.apiKey"]), "key");
    await user.type(within(sheet).getByLabelText(ru["channels.field.sign"]), "Dinamo");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await within(sheet).findByText(message)).toBeVisible();
    expect(within(sheet).getByLabelText(ru["channels.field.sign"])).toHaveValue("Dinamo");
    expect(within(sheet).getByLabelText(ru["channels.field.apiKey"])).toHaveValue("key");
  });
});

describe("каналы связи: изменение в панели", () => {
  it("открывает форму провайдера без выбора и не отправляет пустой токен", async () => {
    const server = appServer({
      "channels/list": () => json({ channels: [bot] }),
      "channels/update": () => empty(),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: named("channels.editChannel", bot.name) }),
    );
    const sheet = await screen.findByRole("dialog");
    const token = within(sheet).getByLabelText(ru["channels.field.botToken"]);

    expect(token).toHaveValue("");
    expect(
      within(sheet).getByText(ru["channels.secretKeepHint"].replace("{mask}", "••••a1B9")),
    ).toBeVisible();
    expect(within(sheet).queryByRole("button", { name: ru["action.back"] })).toBeNull();

    const name = within(sheet).getByLabelText(ru["channels.field.name"]);
    await user.clear(name);
    await user.type(name, "Новый бот");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(server.to("channels/update")).toHaveLength(1);
    });
    const request = UpdateChannelIntegrationRequestSchema.parse(
      server.to("channels/update")[0]?.body,
    );
    expect(request).toEqual({
      id: bot.id,
      name: "Новый бот",
      config: { type: "telegram_bot", botToken: null },
    });
  });

  it("изменение SMS Aero без ключа не отправляет ключ", async () => {
    const server = appServer({
      "channels/list": () => json({ channels: [aero] }),
      "channels/update": () => empty(),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: named("channels.editChannel", aero.name) }),
    );
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByLabelText(ru["channels.field.apiKey"])).toHaveValue("");
    expect(
      within(sheet).getByText(ru["channels.secretKeepHint"].replace("{mask}", "••••Xy7Q")),
    ).toBeVisible();

    const sign = within(sheet).getByLabelText(ru["channels.field.sign"]);
    await user.clear(sign);
    await user.type(sign, "Spartak");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(server.to("channels/update")).toHaveLength(1);
    });
    expect(
      UpdateChannelIntegrationRequestSchema.parse(server.to("channels/update")[0]?.body),
    ).toEqual({
      id: aero.id,
      name: aero.name,
      config: { type: "sms_aero_sms", email: "club@example.com", apiKey: null, sign: "Spartak" },
    });
  });
});

describe("каналы связи: переключатель", () => {
  it("выключает канал отдельной командой", async () => {
    const server = appServer({
      "channels/list": () => json({ channels: [bot] }),
      "channels/set-enabled": () => empty(),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    const toggle = await screen.findByRole("switch", {
      name: named("channels.toggleChannel", bot.name),
    });
    await user.click(toggle);

    await waitFor(() => {
      expect(server.to("channels/set-enabled")).toHaveLength(1);
    });
    expect(
      SetChannelEnabledRequestSchema.parse(server.to("channels/set-enabled")[0]?.body),
    ).toEqual({ id: bot.id, enabled: false });
  });

  it("при ошибке возвращается в прежнее положение", async () => {
    const message = "Интеграция канала не найдена";
    const server = appServer({
      "channels/list": () => json({ channels: [bot] }),
      "channels/set-enabled": () => json({ code: "CHANNEL_NOT_FOUND", message, fields: null }, 404),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    const toggle = await screen.findByRole("switch", {
      name: named("channels.toggleChannel", bot.name),
    });
    await user.click(toggle);

    await waitFor(() => {
      expect(toast.error).toHaveBeenLastCalledWith(message);
    });
    expect(toggle).toBeChecked();
  });
});

describe("каналы связи: поделиться", () => {
  it("показывает ссылку и QR-код бота и копирует ссылку", async () => {
    openApp(
      "/settings/channels",
      appServer({ "channels/list": () => json({ channels: [bot] }) }).fetch,
    );
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: named("channels.shareChannel", bot.name) }),
    );
    const dialog = await screen.findByRole("dialog");

    expect(within(dialog).getByRole("link", { name: bot.share.url })).toHaveAttribute(
      "href",
      bot.share.url,
    );
    expect(
      within(dialog).getByRole("img", {
        name: ru["channels.qrCode"].replace("{url}", bot.share.url),
      }),
    ).toBeVisible();
    expect(within(dialog).getByRole("button", { name: ru["channels.downloadQr"] })).toBeVisible();
    expect(within(dialog).queryByRole("button", { name: ru["channels.systemShare"] })).toBeNull();

    await user.click(within(dialog).getByRole("button", { name: ru["channels.copyLink"] }));
    expect(await navigator.clipboard.readText()).toBe(bot.share.url);
  });

  it("есть у выключенного бота и отсутствует у канала без ссылки", async () => {
    openApp(
      "/settings/channels",
      appServer({
        "channels/list": () => json({ channels: [{ ...bot, enabled: false }, sms] }),
      }).fetch,
    );

    expect(
      await screen.findByRole("button", { name: named("channels.shareChannel", bot.name) }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: named("channels.shareChannel", sms.name) }),
    ).toBeNull();
  });
});
