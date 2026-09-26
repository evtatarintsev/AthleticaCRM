import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientContactIdSchema,
  ClientDetailResponseSchema,
  CreateClientRequestSchema,
  CustomFieldDefinitionSchema,
  EditClientRequestSchema,
  type ClientDetailResponse,
  type CustomFieldDefinition,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const leadSource = { id: "0199a0b2-7c3e-7d2a-9f10-000000000401", name: "Инстаграм" };

/** По одному определению на каждый тип дополнительного поля. */
const customFieldDefs: readonly CustomFieldDefinition[] = [
  {
    fieldType: "text",
    fieldKey: "notes",
    label: "Заметка",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
    minLength: null,
    maxLength: null,
  },
  {
    fieldType: "number",
    fieldKey: "weight",
    label: "Вес",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
    minValue: null,
    maxValue: null,
  },
  {
    fieldType: "boolean",
    fieldKey: "vip",
    label: "VIP",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
  },
  {
    fieldType: "date",
    fieldKey: "joined",
    label: "Дата вступления",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
  },
  {
    fieldType: "select",
    fieldKey: "level",
    label: "Уровень",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
    options: ["Новичок", "Профи"],
  },
  {
    fieldType: "phone",
    fieldKey: "extra_phone",
    label: "Доп. телефон",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
  },
  {
    fieldType: "email",
    fieldKey: "extra_email",
    label: "Доп. email",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
  },
  {
    fieldType: "url",
    fieldKey: "site",
    label: "Сайт",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
  },
].map((def) => CustomFieldDefinitionSchema.parse(def));

const alice = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  name: "Алиса Иванова",
  avatarId: null,
  birthday: null,
  gender: "FEMALE",
  groups: [],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000601", type: "PHONE", value: "+70000000001" }],
  state: "ACTIVE",
});
const aliceContact = alice.contacts[0];
if (aliceContact === undefined) {
  throw new Error("alice must have a contact");
}

/** Поддельный сервер: источники, дополнительные поля и клиенты в памяти. */
function clientFormServer(initial: readonly ClientDetailResponse[] = []) {
  let clients = initial;
  return appServer({
    "lead-sources/list": () => json({ leadSources: [leadSource] }),
    "custom-fields/list": () => json(customFieldDefs),
    "clients/list": () => json({ clients, total: clients.length }),
    "display-settings": () =>
      json({
        clients: { columns: [], sort: null, savedViews: [] },
        groups: { columns: [], sort: null, savedViews: [] },
        employees: { columns: [], sort: null, savedViews: [] },
        tasks: { columns: [], sort: null, savedViews: [] },
        dashboard: { widgets: [], layout: [] },
      }),
    "clients/create": ({ body }) => {
      const request = CreateClientRequestSchema.parse(body);
      const client: ClientDetailResponse = {
        id: request.id,
        name: request.name,
        avatarId: request.avatarId ?? null,
        birthday: request.birthday ?? null,
        gender: request.gender,
        groups: [],
        balance: { minorUnits: 0, currency: "RUB" },
        docs: [],
        leadSourceId: request.leadSourceId ?? null,
        customFields: request.customFields ?? [],
        contacts: (request.contacts ?? []).map((contact, index) => ({
          id: ClientContactIdSchema.parse(`0199a0b2-7c3e-7d2a-9f10-00000000070${index.toString()}`),
          ...contact,
        })),
        state: "ACTIVE",
      };
      clients = [...clients, client];
      return json(client);
    },
    "clients/detail": ({ query }) => {
      const client = clients.find((c) => c.id === query.get("id"));
      return client === undefined ? json({ code: "NOT_FOUND", message: "" }, 404) : json(client);
    },
    "clients/edit": ({ body }) => {
      const request = EditClientRequestSchema.parse(body);
      const existing = clients.find((c) => c.id === request.id);
      const updated: ClientDetailResponse = {
        id: request.id,
        name: request.name,
        avatarId: request.avatarId ?? null,
        birthday: request.birthday ?? null,
        gender: request.gender,
        groups: existing?.groups ?? [],
        balance: existing?.balance ?? { minorUnits: 0, currency: "RUB" },
        docs: existing?.docs ?? [],
        leadSourceId: request.leadSourceId ?? null,
        customFields: request.customFields ?? existing?.customFields ?? [],
        contacts: (request.contacts ?? []).map((contact, index) => ({
          id: ClientContactIdSchema.parse(`0199a0b2-7c3e-7d2a-9f10-00000000080${index.toString()}`),
          ...contact,
        })),
        state: existing?.state ?? "ACTIVE",
      };
      clients = clients.map((c) => (c.id === request.id ? updated : c));
      return json(updated);
    },
  });
}

