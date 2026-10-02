import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  AddClientsToGroupRequestSchema,
  ClientListItemSchema,
  GroupDetailResponseSchema,
  RemoveClientFromGroupRequestSchema,
  type GroupDetailResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

/** Клиент списка с порядковым номером [n] и именем [name]. */
const client = (n: number, name: string) =>
  ClientListItemSchema.parse({
    id: `0199a0b2-0000-7000-8000-50000000000${String(n)}`,
    name,
    avatarId: null,
    birthday: null,
    gender: "FEMALE",
    groups: [],
    balance: { minorUnits: 0, currency: "RUB" },
    customFields: [],
    contacts: [],
    state: "ACTIVE",
  });

const anna = client(1, "Анна Белова");
const vera = client(2, "Вера Котова");
const gleb = client(3, "Глеб Носов");

const juniors = GroupDetailResponseSchema.parse({
  id: "0199a0b2-0000-7000-8000-600000000001",
  name: "Юниоры",
  schedule: [],
  scheduleChangeAt: null,
  disciplines: [],
  employees: [],
  clients: [{ id: anna.id, name: anna.name }],
});

/**
 * Поддельный сервер карточки группы: хранит состав и применяет к нему добавление и удаление
 * так же, как сервер. [failRemove] — удаление отвечает бизнес-ошибкой.
 */
function groupServer(initial: GroupDetailResponse, failRemove = false) {
  let group = initial;
  const all = [anna, vera, gleb];
  return appServer({
    "groups/detail": () => json(group),
    "disciplines/list": () => json({ disciplines: [] }),
    "employees/list": () => json({ employees: [], total: 0 }),
    "halls/list": () => json({ halls: [] }),
    "clients/list": () => json({ clients: all, total: all.length }),
    "clients/add-to-group": ({ body }) => {
      const request = AddClientsToGroupRequestSchema.parse(body);
      const added = all
        .filter((c) => request.clientIds.includes(c.id))
        .map((c) => ({ id: c.id, name: c.name }));
      group = { ...group, clients: [...group.clients, ...added] };
      return empty();
    },
    "clients/remove-from-group": ({ body }) => {
      if (failRemove) {
        return json({ code: "GROUP_NOT_FOUND", message: "Группа не найдена", fields: null }, 404);
      }
      const request = RemoveClientFromGroupRequestSchema.parse(body);
      group = { ...group, clients: group.clients.filter((c) => !request.clientIds.includes(c.id)) };
      return empty();
    },
  });
}

/** Секция «Клиенты» карточки группы. */
const clientsSection = () =>
  screen.getByRole("heading", { name: ru["groups.detail.clientsTitle"] }).closest("section");

describe("состав группы в карточке", () => {
  it("добавляет нескольких клиентов одним запросом; клиент из состава недоступен", async () => {
    const server = groupServer(juniors);
    openApp(`/groups/${juniors.id}`, server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["groups.detail.addClients"] }));
    const sheet = await screen.findByRole("dialog", { name: ru["groups.detail.addClients"] });
    expect(await within(sheet).findByRole("checkbox", { name: /Анна Белова/ })).toBeDisabled();
    expect(within(sheet).getByText(ru["groups.detail.alreadyInGroup"])).toBeVisible();

    await user.click(within(sheet).getByRole("checkbox", { name: vera.name }));
    await user.click(within(sheet).getByRole("checkbox", { name: gleb.name }));
    await user.click(within(sheet).getByRole("button", { name: ru["action.add"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    const requests = server.to("clients/add-to-group");
    expect(requests).toHaveLength(1);
    expect(AddClientsToGroupRequestSchema.parse(requests[0]?.body)).toEqual({
      groupId: juniors.id,
      clientIds: [vera.id, gleb.id],
    });
    const section = clientsSection();
    expect(section).not.toBeNull();
    if (section !== null) {
      expect(await within(section).findByRole("link", { name: vera.name })).toBeVisible();
      expect(within(section).getByRole("link", { name: gleb.name })).toBeVisible();
    }
    expect(toast.success).toHaveBeenCalledWith(
      ru["groups.detail.clientsAdded"].replace("{count}", "2"),
    );
  });

  it("убирает клиента из состава крестиком", async () => {
    const server = groupServer(juniors);
    openApp(`/groups/${juniors.id}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByRole("link", { name: anna.name });
    await user.click(
      screen.getByRole("button", {
        name: ru["groups.detail.removeClientAria"].replace("{name}", anna.name),
      }),
    );

    expect(await screen.findByText(ru["groups.detail.clientsEmpty"])).toBeVisible();
    expect(
      RemoveClientFromGroupRequestSchema.parse(server.to("clients/remove-from-group")[0]?.body),
    ).toEqual({ groupId: juniors.id, clientIds: [anna.id] });
  });

  it("при ошибке удаления оставляет клиента в составе", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors, true).fetch);
    const user = userEvent.setup();

    await screen.findByRole("link", { name: anna.name });
    await user.click(
      screen.getByRole("button", {
        name: ru["groups.detail.removeClientAria"].replace("{name}", anna.name),
      }),
    );

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Группа не найдена");
    });
    expect(screen.getByRole("link", { name: anna.name })).toBeVisible();
  });
});
