import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AssignChatRequestSchema,
  ClientListRequestSchema,
  MarkConversationReadRequestSchema,
  SendMessageRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

const integrationId = "0199a0b2-7c3e-7d2a-9f10-000000000601";
const ivanovId = "0199a0b2-0000-7000-8000-000000000002";

const chat = {
  type: "unresolved",
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

const ivanov = {
  type: "client",
  clientId: ivanovId,
  clientName: "Иванов Сергей",
  lastMessageBody: "Ждём вас в среду",
  lastMessageAt: "2026-10-16T09:00:00Z",
  lastMessageDirection: "OUTBOUND",
  unreadCount: 0,
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

const inbound = (id: string, body: string, createdAt: string) => ({
  type: "inbound",
  id,
  body,
  createdAt,
  receivedVia: integrationId,
});

const telegram = {
  id: integrationId,
  name: "Бот клуба",
  provider: "telegram_bot",
  available: true,
  contacts: [],
};

/**
 * Сервер «Входящих»: беседа Иванова и неразобранный чат Петра, пока его не привязали;
 * [assign] отвечает на привязку. После привязки чат становится беседой клиента Петра.
 */
function inboxServer(assign: () => Response = empty) {
  let linked = false;
  let sent: string | null = null;
  const petrConversation = {
    type: "client",
    clientId: petr.id,
    clientName: petr.name,
    lastMessageBody: chat.lastMessageBody,
    lastMessageAt: chat.lastMessageAt,
    lastMessageDirection: "INBOUND",
    unreadCount: 2,
  };
  return appServer({
    "messaging/inbox/list": ({ query }) => {
      const items = linked ? [petrConversation, ivanov] : [chat, ivanov];
      const filter = query.get("filter");
      const filtered =
        filter === "UNRESOLVED" ? items.filter((item) => item.type === "unresolved") : items;
      return json({ items: filtered, total: filtered.length });
    },
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
    "messaging/conversation": ({ query }) => {
      const clientId = query.get("clientId");
      const messages =
        clientId === petr.id
          ? [
              inbound(
                "0199a0b2-0000-7000-8000-000000000201",
                "Здравствуйте",
                "2026-10-16T09:59:00Z",
              ),
              inbound(
                "0199a0b2-0000-7000-8000-000000000202",
                chat.lastMessageBody,
                chat.lastMessageAt,
              ),
            ]
          : [
              inbound(
                "0199a0b2-0000-7000-8000-000000000301",
                "Добрый день",
                "2026-10-16T08:00:00Z",
              ),
            ];
      return json({ clientId, messages });
    },
    "messaging/channels": () => json({ channels: [telegram] }),
    "messaging/conversation/read": empty,
    "messaging/send": ({ body }) => {
      sent = SendMessageRequestSchema.parse(body).body;
      return json({
        clientId: ivanovId,
        messages: [
          inbound("0199a0b2-0000-7000-8000-000000000301", "Добрый день", "2026-10-16T08:00:00Z"),
          {
            type: "outbound",
            id: "0199a0b2-0000-7000-8000-000000000302",
            body: sent,
            createdAt: "2026-10-16T11:00:00Z",
            authorEmployeeId: null,
            deliveries: [],
          },
        ],
      });
    },
    "clients/list": () => json({ clients: [petr], total: 1 }),
    "messaging/triage/assign": () => {
      const response = assign();
      linked = response.ok;
      return response;
    },
  });
}

/** Отметки прочтения, ушедшие на сервер [server]. */
function readMarks(server: ReturnType<typeof inboxServer>) {
  return server
    .to("messaging/conversation/read")
    .map((r) => MarkConversationReadRequestSchema.parse(r.body));
}

/** Открывает «Входящие», выбирает неразобранный чат и открывает панель привязки. */
async function openLinking(server: ReturnType<typeof inboxServer>) {
  openApp("/inbox", server.fetch);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Пётр/ }));
  expect(await screen.findByText("Здравствуйте")).toBeVisible();
  await user.click(screen.getByRole("button", { name: ru["inbox.link"] }));
  return user;
}

