import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientAppClientAccessResponseSchema,
  ClientAppPhoneRequestSchema,
  ClientDetailResponseSchema,
  type ClientAppClientAccessResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

const ivan = ClientDetailResponseSchema.parse({
  createdAt: "2024-01-01T10:00:00Z",
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  name: "Иван Петров",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

/** Мама подключена, папа приглашён только что, бабушка нажала «Это не я». */
const family = ClientAppClientAccessResponseSchema.parse({
  enabled: true,
  phones: [
    {
      phone: "79030000001",
      status: "CONNECTED",
      invitedAt: "2026-11-01T10:00:00Z",
      inviteChannel: "SMS",
      statusChangedAt: "2026-11-01T11:00:00Z",
      lastSeenAt: "2026-11-20T09:00:00Z",
      remindAvailableAt: null,
    },
    {
      phone: "79030000002",
      status: "INVITED",
      invitedAt: "2099-01-01T10:00:00Z",
      inviteChannel: null,
      statusChangedAt: "2099-01-01T10:00:00Z",
      lastSeenAt: null,
      remindAvailableAt: "2099-01-02T10:00:00Z",
    },
    {
      phone: "79030000003",
      status: "NOT_ME",
      invitedAt: "2026-11-01T10:00:00Z",
      inviteChannel: "SMS",
      statusChangedAt: "2026-11-02T10:00:00Z",
      lastSeenAt: null,
      remindAvailableAt: null,
    },
  ],
});

/** Сервер карточки Ивана с доступами [access]; «Пригласить» отвечает [notified]. */
function cabinetServer(access: ClientAppClientAccessResponse, notified = true) {
  return appServer({
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(ivan),
    "clients/notes/list": () => json({ notes: [] }),
    "client-app-admin/client": () => json(access),
    "client-app-admin/invite": () => json({ notified }),
    "client-app-admin/disable": () => empty(),
  });
}

/** Открывает карточку Ивана и возвращает блок «Клиентский кабинет». */
async function openBlock(fetch: Parameters<typeof openApp>[1]) {
  openApp(`/clients/${ivan.id}`, fetch);
  const heading = await screen.findByRole("heading", { name: ru["clientApp.title"] });
  const block = heading.closest("section");
  if (block === null) {
    throw new Error("Блок не найден");
  }
  return block;
}

describe("блок «Клиентский кабинет» в карточке клиента", () => {
  it("показывает статус каждого номера и доступные действия", async () => {
    const block = await openBlock(cabinetServer(family).fetch);

    const rows = await within(block).findAllByRole("listitem");
    expect(rows).toHaveLength(3);
    expect(within(rows[0] ?? block).getByText(ru["clientApp.status.CONNECTED"])).toBeVisible();
    expect(within(rows[1] ?? block).getByText(ru["clientApp.status.INVITED"])).toBeVisible();
    expect(within(rows[2] ?? block).getByText(ru["clientApp.status.NOT_ME"])).toBeVisible();
    expect(
      within(rows[1] ?? block).getByRole("button", { name: ru["clientApp.remind"] }),
    ).toBeDisabled();
    expect(within(rows[1] ?? block).getByText(/Можно повторить/)).toBeVisible();
    expect(
      within(rows[2] ?? block).queryByRole("button", { name: ru["clientApp.disable"] }),
    ).not.toBeInTheDocument();
    expect(within(block).getByRole("button", { name: ru["clientApp.invite"] })).toBeVisible();
  });

  it("«Пригласить» без канала сообщает, что уведомление не отправлено", async () => {
    const user = userEvent.setup();
    const block = await openBlock(cabinetServer(family, false).fetch);

    await user.click(await within(block).findByRole("button", { name: ru["clientApp.invite"] }));

    expect(await within(block).findByText(ru["clientApp.notNotified"])).toBeVisible();
  });

  it("«Отключить» отправляет номер", async () => {
    const user = userEvent.setup();
    const server = cabinetServer(family);
    const block = await openBlock(server.fetch);

    const rows = await within(block).findAllByRole("listitem");
    await user.click(
      within(rows[0] ?? block).getByRole("button", { name: ru["clientApp.disable"] }),
    );

    await waitFor(() => {
      expect(server.to("client-app-admin/disable")).toHaveLength(1);
    });
    expect(
      ClientAppPhoneRequestSchema.parse(server.to("client-app-admin/disable")[0]?.body),
    ).toEqual({
      clientId: ivan.id,
      phone: "79030000001",
    });
  });

  it("кабинет выключен — действий нет", async () => {
    const block = await openBlock(cabinetServer({ ...family, enabled: false }).fetch);

    expect(await within(block).findByText(ru["clientApp.disabledHint"])).toBeVisible();
    expect(within(block).queryByRole("button")).not.toBeInTheDocument();
  });
});
