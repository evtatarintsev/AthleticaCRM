import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  AddClientsToGroupRequestSchema,
  ClientListItemSchema,
  EditGroupRequestSchema,
  EmployeeListItemSchema,
  GroupDetailResponseSchema,
  GroupDisciplineSchema,
  GroupAttendanceResponseSchema,
  GroupRosterClientSchema,
  GroupSessionSchemaSchema,
  HallDetailResponseSchema,
  RemoveClientFromGroupRequestSchema,
  SetGroupDisciplinesRequestSchema,
  SetGroupEmployeesRequestSchema,
  SetGroupScheduleRequestSchema,
  type ClientId,
  type GroupAttendanceResponse,
  type GroupDetailResponse,
  type GroupRosterClient,
  type GroupSessionSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { appServer, empty, json, openApp } from "@/test/app";
import { activeState, testMembership } from "@/test/memberships";

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

/** Сотрудник списка с порядковым номером [n] и именем [name]. */
const employee = (n: number, name: string, isActive = true) =>
  EmployeeListItemSchema.parse({
    id: `0199a0b2-0000-7000-8000-70000000000${String(n)}`,
    name,
    avatarId: null,
    isOwner: false,
    isActive,
    joinedAt: "2026-01-01T00:00:00Z",
    roles: [],
    phoneNo: null,
    email: null,
  });

const ivanov = employee(1, "Иванов");
const kozlov = employee(2, "Козлов", false);
const sidorova = employee(3, "Сидорова");

const boxing = GroupDisciplineSchema.parse({
  id: "0199a0b2-0000-7000-8000-800000000001",
  name: "Бокс",
});
const judo = GroupDisciplineSchema.parse({
  id: "0199a0b2-0000-7000-8000-800000000002",
  name: "Дзюдо",
});

const bigHall = HallDetailResponseSchema.parse({
  id: "0199a0b2-0000-7000-8000-900000000001",
  name: "Большой",
});

const juniors = GroupDetailResponseSchema.parse({
  id: "0199a0b2-0000-7000-8000-600000000001",
  name: "Юниоры",
  schedule: [],
  scheduleChangeAt: null,
  disciplines: [],
  employees: [],
});

const membershipId = "0199a0b2-0000-7000-8000-b00000000001";

/** Участник состава [id], [name] с абонементом «8 занятий» в порядке и правками [overrides]. */
const rosterClient = (
  id: string,
  name: string,
  overrides: Partial<GroupRosterClient> = {},
): GroupRosterClient => ({
  ...GroupRosterClientSchema.parse({
    id,
    name,
    avatarId: null,
    membership: {
      id: membershipId,
      name: "8 занятий",
      activated: true,
      sessionsTotal: 8,
      sessionsRemaining: 5,
      endDate: "2026-12-01",
      endingSoon: [],
      closePending: false,
    },
    hasNextMembership: false,
    unpaidSessions: 0,
    canRepay: false,
    balance: { minorUnits: 0, currency: "RUB" },
    attention: null,
  }),
  ...overrides,
});

/** Ответ бизнес-ошибкой «группа не найдена». */
const groupNotFound = () =>
  json({ code: "GROUP_NOT_FOUND", message: "Группа не найдена", fields: null }, 404);

/** Пустая посещаемость. */
const noAttendance: GroupAttendanceResponse = { sessions: [], participants: [] };

/**
 * Поддельный сервер карточки группы: хранит группу и применяет к ней изменения так же, как
 * сервер. [failRemove] — удаление клиента отвечает бизнес-ошибкой, [failSet] — замена
 * дисциплин, тренеров и расписания. Состав строится из участников [members] (по умолчанию —
 * Анна) с правками [roster] по идентификатору; [next] — ближайшее занятие, [attendance] — посещаемость группы.
 */