describe("входящие", () => {
  it("показывает беседы клиентов и неразобранные чаты в одном списке", async () => {
    openApp("/inbox", inboxServer().fetch);

    const list = await screen.findByRole("list", { name: ru["inbox.list"] });
    const rows = within(list).getAllByRole("button");
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringContaining("Пётр"),
      expect.stringContaining(ivanov.clientName),
    ]);
    expect(within(list).getByText(`${ru["inbox.you"]}: ${ivanov.lastMessageBody}`)).toBeVisible();
  });

  it("фильтр «Неразобранные» запрашивает только неразобранные чаты", async () => {
    const server = inboxServer();
    openApp("/inbox", server.fetch);
    const user = userEvent.setup();
    await screen.findByRole("list", { name: ru["inbox.list"] });

    await user.click(screen.getByRole("button", { name: ru["inbox.filterUnresolved"] }));

    await waitFor(() => {
      expect(screen.queryByText(ivanov.clientName)).not.toBeInTheDocument();
    });
    expect(server.to("messaging/inbox/list").map((r) => r.query.get("filter"))).toContain(
      "UNRESOLVED",
    );
  });

  it("открытая беседа отмечается прочитанной до последнего показанного сообщения", async () => {
    const server = inboxServer();
    openApp("/inbox", server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: new RegExp(ivanov.clientName) }));

    expect(await screen.findByText("Добрый день")).toBeVisible();
    await waitFor(() => {
      expect(readMarks(server)).toEqual([{ clientId: ivanovId, upTo: "2026-10-16T08:00:00Z" }]);
    });
  });

  it("ответ клиенту уходит со страницы и попадает в ленту", async () => {
    const server = inboxServer();
    openApp(`/inbox?client=${ivanovId}`, server.fetch);
    const user = userEvent.setup();
    expect(await screen.findByText("Добрый день")).toBeVisible();
    const listRequests = server.to("messaging/inbox/list").length;

    await user.type(screen.getByPlaceholderText(ru["messaging.placeholder"]), "Ждём в пятницу");
    await user.click(screen.getByRole("button", { name: ru["messaging.send"] }));

    expect(await screen.findByText("Ждём в пятницу")).toBeVisible();
    expect(SendMessageRequestSchema.parse(server.to("messaging/send")[0]?.body)).toMatchObject({
      clientId: ivanovId,
      channelIntegrationId: integrationId,
      body: "Ждём в пятницу",
    });
    await waitFor(() => {
      expect(server.to("messaging/inbox/list").length).toBeGreaterThan(listRequests);
    });
    await waitFor(() => {
      expect(readMarks(server).map((mark) => mark.upTo)).toEqual([
        "2026-10-16T08:00:00Z",
        "2026-10-16T11:00:00Z",
      ]);
    });
  });

  it("ссылка в открытой беседе ведёт в карточку клиента", async () => {
    openApp(`/inbox?client=${ivanovId}`, inboxServer().fetch);

    const link = await screen.findByRole("link", { name: ru["inbox.openClient"] });

    expect(link).toHaveAttribute("href", `/clients/${ivanovId}`);
  });

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

  it("после привязки на месте чата открывается беседа клиента", async () => {
    const server = inboxServer();
    const user = await openLinking(server);

    await user.click(await screen.findByRole("button", { name: petr.name }));

    expect(AssignChatRequestSchema.parse(server.to("messaging/triage/assign")[0]?.body)).toEqual({
      integrationId,
      address: chat.address,
      clientId: petr.id,
    });
    const list = await screen.findByRole("list", { name: ru["inbox.list"] });
    await waitFor(() => {
      expect(within(list).queryByText(ru["inbox.unresolved"], { exact: false })).toBeNull();
    });
    expect(await screen.findByRole("link", { name: ru["inbox.openClient"] })).toHaveAttribute(
      "href",
      `/clients/${petr.id}`,
    );
    expect(screen.getByPlaceholderText(ru["messaging.placeholder"])).toBeVisible();
    await waitFor(() => {
      expect(readMarks(server)).toContainEqual({ clientId: petr.id, upTo: chat.lastMessageAt });
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

  it("счётчик у пункта «Входящие» показывает, что требует внимания", async () => {
    const server = appServer({
      "messaging/inbox/counter": () => json({ count: 3 }),
      "messaging/inbox/list": () => json({ items: [], total: 0 }),
    });
    openApp("/inbox", server.fetch);

    const badges = await screen.findAllByLabelText(ru["inbox.navBadge"].replace("{count}", "3"));

    expect(badges[0]).toHaveTextContent("3");
  });
});
