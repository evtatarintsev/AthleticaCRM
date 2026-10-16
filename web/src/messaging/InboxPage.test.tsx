import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AssignChatRequestSchema, ClientListRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

const integrationId = "0199a0b2-7c3e-7d2a-9f10-000000000601";

const chat = {
  integrationId,
  channelName: "Бот клуба",
  address: "555",
  displayName: "Пётр",
  username: "petr",
  phone: "+79001234567",
  unresolvedCount: 2,
  lastMessageBody: "Можно перенести занятие?",
  lastMessageAt: "2026-10-16T10:00:00Z",
};

const petr = {
  id: "0199a0b2-0000-7000-8000-000000000001",
  name: "Пётр Иванов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [],
  balance: { minorUnits: 0, currency: "RUB" },
  customFields: [],
  contacts: [],
  state: "ACTIVE",
};

/** Сервер «Входящих»: один чат, пока его не привязали; [assign] отвечает на привязку. */
function inboxServer(assign: () => Response = empty) {
  let linked = false;
  return appServer({
    "messaging/triage/list": () => json({ chats: linked ? [] : [chat] }),
    "messaging/triage/chat": () =>
      json({
        messages: [
          {
            id: "0199a0b2-0000-7000-8000-000000000101",
            body: "Здравствуйте",
            receivedAt: "2026-10-16T09:59:00Z",
          },
          {
            id: "0199a0b2-0000-7000-8000-000000000102",
            body: chat.lastMessageBody,
            receivedAt: chat.lastMessageAt,
          },
        ],
      }),
    "clients/list": () => json({ clients: [petr], total: 1 }),
    "messaging/triage/assign": () => {
      const response = assign();
      linked = response.ok;
      return response;
    },
  });
}

/** Открывает «Входящие», выбирает чат и открывает панель привязки. */
async function openLinking(server: ReturnType<typeof inboxServer>) {
  openApp("/inbox", server.fetch);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Пётр/ }));
  expect(await screen.findByText("Здравствуйте")).toBeVisible();
  await user.click(screen.getByRole("button", { name: ru["inbox.link"] }));
  return user;
}

describe("входящие", () => {
  it("поиск клиента предзаполнен присланным номером", async () => {
    const server = inboxServer();
    await openLinking(server);

    expect(await screen.findByRole("searchbox", { name: ru["clientPicker.search"] })).toHaveValue(
      chat.phone,
    );
    await waitFor(() => {
      expect(
        server.to("clients/list").map((r) => ClientListRequestSchema.parse(r.body).name),
      ).toContain(chat.phone);
    });
  });

  it("привязка со страницы убирает чат из списка", async () => {
    const server = inboxServer();
    const user = await openLinking(server);

    await user.click(await screen.findByRole("button", { name: petr.name }));

    expect(await screen.findByText(ru["inbox.empty"])).toBeVisible();
    expect(AssignChatRequestSchema.parse(server.to("messaging/triage/assign")[0]?.body)).toEqual({
      integrationId,
      address: chat.address,
      clientId: petr.id,
    });
  });

  it("уже привязанный чат — ошибка остаётся на панели", async () => {
    const message = "Чат уже привязан к клиенту";
    const server = inboxServer(() =>
      json({ code: "CHAT_ALREADY_LINKED", message, fields: null }, 400),
    );
    const user = await openLinking(server);

    await user.click(await screen.findByRole("button", { name: petr.name }));

    const sheet = await screen.findByRole("dialog");
    expect(await within(sheet).findByText(message)).toBeVisible();
  });
});