function groupServer(
  initial: GroupDetailResponse,
  {
    failRemove = false,
    failSet = false,
    members: initialMembers = [anna],
    roster = {},
    next = null,
    attendance = noAttendance,
  }: {
    failRemove?: boolean;
    failSet?: boolean;
    members?: readonly { readonly id: ClientId; readonly name: string }[];
    roster?: Readonly<Record<string, Partial<GroupRosterClient>>>;
    next?: GroupSessionSchema | null;
    attendance?: GroupAttendanceResponse;
  } = {},
) {
  let group = initial;
  let members = initialMembers.map((c) => ({ id: c.id, name: c.name }));
  const all = [anna, vera, gleb];
  const staff = [ivanov, kozlov, sidorova];
  const disciplines = [boxing, judo];
  return appServer({
    "groups/detail": () => json(group),
    "groups/roster": () =>
      json({ clients: members.map((c) => rosterClient(c.id, c.name, roster[c.id])) }),
    "groups/sessions": () => json({ sessions: next === null ? [] : [next], last: null, next }),
    "attendance/group": () => json(attendance),
    "disciplines/list": () => json({ disciplines }),
    "employees/list": () => json({ employees: staff, total: staff.length }),
    "groups/set-disciplines": ({ body }) => {
      if (failSet) {
        return groupNotFound();
      }
      const request = SetGroupDisciplinesRequestSchema.parse(body);
      group = {
        ...group,
        disciplines: disciplines.filter((d) => request.disciplineIds.includes(d.id)),
      };
      return empty();
    },
    "groups/set-employees": ({ body }) => {
      if (failSet) {
        return groupNotFound();
      }
      const request = SetGroupEmployeesRequestSchema.parse(body);
      group = {
        ...group,
        employees: staff
          .filter((e) => request.employeeIds.includes(e.id))
          .map((e) => ({ id: e.id, name: e.name, avatarId: e.avatarId })),
      };
      return empty();
    },
    "groups/edit": ({ body }) => {
      if (failSet) {
        return groupNotFound();
      }
      const request = EditGroupRequestSchema.parse(body);
      group = { ...group, name: request.name };
      return json(group);
    },
    "halls/list": () => json({ halls: [bigHall] }),
    "groups/set-schedule": ({ body }) => {
      if (failSet) {
        return groupNotFound();
      }
      const request = SetGroupScheduleRequestSchema.parse(body);
      group = {
        ...group,
        schedule: (request.slots ?? []).map((slot) => ({
          ...slot,
          hallName: bigHall.name,
          validity: null,
        })),
      };
      return json(group);
    },
    "clients/list": () => json({ clients: all, total: all.length }),
    "clients/add-to-group": ({ body }) => {
      const request = AddClientsToGroupRequestSchema.parse(body);
      const added = all
        .filter((c) => request.clientIds.includes(c.id))
        .map((c) => ({ id: c.id, name: c.name }));
      members = [...members, ...added];
      return empty();
    },
    "clients/remove-from-group": ({ body }) => {
      if (failRemove) {
        return groupNotFound();
      }
      const request = RemoveClientFromGroupRequestSchema.parse(body);
      members = members.filter((c) => !request.clientIds.includes(c.id));
      return empty();
    },
    "memberships/list": () =>
      json({
        memberships: [
          testMembership({
            id: membershipId,
            sessionsRemaining: 5,
            state: activeState("2026-11-01", "2026-12-01"),
          }),
        ],
      }),
  });
}

/** Панель активной вкладки. */
const activePanel = () => screen.getByRole("tabpanel");

/** Вкладка [name] полосы вкладок. */
const tab = (name: string) => screen.getByRole("tab", { name: new RegExp(name) });

/** Открывает меню «⋯» шапки и выбирает пункт [item]. */
async function chooseMenu(user: ReturnType<typeof userEvent.setup>, item: string) {
  await user.click(await screen.findByRole("button", { name: ru["groups.detail.more"] }));
  await user.click(await screen.findByRole("menuitem", { name: item }));
}

/**
 * Секция карточки группы с заголовком [title]. Ищется и среди скрытых элементов: пока открыта
 * панель, страница под ней недоступна вспомогательным технологиям, а заголовок панели может
 * совпадать с заголовком секции.
 */
const section = (title: string) => {
  const found = screen
    .getAllByRole("heading", { name: title, hidden: true })
    .map((heading) => heading.closest("section"))
    .find((candidate) => candidate !== null);
  if (!(found instanceof HTMLElement)) {
    throw new Error(`Нет секции «${title}»`);
  }
  return found;
};

