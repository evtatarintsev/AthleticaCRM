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
  HallDetailResponseSchema,
  RemoveClientFromGroupRequestSchema,
  SetGroupDisciplinesRequestSchema,
  SetGroupEmployeesRequestSchema,
  SetGroupScheduleRequestSchema,
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
  clients: [{ id: anna.id, name: anna.name }],
});

/** Ответ бизнес-ошибкой «группа не найдена». */
const groupNotFound = () =>
  json({ code: "GROUP_NOT_FOUND", message: "Группа не найдена", fields: null }, 404);

/**
 * Поддельный сервер карточки группы: хранит группу и применяет к ней изменения так же, как
 * сервер. [failRemove] — удаление клиента отвечает бизнес-ошибкой, [failSet] — замена
 * дисциплин, тренеров и расписания.
 */
function groupServer(
  initial: GroupDetailResponse,
  { failRemove = false, failSet = false }: { failRemove?: boolean; failSet?: boolean } = {},
) {
  let group = initial;
  const all = [anna, vera, gleb];
  const staff = [ivanov, kozlov, sidorova];
  const disciplines = [boxing, judo];
  return appServer({
    "groups/detail": () => json(group),
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
      group = { ...group, clients: [...group.clients, ...added] };
      return empty();
    },
    "clients/remove-from-group": ({ body }) => {
      if (failRemove) {
        return groupNotFound();
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
    openApp(`/groups/${juniors.id}`, groupServer(juniors, { failRemove: true }).fetch);
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

/** Группа с дисциплиной «Бокс» и тренером «Иванов». */
const seniors = GroupDetailResponseSchema.parse({
  ...juniors,
  disciplines: [boxing],
  employees: [{ id: ivanov.id, name: ivanov.name, avatarId: null }],
});

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

describe("дисциплины и тренеры в карточке", () => {
  it("показывает чипы без крестиков и действие «Изменить»", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors).fetch);

    expect(await screen.findByText(boxing.name)).toBeVisible();
    const disciplines = section(ru["groups.detail.disciplinesTitle"]);
    const employees = section(ru["groups.detail.employeesTitle"]);
    expect(within(employees).getByText(ivanov.name)).toBeVisible();
    expect(within(employees).getAllByRole("button")).toHaveLength(1);
    expect(within(disciplines).getAllByRole("button")).toHaveLength(1);
    expect(
      within(disciplines).getByRole("button", { name: ru["groups.detail.editSection"] }),
    ).toBeVisible();
    expect(
      within(employees).getByRole("button", { name: ru["groups.detail.editSection"] }),
    ).toBeVisible();
  });

  it("при пустом наборе показывает сообщения и действие «Изменить»", async () => {
    openApp(`/groups/${juniors.id}`, groupServer(juniors).fetch);

    expect(await screen.findByText(ru["groups.detail.disciplinesEmpty"])).toBeVisible();
    expect(screen.getByText(ru["groups.detail.employeesEmpty"])).toBeVisible();
    expect(screen.getAllByRole("button", { name: ru["groups.detail.editSection"] })).toHaveLength(
      2,
    );
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

  it("снятие всех дисциплин оставляет группу без дисциплин", async () => {
    const server = groupServer(seniors);
    openApp(`/groups/${seniors.id}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText(boxing.name);
    await user.click(
      within(section(ru["groups.detail.disciplinesTitle"])).getByRole("button", {
        name: ru["groups.detail.editSection"],
      }),
    );
    const sheet = await screen.findByRole("dialog", { name: ru["groups.picker.disciplinesTitle"] });
    await user.click(await within(sheet).findByRole("checkbox", { name: boxing.name }));
    await user.click(within(sheet).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByText(ru["groups.detail.disciplinesEmpty"])).toBeVisible();
    expect(
      SetGroupDisciplinesRequestSchema.parse(server.to("groups/set-disciplines")[0]?.body),
    ).toEqual({ groupId: seniors.id, disciplineIds: [] });
  });

  it("при ошибке сохранения панель остаётся открытой, карточка не меняется", async () => {
    openApp(`/groups/${seniors.id}`, groupServer(seniors, { failSet: true }).fetch);
    const user = userEvent.setup();

    await screen.findByText(ivanov.name);
    await user.click(
      within(section(ru["groups.detail.employeesTitle"])).getByRole("button", {
        name: ru["groups.detail.editSection"],
      }),
    );
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

/** Открывает панель расписания карточки группы и возвращает её. */
async function openSchedule(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: ru["groups.detail.editSchedule"] }));
  return screen.findByRole("dialog", { name: ru["groups.schedule.title"] });
}

/** Кнопка дня недели [day] в единственной карточке панели [sheet]. */
const dayButton = (sheet: HTMLElement, day: "day.MONDAY" | "day.WEDNESDAY" | "day.FRIDAY") =>
  within(sheet).getByRole("button", { name: ru[day] });

describe("изменение названия группы", () => {
  /** Открывает панель изменения названия на карточке. */
  const openRename = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(await screen.findByRole("button", { name: ru["groups.detail.editAction"] }));
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