describe("создание клиента", () => {
  it("создаёт клиента с полями каждого типа", async () => {
    const api = clientFormServer();
    const { history } = openApp("/clients/new", api.fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(ru["clients.name"]), "Новый клиент");
    await user.type(screen.getByLabelText(ru["contactType.PHONE"]), "+79990000000");
    await user.selectOptions(screen.getByLabelText(ru["clients.leadSource"]), leadSource.name);
    fireEvent.change(screen.getByLabelText(ru["clients.birthday"]), {
      target: { value: "1990-05-01" },
    });

    await user.type(screen.getByLabelText("Заметка"), "текст заметки");
    await user.type(screen.getByLabelText("Вес"), "70");
    await user.click(screen.getByLabelText("VIP"));
    fireEvent.change(screen.getByLabelText("Дата вступления"), {
      target: { value: "2020-01-01" },
    });
    await user.selectOptions(screen.getByLabelText("Уровень"), "Профи");
    await user.type(screen.getByLabelText("Доп. телефон"), "+79990000001");
    await user.type(screen.getByLabelText("Доп. email"), "a@example.com");
    await user.type(screen.getByLabelText("Сайт"), "https://example.com");

    await user.click(screen.getByRole("button", { name: ru["action.create"] }));

    await screen.findByRole("table");
    expect(history.location.pathname).toBe("/web/clients");

    const request = CreateClientRequestSchema.parse(api.to("clients/create").at(-1)?.body);
    expect(request.name).toBe("Новый клиент");
    expect(request.leadSourceId).toBe(leadSource.id);
    expect(request.birthday).toBe("1990-05-01");
    expect(request.contacts).toEqual([{ type: "PHONE", value: "+79990000000" }]);
    expect(request.customFields).toEqual(
      expect.arrayContaining([
        { type: "text", fieldKey: "notes", value: "текст заметки" },
        { type: "number", fieldKey: "weight", value: 70 },
        { type: "bool", fieldKey: "vip", value: true },
        { type: "date", fieldKey: "joined", value: "2020-01-01" },
        { type: "select", fieldKey: "level", value: "Профи" },
        { type: "text", fieldKey: "extra_phone", value: "+79990000001" },
        { type: "text", fieldKey: "extra_email", value: "a@example.com" },
        { type: "text", fieldKey: "site", value: "https://example.com" },
      ]),
    );
  });
});

describe("редактирование клиента", () => {
  it("подставляет исходные значения и сохраняет изменения", async () => {
    const api = clientFormServer([alice]);
    const { history } = openApp(`/clients/${alice.id}/edit`, api.fetch);
    const user = userEvent.setup();

    const nameField = await screen.findByLabelText(ru["clients.name"]);
    expect(nameField).toHaveValue(alice.name);
    expect(screen.getByLabelText(ru["contactType.PHONE"])).toHaveValue(aliceContact.value);

    await user.clear(nameField);
    await user.type(nameField, "Алиса Смирнова");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await screen.findByRole("table");
    expect(history.location.pathname).toBe("/web/clients");

    const request = EditClientRequestSchema.parse(api.to("clients/edit").at(-1)?.body);
    expect(request.id).toBe(alice.id);
    expect(request.name).toBe("Алиса Смирнова");
    expect(request.contacts).toEqual([{ type: "PHONE", value: aliceContact.value }]);
  });
});