describe("вкладки карточки группы", () => {
  it("без параметра активна вкладка «Обзор»", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);

    expect(await screen.findByRole("tab", { name: ru["groups.tabs.overview"] })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("выбор вкладки пишет её в адрес, «Назад» возвращает предыдущую", async () => {
    const { history } = openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("tab", { name: ru["groups.tabs.journal"] }));

    expect(history.location.search).toBe("?tab=journal");
    history.back();
    await waitFor(() => {
      expect(tab(ru["groups.tabs.overview"])).toHaveAttribute("aria-selected", "true");
    });
  });

  it("некорректный параметр — «Обзор» без ошибки", async () => {
    openApp(`/groups/${juniors.id}?tab=unknown`, groupServer(juniors).fetch);

    expect(await screen.findByRole("tab", { name: ru["groups.tabs.overview"] })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.queryByRole("heading", { name: ru["notFound.title"] })).not.toBeInTheDocument();
  });

  it("у вкладки «Клиенты» есть индикатор, если кто-то требует внимания", async () => {
    openApp(
      `/groups/${juniors.id}`,
      groupServer(juniors, { roster: { [anna.id]: { attention: "NO_MEMBERSHIP" } } }).fetch,
    );

    await waitFor(() => {
      expect(tab(ru["groups.tabs.clients"])).toHaveTextContent(ru["groups.tabs.attentionMark"]);
    });
  });
});

/** Ближайшее занятие группы. */
const nextSession = GroupSessionSchemaSchema.parse({
  id: "0199a0b2-0000-7000-8000-a00000000001",
  date: addDays(todayLocalDate(), 2),
  startTime: "18:00",
  endTime: "19:30",
  hall: { id: bigHall.id, name: bigHall.name },
  coaches: [{ id: ivanov.id, name: ivanov.name }],
  coachesOverridden: false,
  status: "SCHEDULED",
  rescheduledFrom: null,
  isManual: false,
  attendance: null,
});

describe("сводка на вкладке «Обзор»", () => {
  it("плитка «Следующее занятие» ведёт в журнал занятия", async () => {
    const { history } = openApp(
      `/groups/${juniors.id}`,
      groupServer(juniors, { next: nextSession }).fetch,
    );
    const user = userEvent.setup();

    await screen.findByText(/18:00–19:30 · Большой/);
    const tile = section(ru["groups.overview.nextTitle"]);
    expect(within(tile).getByText(ivanov.name)).toBeVisible();
    await user.click(
      within(tile).getByRole("link", { name: ru["groups.overview.markAttendance"] }),
    );

    expect(history.location.pathname).toBe(`/sessions/${nextSession.id}`);
  });

  it("без ближайшего занятия — «Занятий не запланировано» и «Изменить расписание»", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);
    const user = userEvent.setup();

    await screen.findByText(ru["groups.overview.noNext"]);
    const tile = section(ru["groups.overview.nextTitle"]);
    await user.click(within(tile).getByRole("button", { name: ru["groups.detail.editSchedule"] }));

    expect(await screen.findByRole("dialog", { name: ru["groups.schedule.title"] })).toBeVisible();
  });

  it("«Требуют внимания» показывает число с разбивкой и открывает состав с фильтром", async () => {
    const group = juniors;
    openApp(
      `/groups/${group.id}`,
      groupServer(group, {
        members: [anna, vera, gleb],
        roster: {
          [anna.id]: { attention: "DEBT" },
          [vera.id]: { attention: "DEBT" },
          [gleb.id]: { attention: "MEMBERSHIP_ENDING" },
        },
      }).fetch,
    );
    const user = userEvent.setup();

    const title = await screen.findByRole("heading", {
      name: ru["groups.overview.attentionTitle"],
    });
    const tile = title.closest("button");
    if (tile === null) {
      throw new Error("Нет плитки");
    }
    expect(await within(tile).findByText("3")).toBeVisible();
    expect(within(tile).getByText("Долг: 2")).toBeVisible();
    expect(within(tile).getByText("Абонемент заканчивается: 1")).toBeVisible();
    await user.click(tile);

    expect(tab(ru["groups.tabs.clients"])).toHaveAttribute("aria-selected", "true");
    expect(
      screen.getByRole("button", { name: new RegExp(ru["groups.clients.filterAttention"]) }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  it("все в порядке — «Все в порядке»", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);

    expect(await screen.findByText(ru["groups.overview.allOk"])).toBeVisible();
  });

  it("без закрытых занятий — «Нет закрытых занятий», плитка открывает «Журнал»", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByText(ru["groups.overview.noClosed"]));

    expect(tab(ru["groups.tabs.journal"])).toHaveAttribute("aria-selected", "true");
  });

  it("карточка «Расписание» показывает слоты и пометку о смене расписания", async () => {
    const group = GroupDetailResponseSchema.parse({
      ...juniors,
      scheduleChangeAt: "2026-12-01",
      schedule: [
        {
          dayOfWeek: "MONDAY",
          startAt: "18:00",
          endAt: "19:30",
          hallId: bigHall.id,
          hallName: bigHall.name,
          validity: null,
        },
      ],
    });
    openApp(`/groups/${group.id}`, groupServer(group).fetch);

    await screen.findByText(/Смена расписания с/);
    const card = section(ru["groups.detail.scheduleTitle"]);
    expect(within(card).getAllByText(bigHall.name)).not.toHaveLength(0);
  });
});

