import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CreateChannelIntegrationRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const bot = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  name: "Бот клуба",
  config: { type: "telegram_bot", botToken: "123:abc", botUsername: "club_bot" },
  enabled: true,
  botLink: "https://t.me/club_bot",
};

describe("каналы связи: Telegram-бот", () => {
  it("показывает ссылку на бота и копирует её", async () => {
    openApp(
      "/settings/channels",
      appServer({ "channels/list": () => json({ channels: [bot] }) }).fetch,
    );
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: ru["channels.editChannel"].replace("{name}", bot.name),
      }),
    );
    const dialog = await screen.findByRole("dialog");

    expect(within(dialog).getByRole("link", { name: bot.botLink })).toHaveAttribute(
      "href",
      bot.botLink,
    );
    await user.click(within(dialog).getByRole("button", { name: ru["channels.copyBotLink"] }));
    expect(await navigator.clipboard.readText()).toBe(bot.botLink);
  });

  it("при недействительном токене показывает ошибку и не закрывает диалог", async () => {
    const message = "Telegram не принял токен бота — проверьте его в BotFather";
    const server = appServer({
      "channels/list": () => json({ channels: [] }),
      "channels/create": () => json({ code: "TELEGRAM_TOKEN_INVALID", message, fields: null }, 400),
    });
    openApp("/settings/channels", server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["channels.create"] }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(ru["channels.field.name"]), "Бот");
    await user.selectOptions(
      within(dialog).getByLabelText(ru["channels.field.provider"]),
      "telegram_bot",
    );
    await user.type(within(dialog).getByLabelText(ru["channels.field.botToken"]), "bad");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await within(dialog).findByText(message)).toBeVisible();
    const request = CreateChannelIntegrationRequestSchema.parse(
      server.to("channels/create")[0]?.body,
    );
    expect(request.config).toEqual({ type: "telegram_bot", botToken: "bad" });
  });
});
