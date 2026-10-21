import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  CreateSessionRequestSchema,
  EmployeeIdSchema,
  GroupIdSchema,
  HallIdSchema,
  LocalTimeSchema,
  ScheduleListRequestSchema,
  SessionIdSchema,
  type ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { addDays, mondayOf, todayLocalDate } from "@/lib/localDate";
import { appServer, json, openApp, type FakeHandler } from "@/test/app";

const session: ScheduleSessionSchema = {
  id: SessionIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000601"),
  group: { id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000602"), name: "Юниоры У10" },
  date: mondayOf(todayLocalDate()),
  startTime: LocalTimeSchema.parse("10:00"),
  endTime: LocalTimeSchema.parse("11:00"),
  hall: { id: HallIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000603"), name: "Зал А" },
  coaches: [],
  disciplines: [],
  status: "SCHEDULED",
  colorKey: "ORANGE",
};

/** Сотрудник в форме ответа `employees/list`. */
function employee(id: string, name: string) {
  return {
    id: EmployeeIdSchema.parse(id),
    name,
    avatarId: null,
    isOwner: false,
    isActive: true,
    joinedAt: "2026-01-01T00:00:00Z",
    roles: [],
    phoneNo: null,
    email: null,
  };
}

const ivanov = employee("0199a0b2-7c3e-7d2a-9f10-000000000701", "Иванов");
const petrov = employee("0199a0b2-7c3e-7d2a-9f10-000000000702", "Петров");
const sidorov = employee("0199a0b2-7c3e-7d2a-9f10-000000000703", "Сидоров");

/** Группа в форме ответа `groups/list` с тренерами [coaches]. */
function group(id: string, name: string, coaches: readonly ReturnType<typeof employee>[]) {
  return {
    id: GroupIdSchema.parse(id),
    name,
    schedule: [],
    scheduleChangeAt: null,
    employees: coaches.map((c) => ({ id: c.id, name: c.name, avatarId: null })),
  };
}

const juniors = group(session.group.id, session.group.name, [ivanov, petrov]);
const seniors = group("0199a0b2-7c3e-7d2a-9f10-000000000604", "Взрослые", [sidorov]);

function server(sessions: readonly ScheduleSessionSchema[] = [session], create?: FakeHandler) {
  return appServer({
    "schedule/list": () => json({ sessions }),
    "disciplines/list": () => json({ disciplines: [] }),
    "halls/list": () => json({ halls: [session.hall] }),
    "employees/list": () => json({ employees: [ivanov, petrov, sidorov], total: 3 }),
    "groups/list-for-select": () => json([session.group]),
    "groups/list": () => json({ groups: [juniors, seniors], total: 2 }),
    "sessions/create": create ?? (() => json({ code: "UNEXPECTED", message: "" }, 500)),
  });
}

/** Подпись кнопки добавления занятия в шапке четверга текущей недели. */
async function thursdayAddButton() {
  const buttons = await screen.findAllByRole("button", {
    name: new RegExp(`^${ru["schedule.addSession"].replace("{day}", "")}`),
  });
  const thursday = buttons[3];
  if (thursday === undefined) {
    throw new Error("нет кнопки четверга");
  }
  return thursday;
}

/** Открывает панель создания из шапки четверга и выбирает группу [name]. */
async function openCreateWithGroup(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(await thursdayAddButton());
  const dialog = await screen.findByRole("dialog", { name: ru["schedule.create.title"] });
  const groupSelect = within(dialog).getByLabelText(ru["schedule.create.group"]);
  await within(dialog).findByRole("option", { name });
  await user.selectOptions(groupSelect, name);
  return dialog;
}

/** Адрес расписания с фильтром по группе занятия. */
const groupFilterPath = `/schedule?groupIds=${encodeURIComponent(JSON.stringify([session.group.id]))}`;

/** Фильтры по группам из запросов `schedule/list` поддельного сервера [fake]. */
const requestedGroupIds = (fake: ReturnType<typeof server>) =>
  fake.to("schedule/list").map((r) => ScheduleListRequestSchema.parse(r.body).groupIds);

describe("расписание", () => {
  it("клик по занятию в сетке открывает его карточку", async () => {
    const { history } = openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByText(session.group.name));

    expect(history.location.pathname).toBe(`/sessions/${session.id}`);
  });

  it("адрес с фильтром по группе отправляет фильтр и показывает чип с названием группы", async () => {
    const fake = server();
    openApp(groupFilterPath, fake.fetch);

    expect(
      await screen.findByText(ru["schedule.filter.group"].replace("{name}", session.group.name)),
    ).toBeVisible();
    expect(requestedGroupIds(fake)[0]).toEqual([session.group.id]);
  });

  it("снятие чипа убирает фильтр по группе из адреса и запроса", async () => {
    const fake = server();
    const { history } = openApp(groupFilterPath, fake.fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: ru["schedule.filter.removeGroup"] }),
    );

    await waitFor(() => {
      expect(history.location.search).not.toContain("groupIds");
    });
    await waitFor(() => {
      expect(requestedGroupIds(fake).at(-1)).toBeUndefined();
    });
  });

  it("переключение недели сохраняет фильтр по группе", async () => {
    const fake = server();
    openApp(groupFilterPath, fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["schedule.nextWeek"] }));

    await waitFor(() => {
      expect(fake.to("schedule/list").length).toBeGreaterThan(1);
    });
    expect(requestedGroupIds(fake).every((ids) => ids?.[0] === session.group.id)).toBe(true);
  });

  it("неделя без занятий показывает шапку дней с кнопками добавления", async () => {
    openApp("/schedule", server([]).fetch);

    expect(await screen.findByText(ru["schedule.empty"])).toBeVisible();
    expect(
      screen.getAllByRole("button", {
        name: new RegExp(`^${ru["schedule.addSession"].replace("{day}", "")}`),
      }),
    ).toHaveLength(7);
  });

  it("кнопка в шапке дня открывает панель с датой этого дня", async () => {
    openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    await user.click(await thursdayAddButton());

    const dialog = await screen.findByRole("dialog", { name: ru["schedule.create.title"] });
    expect(within(dialog).getByLabelText(ru["schedule.create.date"])).toHaveValue(
      addDays(mondayOf(todayLocalDate()), 3),
    );
  });

  it("выбор группы отмечает её тренеров, смена группы заменяет состав", async () => {
    openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    const dialog = await openCreateWithGroup(user, juniors.name);
    expect(within(dialog).getByRole("checkbox", { name: ivanov.name })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: petrov.name })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: sidorov.name })).not.toBeChecked();

    await user.selectOptions(
      within(dialog).getByLabelText(ru["schedule.create.group"]),
      seniors.name,
    );

    expect(within(dialog).getByRole("checkbox", { name: ivanov.name })).not.toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: sidorov.name })).toBeChecked();
  });

  it("окончание не позже начала блокирует создание", async () => {
    openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    const dialog = await openCreateWithGroup(user, juniors.name);
    await user.selectOptions(
      within(dialog).getByLabelText(ru["schedule.create.hall"]),
      session.hall.name,
    );
    await user.type(within(dialog).getByLabelText(ru["schedule.create.startTime"]), "18:00");
    await user.type(within(dialog).getByLabelText(ru["schedule.create.endTime"]), "17:00");

    expect(within(dialog).getByText(ru["schedule.create.timeOrder"])).toBeVisible();
    expect(
      within(dialog).getByRole("button", { name: ru["schedule.create.submit"] }),
    ).toBeDisabled();
  });

  it("создание отправляет изменённый состав тренеров, закрывает панель и перезагружает расписание", async () => {
    const fake = server([session], ({ body }) => {
      const request = CreateSessionRequestSchema.parse(body);
      return json({
        id: request.id,
        groupId: request.groupId,
        groupName: juniors.name,
        date: request.date,
        startTime: request.startTime,
        endTime: request.endTime,
        hallId: request.hallId,
        status: "scheduled",
        isManual: true,
        isRescheduled: false,
        notes: request.notes ?? null,
        employeeIds: request.employeeIds,
        isEmployeeAssignmentOverridden: false,
      });
    });
    openApp("/schedule", fake.fetch);
    const user = userEvent.setup();

    const dialog = await openCreateWithGroup(user, juniors.name);
    await user.click(within(dialog).getByRole("checkbox", { name: petrov.name }));
    await user.click(within(dialog).getByRole("checkbox", { name: sidorov.name }));
    await user.selectOptions(
      within(dialog).getByLabelText(ru["schedule.create.hall"]),
      session.hall.name,
    );
    await user.type(within(dialog).getByLabelText(ru["schedule.create.startTime"]), "15:00");
    await user.type(within(dialog).getByLabelText(ru["schedule.create.endTime"]), "16:30");
    const loadsBefore = fake.to("schedule/list").length;
    await user.click(within(dialog).getByRole("button", { name: ru["schedule.create.submit"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["schedule.create.title"] })).toBeNull();
    });
    const request = CreateSessionRequestSchema.parse(fake.to("sessions/create")[0]?.body);
    expect(request).toMatchObject({
      groupId: juniors.id,
      date: addDays(mondayOf(todayLocalDate()), 3),
      startTime: "15:00",
      endTime: "16:30",
      hallId: session.hall.id,
      employeeIds: [ivanov.id, sidorov.id],
      notes: null,
    });
    expect(fake.to("schedule/list").length).toBeGreaterThan(loadsBefore);
  });

  it("ошибка создания оставляет панель открытой с сообщением", async () => {
    openApp(
      "/schedule",
      server([session], () =>
        json({ code: "HALL_NOT_FOUND", message: "Зал не найден", fields: null }, 404),
      ).fetch,
    );
    const user = userEvent.setup();

    const dialog = await openCreateWithGroup(user, juniors.name);
    await user.selectOptions(
      within(dialog).getByLabelText(ru["schedule.create.hall"]),
      session.hall.name,
    );
    await user.type(within(dialog).getByLabelText(ru["schedule.create.startTime"]), "15:00");
    await user.type(within(dialog).getByLabelText(ru["schedule.create.endTime"]), "16:00");
    await user.click(within(dialog).getByRole("button", { name: ru["schedule.create.submit"] }));

    expect(await within(dialog).findByText("Зал не найден")).toBeVisible();
    expect(within(dialog).getByLabelText(ru["schedule.create.startTime"])).toHaveValue("15:00");
  });
});