describe("состав группы на вкладке «Клиенты»", () => {
  it("добавляет нескольких клиентов одним запросом; клиент из состава недоступен", async () => {
    const server = groupServer(juniors);
    openApp(`/groups/${juniors.id}?tab=clients`, server.fetch);
    const user = userEvent.setup();

    await screen.findByRole("link", { name: anna.name });
    const [addAction] = screen.getAllByRole("button", { name: ru["groups.detail.addClients"] });
    if (addAction === undefined) {
      throw new Error("Нет действия добавления");
    }
    await user.click(addAction);
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
    expect(await within(activePanel()).findByRole("link", { name: vera.name })).toBeVisible();
    expect(within(activePanel()).getByRole("link", { name: gleb.name })).toBeVisible();
    expect(toast.success).toHaveBeenCalledWith(
      ru["groups.detail.clientsAdded"].replace("{count}", "2"),
    );
  });

  it("показывает абонемент и баланс клиента, имя ведёт в карточку", async () => {
    const { history } = openApp(`/groups/${juniors.id}?tab=clients`, groupServer(juniors).fetch);
    const user = userEvent.setup();

    expect(await screen.findByText(/8 занятий · осталось 5 занятий · до/)).toBeVisible();
    expect(screen.getByText(/Баланс/)).toBeVisible();
    await user.click(screen.getByRole("link", { name: anna.name }));

    expect(history.location.pathname).toBe(`/clients/${anna.id}`);
  });

  it("пустая группа — сообщение и действие добавления", async () => {
    openApp(`/groups/${juniors.id}?tab=clients`, groupServer(juniors, { members: [] }).fetch);

    expect(await screen.findByText(ru["groups.detail.clientsEmpty"])).toBeVisible();
    expect(
      within(activePanel()).getByRole("button", { name: ru["groups.detail.addClients"] }),
    ).toBeVisible();
  });

  it("без абонемента — «Оформить абонемент» открывает выдачу этому клиенту", async () => {
    openApp(
      `/groups/${juniors.id}?tab=clients`,
      groupServer(juniors, {
        roster: { [anna.id]: { membership: null, attention: "NO_MEMBERSHIP" } },
      }).fetch,
    );
    const user = userEvent.setup();

    await screen.findByRole("link", { name: anna.name });
    expect(
      within(activePanel()).getAllByText(ru["groups.attention.NO_MEMBERSHIP"]),
    ).not.toHaveLength(0);
    await user.click(screen.getByRole("button", { name: ru["groups.clients.action.issue"] }));

    expect(
      await screen.findByRole("dialog", { name: ru["clients.detail.issueSubscription"] }),
    ).toBeVisible();
  });

  it("неоплаченные занятия с остатком — «Погасить долг» открывает абонемент клиента", async () => {
    openApp(
      `/groups/${juniors.id}?tab=clients`,
      groupServer(juniors, {
        roster: { [anna.id]: { unpaidSessions: 2, canRepay: true, attention: "DEBT" } },
      }).fetch,
    );
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: ru["groups.clients.action.repay"] }),
    );

    expect(await screen.findByRole("dialog", { name: "8 занятий" })).toBeVisible();
  });

  it("долг только по балансу — «Открыть клиента» ведёт в карточку", async () => {
    openApp(
      `/groups/${juniors.id}?tab=clients`,
      groupServer(juniors, {
        roster: {
          [anna.id]: { balance: { minorUnits: -500_00, currency: "RUB" }, attention: "DEBT" },
        },
      }).fetch,
    );

    const link = await screen.findByRole("link", { name: ru["groups.clients.action.openClient"] });
    expect(link).toHaveAttribute("href", `/clients/${anna.id}`);
    expect(within(activePanel()).getByText(ru["groups.attention.DEBT"])).toBeVisible();
  });

  it("фильтр «Требуют внимания» и поиск по имени", async () => {
    const group = juniors;
    openApp(
      `/groups/${group.id}?tab=clients`,
      groupServer(group, {
        members: [anna, vera, gleb],
        roster: { [vera.id]: { attention: "MEMBERSHIP_ENDING" } },
      }).fetch,
    );
    const user = userEvent.setup();

    const filter = await screen.findByRole("button", {
      name: new RegExp(ru["groups.clients.filterAttention"]),
    });
    expect(filter).toHaveTextContent("1");
    await user.click(filter);
    expect(
      within(activePanel()).getAllByRole("link", { name: /Котова|Белова|Носов/ }),
    ).toHaveLength(1);

    await user.click(
      screen.getByRole("button", { name: new RegExp(ru["groups.clients.filterAll"]) }),
    );
    await user.type(screen.getByRole("searchbox", { name: ru["groups.clients.search"] }), "НОС");
    expect(within(activePanel()).getByRole("link", { name: gleb.name })).toBeVisible();
    expect(within(activePanel()).queryByRole("link", { name: anna.name })).not.toBeInTheDocument();
  });

  it("убирает клиента из состава крестиком", async () => {
    const server = groupServer(juniors);
    openApp(`/groups/${juniors.id}?tab=clients`, server.fetch);
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
    openApp(`/groups/${juniors.id}?tab=clients`, groupServer(juniors, { failRemove: true }).fetch);
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

describe("вкладка «Абонементы»", () => {
  it("группирует состав по абонементу, без абонемента — отдельно", async () => {
    const group = juniors;
    openApp(
      `/groups/${group.id}?tab=memberships`,
      groupServer(group, {
        members: [anna, vera, gleb],
        roster: { [gleb.id]: { membership: null, attention: "NO_MEMBERSHIP" } },
      }).fetch,
    );

    expect(await screen.findByText("— 2 клиента")).toBeVisible();
    expect(screen.getByText(ru["groups.memberships.none"])).toBeVisible();
    expect(screen.getByText("— 1 клиент")).toBeVisible();
  });
});

describe("вкладка «Журнал»", () => {
  it("отличает «не участвовал» от «не пришёл», исключённые — в конце", async () => {
    const sessionId = "0199a0b2-0000-7000-8000-a00000000009";
    const group = juniors;
    const attendance = GroupAttendanceResponseSchema.parse({
      sessions: [{ id: sessionId, date: addDays(todayLocalDate(), -3), startTime: "18:00" }],
      participants: [
        {
          clientId: anna.id,
          name: anna.name,
          presentCount: 0,
          absentCount: 1,
          marks: [
            {
              sessionId,
              presence: "ABSENT",
              labels: [
                {
                  id: "0199a0b2-0000-7000-8000-c00000000001",
                  name: "болеет",
                  scope: "ABSENT",
                  position: 0,
                  isArchived: false,
                },
              ],
            },
          ],
        },
        {
          clientId: gleb.id,
          name: gleb.name,
          presentCount: 1,
          absentCount: 0,
          marks: [{ sessionId, presence: "PRESENT", labels: [] }],
        },
      ],
    });
    openApp(
      `/groups/${group.id}?tab=journal`,
      groupServer(group, { members: [anna, vera], attendance }).fetch,
    );

    const absent = await screen.findByRole("link", { name: "Не пришёл · болеет" });
    expect(absent).toHaveAttribute("href", `/sessions/${sessionId}`);
    const veraRow = screen.getByRole("rowheader", { name: vera.name }).closest("tr");
    expect(veraRow).not.toBeNull();
    if (veraRow !== null) {
      expect(within(veraRow).getByText(ru["groups.journal.none"])).toBeInTheDocument();
      expect(within(veraRow).queryByRole("link")).not.toBeInTheDocument();
    }
    const rows = screen.getAllByRole("rowheader").map((cell) => cell.textContent);
    expect(rows).toEqual([anna.name, vera.name, `${gleb.name}${ru["groups.journal.former"]}`]);
  });
});

/** Группа с дисциплиной «Бокс» и тренером «Иванов». */
const seniors = GroupDetailResponseSchema.parse({
  ...juniors,
  disciplines: [boxing],
  employees: [{ id: ivanov.id, name: ivanov.name, avatarId: null }],
});

describe("дисциплины и тренеры в карточке", () => {
  it("дисциплины — чипами в шапке без действий, тренеры — карточкой с «Изменить»", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors).fetch);

    const chip = await screen.findByText(boxing.name);
    expect(chip.closest("li")).not.toBeNull();
    expect(within(chip).queryByRole("button")).not.toBeInTheDocument();
    const employees = section(ru["groups.detail.employeesTitle"]);
    expect(within(employees).getByText(ivanov.name)).toBeVisible();
    expect(within(employees).getAllByRole("button")).toHaveLength(1);
    expect(
      within(employees).getByRole("button", { name: ru["groups.detail.editSection"] }),
    ).toBeVisible();
  });

  it("при пустом наборе тренеров — «Тренеры не указаны» и «Изменить», чипов нет", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);

    await screen.findByText(ru["groups.detail.employeesEmpty"]);
    const employees = section(ru["groups.detail.employeesTitle"]);
    expect(
      within(employees).getByRole("button", { name: ru["groups.detail.editSection"] }),
    ).toBeVisible();
    expect(screen.queryByText(boxing.name)).not.toBeInTheDocument();
  });

  it("«⋯» → «Дисциплины» открывает панель выбора дисциплин", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors).fetch);
    const user = userEvent.setup();

    await chooseMenu(user, ru["groups.detail.disciplinesTitle"]);

    expect(
      await screen.findByRole("dialog", { name: ru["groups.picker.disciplinesTitle"] }),
    ).toBeVisible();
  });

  it("заменяет тренеров отмеченными в панели, включая неактивных в списке", async () => {
    const server = groupServer(seniors);
    openApp(`/groups/${seniors.id}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText(ivanov.name);
    await user.click(
      within(section(ru["groups.detail.employeesTitle"])).getByRole("button", {
        name: ru["groups.detail.editSection"],
      }),
    );
    const sheet = await screen.findByRole("dialog", { name: ru["groups.picker.employeesTitle"] });
    expect(await within(sheet).findByRole("checkbox", { name: ivanov.name })).toBeChecked();
    expect(within(sheet).getByRole("checkbox", { name: kozlov.name })).not.toBeChecked();
    await user.click(within(sheet).getByRole("checkbox", { name: ivanov.name }));
    await user.click(within(sheet).getByRole("checkbox", { name: sidorova.name }));
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(
      SetGroupEmployeesRequestSchema.parse(server.to("groups/set-employees")[0]?.body),
    ).toEqual({ groupId: seniors.id, employeeIds: [sidorova.id] });
    const employees = section(ru["groups.detail.employeesTitle"]);
    expect(await within(employees).findByText(sidorova.name)).toBeVisible();
    expect(within(employees).queryByText(ivanov.name)).not.toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith(ru["groups.detail.employeesSaved"]);
  });

  it("снятие всех дисциплин убирает чипы из шапки", async () => {
    const server = groupServer(seniors);
    openApp(`/groups/${seniors.id}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText(boxing.name);
    await chooseMenu(user, ru["groups.detail.disciplinesTitle"]);
    const sheet = await screen.findByRole("dialog", { name: ru["groups.picker.disciplinesTitle"] });
    await user.click(await within(sheet).findByRole("checkbox", { name: boxing.name }));
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByText(boxing.name)).not.toBeInTheDocument();
    });
    expect(
      SetGroupDisciplinesRequestSchema.parse(server.to("groups/set-disciplines")[0]?.body),
    ).toEqual({ groupId: seniors.id, disciplineIds: [] });
  });

  it("при ошибке сохранения панель остаётся открытой, карточка не меняется", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors, { failSet: true }).fetch);
    const user = userEvent.setup();

    await screen.findByText(ivanov.name);
    await chooseMenu(user, ru["groups.detail.employeesTitle"]);
    const sheet = await screen.findByRole("dialog", { name: ru["groups.picker.employeesTitle"] });
    await user.click(await within(sheet).findByRole("checkbox", { name: sidorova.name }));
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Группа не найдена");
    expect(within(sheet).getByRole("checkbox", { name: sidorova.name })).toBeChecked();
    expect(
      within(section(ru["groups.detail.employeesTitle"])).queryByText(sidorova.name),
    ).not.toBeInTheDocument();
  });
});

