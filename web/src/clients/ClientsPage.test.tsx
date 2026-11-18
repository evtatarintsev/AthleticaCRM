import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  ArchiveClientRequestSchema,
  ClientListItemSchema,
  ClientListRequestSchema,
  DisplaySettingsInputSchema,
  DisplaySettingsSchema,
  type ClientListItem,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const alice = ClientListItemSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000201",
  name: "Алиса Иванова",
  avatarId: null,
  birthday: null,
  gender: "FEMALE",
  groups: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000210", name: "Группа А" }],
  balance: { minorUnits: 100_000, currency: "RUB" },
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

const bob = ClientListItemSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000202",
  name: "Борис Смирнов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [],
  balance: { minorUnits: -5_000, currency: "RUB" },
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

const emptyDisplaySettings = DisplaySettingsSchema.parse({
  clients: { columns: [], sort: null, savedViews: [] },
  groups: { columns: [], sort: null, savedViews: [] },
  employees: { columns: [], sort: null, savedViews: [] },
  tasks: { columns: [], sort: null, savedViews: [] },
  dashboard: { widgets: [], layout: [] },
});

/** Сервер со списком клиентов [clients]: фильтрует по имени, полу и задолженности. */
function clientsServer(clients: readonly ClientListItem[]) {
  let settings = emptyDisplaySettings;
  return appServer({
    "clients/list": ({ body }) => {
      const request = ClientListRequestSchema.parse(body);
      const filtered = clients.filter((client) => {
        if (request.name !== null && request.name !== undefined) {
          if (!client.name.toLowerCase().includes(request.name.toLowerCase())) {
            return false;
          }
        }
        if (request.gender !== null && request.gender !== undefined) {
          if (client.gender !== request.gender) {
            return false;
          }
        }
        if (request.hasDebt === true && client.balance.minorUnits >= 0) {
          return false;
        }
        if (request.noGroup === true && client.groups.length > 0) {
          return false;
        }
        return true;
      });
      return json({ clients: filtered, total: filtered.length });
    },
    "custom-fields/list": () => json([]),
    "display-settings": () => json(settings),
    "display-settings/update": ({ body }) => {
      const input = DisplaySettingsInputSchema.parse(body);
      settings = {
        ...settings,
        clients: {
          ...settings.clients,
          columns: input.clients?.columns ?? settings.clients.columns,
        },
      };
      return json(settings);
    },
    "clients/archive": () => json(undefined),
    "clients/restore": () => json(undefined),
  });
}

/** Таблица списка клиентов (широкий экран); карточки на узком экране дублируют текст. */
function table() {
  return within(screen.getByRole("table"));
}

