import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AddClientsToGroupRequestSchema,
  ClientDetailResponseSchema,
  GroupIdSchema,
  RemoveClientFromGroupRequestSchema,
  type ClientDetailResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

const morning = {
  id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000901"),
  name: "Утренняя группа",
};
const evening = {
  id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000902"),
  name: "Вечерняя группа",
};

const carl = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000503",
  name: "Карл Орлов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [morning],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

function groupsServer(initial: ClientDetailResponse) {
  let client = initial;
  return appServer({
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "groups/list-for-select": () => json([morning, evening]),
    "clients/add-to-group": ({ body }) => {
      const request = AddClientsToGroupRequestSchema.parse(body);
      const group = [morning, evening].find((candidate) => candidate.id === request.groupId);
      if (group !== undefined) {
        client = { ...client, groups: [...client.groups, group] };
      }
      return empty();
    },
    "clients/remove-from-group": ({ body }) => {
      const request = RemoveClientFromGroupRequestSchema.parse(body);
      client = { ...client, groups: client.groups.filter((g) => g.id !== request.groupId) };
      return empty();
    },
  });
}

describe("группы клиента", () => {
  it("добавляет клиента в новую группу и убирает из существующей", async () => {
    const api = groupsServer(carl);
    openApp(`/clients/${carl.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByText(morning.name);
    await user.click(screen.getByRole("button", { name: ru["clients.detail.addToGroup"] }));
    await screen.findByRole("dialog", { name: ru["clients.detail.addToGroup"] });
    await user.click(await screen.findByRole("button", { name: evening.name }));

    await screen.findByText(evening.name);
    const addRequest = AddClientsToGroupRequestSchema.parse(
      api.to("clients/add-to-group").at(-1)?.body,
    );
    expect(addRequest.groupId).toBe(evening.id);

    await user.click(
      screen.getByRole("button", {
        name: ru["clients.detail.removeFromGroupAria"].replace("{name}", morning.name),
      }),
    );
    await waitFor(() => {
      expect(screen.queryByText(morning.name)).not.toBeInTheDocument();
    });
  });
});