const scheduled = GroupDetailResponseSchema.parse({
  ...juniors,
  schedule: ["MONDAY", "WEDNESDAY"].map((dayOfWeek) => ({
    dayOfWeek,
    startAt: "15:00",
    endAt: "17:00",
    hallId: bigHall.id,
    hallName: bigHall.name,
    validity: null,
  })),
});

/** Открывает панель расписания из меню «⋯» и возвращает её. */
async function openSchedule(user: ReturnType<typeof userEvent.setup>) {
  await chooseMenu(user, ru["groups.detail.editSchedule"]);
  return screen.findByRole("dialog", { name: ru["groups.schedule.title"] });
}

/** Кнопка дня недели [day] в единственной карточке панели [sheet]. */
const dayButton = (sheet: HTMLElement, day: "day.MONDAY" | "day.WEDNESDAY" | "day.FRIDAY") =>
  within(sheet).getByRole("button", { name: ru[day] });

describe("изменение названия группы", () => {
  /** Открывает панель изменения названия из меню «⋯». */
  const openRename = async (user: ReturnType<typeof userEvent.setup>) => {
    await chooseMenu(user, ru["groups.detail.rename"]);
    return screen.findByRole("dialog", { name: ru["groups.edit"] });
  };

  it("меняет только название, дисциплины и тренеры отправляются прежними", async () => {
    const server = groupServer(seniors);
    openApp(`/groups/${seniors.id}`, server.fetch);
    const user = userEvent.setup();

    const sheet = await openRename(user);
    const name = within(sheet).getByLabelText(ru["groups.name"], { exact: false });
    expect(name).toHaveValue(seniors.name);
    await user.clear(name);
    await user.type(name, "  Старшие  ");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(EditGroupRequestSchema.parse(server.to("groups/edit")[0]?.body)).toEqual({
      id: seniors.id,
      name: "Старшие",
      disciplineIds: [boxing.id],
      employeeIds: [ivanov.id],
    });
    expect(await screen.findByRole("heading", { name: "Старшие" })).toBeVisible();
    expect(
      within(section(ru["groups.detail.employeesTitle"])).getByText(ivanov.name),
    ).toBeVisible();
    expect(toast.success).toHaveBeenCalledWith(ru["groups.detail.renamed"]);
  });

  it("пустое название — ошибка без запроса", async () => {
    const server = groupServer(seniors);
    openApp(`/groups/${seniors.id}`, server.fetch);
    const user = userEvent.setup();

    const sheet = await openRename(user);
    await user.clear(within(sheet).getByLabelText(ru["groups.name"], { exact: false }));
    await user.type(within(sheet).getByLabelText(ru["groups.name"], { exact: false }), "   ");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await within(sheet).findByText(ru["error.required"])).toBeVisible();
    expect(server.to("groups/edit")).toHaveLength(0);
  });

  it("при ошибке сервера панель остаётся открытой с введённым названием", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors, { failSet: true }).fetch);
    const user = userEvent.setup();

    const sheet = await openRename(user);
    const name = within(sheet).getByLabelText(ru["groups.name"], { exact: false });
    await user.clear(name);
    await user.type(name, "Старшие");
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await within(sheet).findByText("Группа не найдена")).toBeVisible();
    expect(name).toHaveValue("Старшие");
  });

  it("Esc без изменений закрывает панель без подтверждения", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors).fetch);
    const user = userEvent.setup();

    await openRename(user);
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.queryByText(ru["editSheet.discardTitle"])).not.toBeInTheDocument();
  });
});