describe("список клиентов", () => {
  it("из виджета сохраняет режим, показывает фильтр и меняет сортировку с клавиатуры", async () => {
    const api = clientsServer([alice, bob]);
    const { history } = openApp("/clients?birthday=todayTomorrow&sort=nextBirthday", api.fetch);
    const user = userEvent.setup();
    await screen.findByRole("table");
    expect(screen.getByText("ДР · Сегодня и завтра")).toBeVisible();
    expect(ClientListRequestSchema.parse(api.to("clients/list").at(-1)?.body).sort).toEqual([
      { field: "NEXT_BIRTHDAY", direction: "Asc" },
      { field: "NAME", direction: "Asc" },
    ]);
    expect(
      table()
        .getAllByRole("columnheader")
        .filter((head) => head.getAttribute("aria-sort") === "ascending"),
    ).toHaveLength(0);
    const name = table().getByRole("button", { name: /Сортировать по.*Имя/ });
    name.focus();
    expect(name).toHaveFocus();
    await user.keyboard("{Enter}");
    await waitFor(() => {
      expect(ClientListRequestSchema.parse(api.to("clients/list").at(-1)?.body)).toMatchObject({
        sortField: "NAME",
        sortDirection: "Asc",
      });
    });
    expect(history.location.search).toContain("birthday=todayTomorrow");
    expect(history.location.search).not.toContain("nextBirthday");
  });

  it("панель показывает период виджета, позволяет заменить его и снять фильтр", async () => {
    const api = clientsServer([alice]);
    const { history } = openApp("/clients?birthday=weekAhead&sort=nextBirthday", api.fetch);
    const user = userEvent.setup();
    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: /Фильтры/ }));
    const sheet = screen.getByRole("dialog", { name: ru["clients.filters"] });
    expect(within(sheet).getByRole("button", { name: "На неделю вперёд" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await user.click(within(sheet).getByRole("button", { name: "Завтра" }));
    await user.click(within(sheet).getByRole("button", { name: ru["clients.filter.apply"] }));
    expect(await screen.findByText("ДР · Завтра")).toBeVisible();
    expect(history.location.search).toContain("birthday=tomorrow");
    await user.click(screen.getByRole("button", { name: /ДР · Завтра/ }));
    await waitFor(() => {
      const request = ClientListRequestSchema.parse(api.to("clients/list").at(-1)?.body);
      expect(request.birthday).toBeUndefined();
      expect(request.sort).toBeUndefined();
      expect(request.sortField).toBe("NAME");
    });
    expect(history.location.search).not.toContain("birthday");
  });

  it("показывает клиентов с числом найденного", async () => {
    openApp("/clients", clientsServer([alice, bob]).fetch);

    expect(await screen.findByRole("table")).toBeVisible();
    expect(table().getByText("Алиса Иванова")).toBeVisible();
    expect(table().getByText("Борис Смирнов")).toBeVisible();
    expect(screen.getByText("2 клиента")).toBeVisible();
  });

  it("поиск фильтрует список и сохраняется в адресе", async () => {
    const api = clientsServer([alice, bob]);
    const { history } = openApp("/clients", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.type(screen.getByRole("searchbox", { name: ru["clients.search"] }), "алис");

    await screen.findByText("1 клиент");
    expect(table().queryByText("Борис Смирнов")).not.toBeInTheDocument();
    expect(history.location.search).toContain("q=");
  });

  it("быстрый вид «Должники» переживает перезагрузку", async () => {
    const api = clientsServer([alice, bob]);
    openApp("/clients?debt=true", api.fetch);

    await screen.findByRole("table");
    expect(table().getByText("Борис Смирнов")).toBeVisible();
    expect(table().queryByText("Алиса Иванова")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: ru["clients.view.debt"] })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("фильтр по полу применяется через панель и виден чипом", async () => {
    const api = clientsServer([alice, bob]);
    openApp("/clients", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: ru["clients.filters"] }));
    const sheet = await screen.findByRole("dialog", { name: ru["clients.filters"] });
    await user.click(
      within(sheet).getByRole("button", { name: ru["clients.filter.genderFemale"] }),
    );
    await user.click(within(sheet).getByRole("button", { name: ru["clients.filter.apply"] }));

    expect(await screen.findByText(ru["clients.filter.genderFemale"])).toBeVisible();
    expect(table().queryByText("Борис Смирнов")).not.toBeInTheDocument();
    const requests = api.to("clients/list").map((r) => ClientListRequestSchema.parse(r.body));
    expect(requests.at(-1)?.gender).toBe("FEMALE");
  });

  it("настройка колонок добавляет колонку «День рождения» и сохраняет её", async () => {
    const api = clientsServer([alice, bob]);
    openApp("/clients", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: ru["clients.columns"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["clients.displaySettings"] });
    await user.click(within(dialog).getByRole("checkbox", { name: ru["clients.column.birthday"] }));

    expect(api.to("display-settings/update").map((r) => r.body)).toEqual([
      { clients: { columns: ["birthday"] } },
    ]);
    await user.keyboard("{Escape}");
    expect(await table().findByText(ru["clients.column.birthday"])).toBeVisible();
  });

  it("массовое архивирование отправляет выбранных клиентов и снимает выбор", async () => {
    const api = clientsServer([alice, bob]);
    openApp("/clients", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(table().getByRole("checkbox", { name: "Выбрать «Алиса Иванова»" }));
    expect(screen.getByText("Выбрано: 1")).toBeVisible();

    await user.click(screen.getByRole("button", { name: ru["clients.archiveSelected"] }));

    expect(api.to("clients/archive").map((r) => ArchiveClientRequestSchema.parse(r.body))).toEqual([
      { clientIds: [alice.id] },
    ]);
    await table().findByText("Алиса Иванова");
    expect(screen.queryByText(/Выбрано/)).not.toBeInTheDocument();
  });

  it("экспортирует клиентов с выбранными полями", async () => {
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:mock");
    vi.spyOn(URL, "revokeObjectURL").mockReturnValue(undefined);
    const api = clientsServer([alice, bob]);
    const fetchWithExport = async (input: string, init: RequestInit) =>
      input.includes("clients/export")
        ? new Response(new Blob(["name,birthday\n"], { type: "text/csv" }))
        : api.fetch(input, init);
    openApp("/clients", fetchWithExport);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(table().getByRole("checkbox", { name: "Выбрать «Алиса Иванова»" }));
    await user.click(screen.getByRole("button", { name: ru["clients.export.action"] }));

    const dialog = await screen.findByRole("dialog", { name: ru["clients.export.title"] });
    await user.click(within(dialog).getByLabelText(ru["clients.column.gender"]));
    await user.click(within(dialog).getByRole("button", { name: ru["clients.export.download"] }));

    await waitFor(() => {
      expect(dialog).not.toBeInTheDocument();
    });
  });
});
