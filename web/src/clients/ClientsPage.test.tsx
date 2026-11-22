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
  type DisplaySettings,
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
  memberships: [],
  unpaidSessions: 0,
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
  memberships: [],
  unpaidSessions: 0,
});

const emptyDisplaySettings = DisplaySettingsSchema.parse({
  clients: { columns: [], sort: null, savedViews: [] },
  groups: { columns: [], sort: null, savedViews: [] },
  employees: { columns: [], sort: null, savedViews: [] },
  tasks: { columns: [], sort: null, savedViews: [] },
  dashboard: { widgets: [], layout: [] },
});

/** Настройки отображения нового сотрудника: колонки по умолчанию с сервера. */
const defaultDisplaySettings = DisplaySettingsSchema.parse({
  ...emptyDisplaySettings,
  clients: { columns: ["groups", "memberships", "balance"], sort: null, savedViews: [] },
});

/**
 * Сервер со списком клиентов [clients] и настройками отображения [initialSettings]:
 * фильтрует по имени, полу и задолженности.
 */
function clientsServer(
  clients: readonly ClientListItem[],
  initialSettings: DisplaySettings = emptyDisplaySettings,
) {
  let settings = initialSettings;
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
    const heading = screen.getByRole("heading", { level: 1, name: ru["clients.title"] });
    expect(heading.parentElement).toHaveTextContent("2 клиента");
    expect(
      within(screen.getByRole("group", { name: ru["clients.viewsLabel"] })).queryByText(/клиент/),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: ru["clients.create"] })).toHaveAttribute(
      "href",
      "/clients/new",
    );
  });

  it("пустой вид «Должники» показывает «Должников нет»", async () => {
    openApp("/clients?debt=true", clientsServer([alice]).fetch);

    expect(await screen.findByText(ru["clients.emptyView.debt"])).toBeVisible();
    expect(
      screen.queryByRole("button", { name: ru["clients.resetFilters"] }),
    ).not.toBeInTheDocument();
  });

  it("«Сбросить фильтры» после пустого поиска очищает поиск и сохраняет сортировку", async () => {
    const api = clientsServer([alice, bob]);
    const { history } = openApp("/clients?q=зз&noGroup=true&sort=balance", api.fetch);
    const user = userEvent.setup();

    expect(await screen.findByText(ru["clients.noResults"])).toBeVisible();
    await user.click(screen.getByRole("button", { name: ru["clients.resetFilters"] }));

    await screen.findByRole("table");
    expect(screen.getByRole("searchbox", { name: ru["clients.search"] })).toHaveValue("");
    expect(screen.getByRole("button", { name: ru["clients.view.all"] })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(history.location.search).toBe("?sort=balance");
  });

  it("отметка чекбокса подсвечивает строку и не уводит в карточку", async () => {
    const { history } = openApp("/clients", clientsServer([alice, bob]).fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    const checkbox = table().getByRole("checkbox", { name: "Выбрать «Алиса Иванова»" });
    await user.click(checkbox);

    expect(checkbox.closest("tr")).toHaveAttribute("data-state", "selected");
    expect(history.location.pathname).toBe("/clients");
  });

  it("три группы — чипы-ссылки друг под другом без «+N», чип ведёт на страницу группы", async () => {
    const groups = [
      { id: "0199a0b2-7c3e-7d2a-9f10-000000000211", name: "Младшая группа" },
      { id: "0199a0b2-7c3e-7d2a-9f10-000000000212", name: "ОФП дети" },
      { id: "0199a0b2-7c3e-7d2a-9f10-000000000213", name: "Плавание" },
    ];
    const api = clientsServer([ClientListItemSchema.parse({ ...alice, groups })]);
    const { history } = openApp("/clients", api.fetch);
    const user = userEvent.setup();
    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: ru["clients.columns"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["clients.displaySettings"] });
    await user.click(within(dialog).getByRole("checkbox", { name: ru["clients.column.groups"] }));
    await user.keyboard("{Escape}");

    const chip = await table().findByRole("link", { name: "ОФП дети" });
    expect(table().getByRole("link", { name: "Младшая группа" })).toBeVisible();
    expect(table().getByRole("link", { name: "Плавание" })).toBeVisible();
    expect(table().queryByText("+1")).not.toBeInTheDocument();

    await user.click(chip);
    await waitFor(() => {
      expect(history.location.pathname).toBe(`/groups/${groups[1]?.id ?? ""}`);
    });
  });

  it("под именем пол и возраст, без даты рождения — только пол", async () => {
    vi.useFakeTimers({ toFake: ["Date"], now: new Date(2026, 10, 20, 12) });
    try {
      const anna = ClientListItemSchema.parse({ ...alice, birthday: "2017-05-10" });
      openApp("/clients", clientsServer([anna, bob]).fetch);

      await screen.findByRole("table");
      expect(await table().findByText("Ж · 9 лет")).toBeVisible();
      expect(table().getByText("М")).toBeVisible();
    } finally {
      vi.useRealTimers();
    }
  });

  it("колонка «Абонемент»: метки друг под другом в порядке сервера, без абонемента — «Нет абонемента»", async () => {
    const mark = ClientListItemSchema.parse({
      ...alice,
      memberships: [
        {
          id: "0199a0b2-7c3e-7d2a-9f10-000000000301",
          name: "Младшая 8",
          sessionsTotal: 8,
          sessionsRemaining: 1,
          state: {
            type: "active",
            startDate: "2026-11-01",
            endDate: "2026-12-01",
            termPosition: "IN_TERM",
            endingSoon: ["BY_SESSIONS"],
            closePending: false,
          },
        },
        {
          id: "0199a0b2-7c3e-7d2a-9f10-000000000302",
          name: "ОФП 8",
          sessionsTotal: 8,
          sessionsRemaining: 5,
          state: {
            type: "active",
            startDate: "2026-11-20",
            endDate: "2026-12-20",
            termPosition: "IN_TERM",
            endingSoon: [],
            closePending: false,
          },
        },
      ],
    });
    openApp("/clients", clientsServer([mark, bob], defaultDisplaySettings).fetch);

    await screen.findByRole("table");
    const ending = await table().findByText("Младшая 8 · Заканчивается · 1 из 8");
    const ok = table().getByText("ОФП 8 · 5 из 8 · до 20.12");
    expect(ending.compareDocumentPosition(ok) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(ending.closest("[data-tone]")).toHaveAttribute("data-tone", "warn");
    expect(ok.closest("[data-tone]")).toHaveAttribute("data-tone", "ok");
    expect(table().getByText(ru["clients.membership.none"])).toBeVisible();
  });

  it("неоплаченные занятия красным под балансом, без них — ничего", async () => {
    const debtor = ClientListItemSchema.parse({
      ...bob,
      balance: { minorUnits: -120_000, currency: "RUB" },
      unpaidSessions: 2,
    });
    openApp("/clients", clientsServer([alice, debtor], defaultDisplaySettings).fetch);

    await screen.findByRole("table");
    const unpaid = await table().findByText("2 неоплач.");
    expect(unpaid).toHaveClass("text-destructive");
    expect(table().getAllByText(/неоплач\./)).toHaveLength(1);
  });

  it("заголовки таблицы: сортировка по имени и балансу, по дате рождения — нет", async () => {
    openApp("/clients", clientsServer([alice, bob], defaultDisplaySettings).fetch);

    await screen.findByRole("table");
    const sortButtons = table()
      .getAllByRole("button", { name: /Сортировать по/ })
      .map((button) => button.textContent);
    expect(sortButtons).toEqual([
      expect.stringContaining(ru["clients.column.name"]),
      expect.stringContaining(ru["clients.column.balance"]),
    ]);
    expect(table().getByRole("columnheader", { name: ru["clients.column.groups"] })).toBeVisible();
    expect(
      table().getByRole("columnheader", { name: ru["clients.column.memberships"] }),
    ).toBeVisible();
    expect(
      table().queryByRole("columnheader", { name: ru["clients.column.birthday"] }),
    ).not.toBeInTheDocument();
  });

  it("строка на телефоне: группы и метки на подложке, долг — красная метка, редактирования нет", async () => {
    const anna = ClientListItemSchema.parse({
      ...alice,
      groups: [
        { id: "0199a0b2-7c3e-7d2a-9f10-000000000214", name: "Мини" },
        { id: "0199a0b2-7c3e-7d2a-9f10-000000000215", name: "Юниор" },
      ],
      memberships: [
        {
          id: "0199a0b2-7c3e-7d2a-9f10-000000000303",
          name: "Юниор безлимит",
          sessionsTotal: null,
          sessionsRemaining: null,
          state: {
            type: "active",
            startDate: "2026-11-01",
            endDate: "2026-12-31",
            termPosition: "IN_TERM",
            endingSoon: [],
            closePending: false,
          },
        },
      ],
    });
    openApp("/clients", clientsServer([anna, bob], defaultDisplaySettings).fetch);

    const list = within(await screen.findByRole("list", { name: ru["clients.title"] }));
    expect(list.getByText(/[-−]50,00/)).toHaveAttribute("data-balance", "neg");
    expect(list.getByText(/1\s000,00/)).toHaveAttribute("data-balance", "pos");
    expect(list.getByText("Мини")).toBeVisible();
    expect(list.getByText("Юниор")).toBeVisible();
    expect(list.getByText("Юниор безлимит · Безлимит · до 31.12")).toBeVisible();
    expect(list.queryByRole("link", { name: /Редактировать/ })).not.toBeInTheDocument();
  });

  it("на телефоне при выключенном «Абонементе» на подложке только группы", async () => {
    const settings = DisplaySettingsSchema.parse({
      ...emptyDisplaySettings,
      clients: { columns: ["groups", "balance"], sort: null, savedViews: [] },
    });
    openApp("/clients", clientsServer([alice, bob], settings).fetch);

    const list = within(await screen.findByRole("list", { name: ru["clients.title"] }));
    expect(list.getByText("Группа А")).toBeVisible();
    expect(list.queryByText(ru["clients.membership.none"])).not.toBeInTheDocument();
  });

  it("на телефоне без «Групп» и «Абонемента» подложки нет", async () => {
    openApp("/clients", clientsServer([alice, bob]).fetch);

    const list = within(await screen.findByRole("list", { name: ru["clients.title"] }));
    expect(list.queryByText("Группа А")).not.toBeInTheDocument();
    expect(list.queryByText(ru["clients.membership.none"])).not.toBeInTheDocument();
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

  it("новый сотрудник видит колонки по умолчанию «Группы», «Абонемент», «Баланс»", async () => {
    openApp("/clients", clientsServer([alice], defaultDisplaySettings).fetch);

    await screen.findByRole("table");
    const headers = table()
      .getAllByRole("columnheader")
      .map((header) => header.textContent)
      .filter((text) => text !== "");
    expect(headers).toEqual([
      expect.stringContaining(ru["clients.column.name"]),
      ru["clients.column.groups"],
      ru["clients.column.memberships"],
      expect.stringContaining(ru["clients.column.balance"]),
    ]);
  });

  it("«Колонки» предлагают «Группы», «Абонемент», «Баланс» без пола и даты рождения", async () => {
    const api = clientsServer([alice, bob]);
    openApp("/clients", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: ru["clients.columns"] }));
    const dialog = within(
      await screen.findByRole("dialog", { name: ru["clients.displaySettings"] }),
    );
    expect(dialog.getByRole("checkbox", { name: ru["clients.column.groups"] })).toBeVisible();
    expect(dialog.getByRole("checkbox", { name: ru["clients.column.balance"] })).toBeVisible();
    expect(
      dialog.queryByRole("checkbox", { name: ru["clients.column.gender"] }),
    ).not.toBeInTheDocument();
    expect(
      dialog.queryByRole("checkbox", { name: ru["clients.column.birthday"] }),
    ).not.toBeInTheDocument();
    await user.click(dialog.getByRole("checkbox", { name: ru["clients.column.memberships"] }));

    expect(api.to("display-settings/update").map((r) => r.body)).toEqual([
      { clients: { columns: ["memberships"] } },
    ]);
    await user.keyboard("{Escape}");
    expect(
      await table().findByRole("columnheader", { name: ru["clients.column.memberships"] }),
    ).toBeVisible();
  });

  it("выключенный «Абонемент» убирает колонку, остальные на месте", async () => {
    const api = clientsServer([alice, bob], defaultDisplaySettings);
    openApp("/clients", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: ru["clients.columns"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["clients.displaySettings"] });
    await user.click(
      within(dialog).getByRole("checkbox", { name: ru["clients.column.memberships"] }),
    );
    await user.keyboard("{Escape}");

    expect(api.to("display-settings/update").map((r) => r.body)).toEqual([
      { clients: { columns: ["groups", "balance"] } },
    ]);
    await waitFor(() => {
      expect(
        table().queryByRole("columnheader", { name: ru["clients.column.memberships"] }),
      ).not.toBeInTheDocument();
    });
    expect(table().getByRole("columnheader", { name: ru["clients.column.groups"] })).toBeVisible();
    expect(table().queryByText(ru["clients.membership.none"])).not.toBeInTheDocument();
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
    expect(within(dialog).getByLabelText(ru["clients.column.birthday"])).toBeVisible();
    await user.click(within(dialog).getByLabelText(ru["clients.column.gender"]));
    await user.click(within(dialog).getByRole("button", { name: ru["clients.export.download"] }));

    await waitFor(() => {
      expect(dialog).not.toBeInTheDocument();
    });
  });
});