describe("расписание в карточке группы", () => {
  it("«Изменить расписание» открывает панель с карточками действующего расписания", async () => {
    openApp(`/groups/${scheduled.id}`, groupServer(scheduled).fetch);
    const user = userEvent.setup();

    const sheet = await openSchedule(user);
    expect(dayButton(sheet, "day.MONDAY")).toHaveAttribute("aria-pressed", "true");
    expect(dayButton(sheet, "day.WEDNESDAY")).toHaveAttribute("aria-pressed", "true");
    expect(dayButton(sheet, "day.FRIDAY")).toHaveAttribute("aria-pressed", "false");
    expect(within(sheet).getByLabelText(ru["groups.schedule.startTime"])).toHaveValue("15:00");
  });

  it("при карточке без дней «Сохранить» недоступна", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);
    const user = userEvent.setup();

    const sheet = await openSchedule(user);
    await user.click(within(sheet).getByRole("button", { name: ru["groups.schedule.addCard"] }));

    expect(within(sheet).getByText(ru["groups.schedule.errorNoDays"])).toBeVisible();
    expect(within(sheet).getByRole("button", { name: ru["action.save"] })).toBeDisabled();
  });

  it("«Отмена» после правки просит подтверждение и не меняет расписание", async () => {
    const server = groupServer(scheduled);
    openApp(`/groups/${scheduled.id}`, server.fetch);
    const user = userEvent.setup();

    const sheet = await openSchedule(user);
    await user.click(dayButton(sheet, "day.FRIDAY"));
    await user.click(within(sheet).getByRole("button", { name: ru["action.cancel"] }));

    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(server.to("groups/set-schedule")).toHaveLength(0);
  });

  it("«Отмена» без правок закрывает панель сразу", async () => {
    openApp(`/groups/${scheduled.id}`, groupServer(scheduled).fetch);
    const user = userEvent.setup();

    const sheet = await openSchedule(user);
    await user.click(dayButton(sheet, "day.FRIDAY"));
    await user.click(dayButton(sheet, "day.FRIDAY"));
    await user.click(within(sheet).getByRole("button", { name: ru["action.cancel"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.queryByText(ru["editSheet.discardTitle"])).not.toBeInTheDocument();
  });

  it("«Сохранить» отправляет новое расписание и закрывает панель", async () => {
    const server = groupServer(scheduled);
    openApp(`/groups/${scheduled.id}`, server.fetch);
    const user = userEvent.setup();

    const sheet = await openSchedule(user);
    await user.click(dayButton(sheet, "day.FRIDAY"));
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    const request = SetGroupScheduleRequestSchema.parse(server.to("groups/set-schedule")[0]?.body);
    expect(request.effectiveFrom).toBeNull();
    expect(request.slots?.map((slot) => slot.dayOfWeek)).toEqual(["MONDAY", "WEDNESDAY", "FRIDAY"]);
    expect(toast.success).toHaveBeenCalledWith(ru["groups.schedule.savedToast"]);
  });

  it("при ошибке сохранения панель остаётся открытой с правками", async () => {
    openApp(`/groups/${scheduled.id}`, groupServer(scheduled, { failSet: true }).fetch);
    const user = userEvent.setup();

    const sheet = await openSchedule(user);
    await user.click(dayButton(sheet, "day.FRIDAY"));
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await within(sheet).findByRole("alert")).toHaveTextContent("Группа не найдена");
    expect(dayButton(sheet, "day.FRIDAY")).toHaveAttribute("aria-pressed", "true");
  });
});
