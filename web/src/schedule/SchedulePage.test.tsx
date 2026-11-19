import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  CreateSessionRequestSchema,
  EmployeeIdSchema,
  GroupIdSchema,
  HallIdSchema,
  LocalDateSchema,
  LocalTimeSchema,
  ScheduleListRequestSchema,
  SessionIdSchema,
  type ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp, type FakeHandler } from "@/test/app";
import { stubMatchMedia } from "@/test/media";

/** Среда 18 ноября 2026, 15:00 по Москве — «сейчас» всех тестов страницы. */
const NOW = new Date("2026-11-18T12:00:00Z");
const monday = LocalDateSchema.parse("2026-11-16");
const today = LocalDateSchema.parse("2026-11-18");
const thursday = LocalDateSchema.parse("2026-11-19");

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
});

const session: ScheduleSessionSchema = {
  id: SessionIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000601"),
  group: { id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000602"), name: "Юниоры У10" },
  date: monday,
  startTime: LocalTimeSchema.parse("10:00"),
  endTime: LocalTimeSchema.parse("11:00"),
  hall: { id: HallIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000603"), name: "Зал А" },
  coaches: [],
  disciplines: [],
  status: "SCHEDULED",
  colorKey: "ORANGE",
  coachesOverridden: false,
  rescheduledFrom: null,
  isManual: false,
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
    "org/settings": () => json({ name: "Лига", timezone: "Europe/Moscow", currency: "RUB" }),
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

    const [card] = await screen.findAllByRole("link", { name: new RegExp(session.group.name) });
    if (card === undefined) {
      throw new Error("нет карточки занятия");
    }
    await user.click(card);

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

    expect((await screen.findAllByText(ru["schedule.empty"]))[0]).toBeVisible();
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
    expect(within(dialog).getByLabelText(ru["schedule.create.date"])).toHaveValue(thursday);
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
      date: thursday,
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

/** Занятие [n] группы «Юниоры У10» в день [date] с [start] до [end] и отличиями [extra]. */
function sessionAt(
  n: number,
  date: string,
  start: string,
  end: string,
  extra: Partial<ScheduleSessionSchema> = {},
): ScheduleSessionSchema {
  return {
    ...session,
    id: SessionIdSchema.parse(`0199a0b2-7c3e-7d2a-9f10-0000000008${String(n).padStart(2, "0")}`),
    group: { ...session.group, name: `Группа ${String(n)}` },
    date: LocalDateSchema.parse(date),
    startTime: LocalTimeSchema.parse(start),
    endTime: LocalTimeSchema.parse(end),
    ...extra,
  };
}

/** Понедельники недель из запросов `schedule/list` поддельного сервера [fake]. */
const requestedFrom = (fake: ReturnType<typeof server>) =>
  fake.to("schedule/list").map((r) => ScheduleListRequestSchema.parse(r.body).from);

/** Переключатель режима [name]. */
const viewButton = (name: string) => screen.findByRole("button", { name });

describe("режимы «День» и «Неделя»", () => {
  it("без режима на узком экране открывается сегодняшний день", async () => {
    stubMatchMedia(false);
    openApp("/schedule", server().fetch);

    expect(await screen.findByText("среда, 18 ноября · сегодня")).toBeVisible();
    expect(await viewButton(ru["schedule.view.day"])).toHaveAttribute("aria-pressed", "true");
  });

  it("без режима на широком экране открывается текущая неделя", async () => {
    const fake = server();
    openApp("/schedule", fake.fetch);

    expect(await viewButton(ru["schedule.view.week"])).toHaveAttribute("aria-pressed", "true");
    await waitFor(() => {
      expect(requestedFrom(fake)).toEqual([monday]);
    });
  });

  it("неделя → день открывает сегодня и пишет режим и дату в адрес", async () => {
    const { history } = openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    await user.click(await viewButton(ru["schedule.view.day"]));

    await waitFor(() => {
      expect(history.location.search).toBe("?view=day&date=2026-11-18");
    });
  });

  it("неделя без сегодняшнего дня → день открывает понедельник", async () => {
    const { history } = openApp("/schedule?view=week&date=2026-11-25", server().fetch);
    const user = userEvent.setup();

    await user.click(await viewButton(ru["schedule.view.day"]));

    await waitFor(() => {
      expect(history.location.search).toBe("?view=day&date=2026-11-23");
    });
  });

  it("день → неделя открывает неделю выбранного дня", async () => {
    const fake = server();
    const { history } = openApp("/schedule?view=day&date=2026-11-25", fake.fetch);
    const user = userEvent.setup();

    await user.click(await viewButton(ru["schedule.view.week"]));

    await waitFor(() => {
      expect(history.location.search).toBe("?view=week&date=2026-11-25");
    });
    expect(requestedFrom(fake).every((from) => from === "2026-11-23")).toBe(true);
  });

  it("назад с понедельника открывает воскресенье прошлой недели и загружает её", async () => {
    const fake = server();
    const { history } = openApp("/schedule?view=day&date=2026-11-16", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["schedule.prevDay"] }));

    await waitFor(() => {
      expect(history.location.search).toBe("?view=day&date=2026-11-15");
    });
    await waitFor(() => {
      expect(requestedFrom(fake).at(-1)).toBe("2026-11-09");
    });
    expect(await screen.findByText("воскресенье, 15 ноября")).toBeVisible();
  });

  it("«Сегодня» возвращает к текущему периоду", async () => {
    const fake = server();
    const { history } = openApp("/schedule?view=week&date=2026-11-02", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["schedule.today"] }));

    await waitFor(() => {
      expect(history.location.search).toBe("?view=week");
    });
    await waitFor(() => {
      expect(requestedFrom(fake).at(-1)).toBe(monday);
    });
    expect(screen.queryByRole("button", { name: ru["schedule.today"] })).toBeNull();
  });

  it("смена режима сохраняет фильтр по группе", async () => {
    const fake = server();
    const { history } = openApp(groupFilterPath, fake.fetch);
    const user = userEvent.setup();

    await user.click(await viewButton(ru["schedule.view.day"]));

    await waitFor(() => {
      expect(history.location.search).toContain("view=day");
    });
    expect(history.location.search).toContain("groupIds");
    expect(requestedGroupIds(fake).every((ids) => ids?.[0] === session.group.id)).toBe(true);
  });

  it("сегодня по поясу организации, а не браузера", async () => {
    vi.setSystemTime(new Date("2026-11-18T21:30:00Z"));
    stubMatchMedia(false);
    openApp("/schedule", server().fetch);

    expect(await screen.findByText("четверг, 19 ноября · сегодня")).toBeVisible();
  });
});

describe("недельная сетка", () => {
  /** Ссылка на день недели [weekDay] в шапке сетки. */
  const dayHeader = (weekDay: string) =>
    screen.findByRole("link", { name: new RegExp(`^Открыть день: ${weekDay}`) });

  it("выделяет сегодняшний день", async () => {
    openApp("/schedule", server().fetch);

    expect((await dayHeader("Ср")).closest("[data-today]")).not.toBeNull();
    expect((await dayHeader("Пн")).closest("[data-today]")).toBeNull();
  });

  it("день в шапке открывает режим «День» на сегодняшней дате с фильтрами", async () => {
    const { history } = openApp(groupFilterPath, server().fetch);
    const user = userEvent.setup();

    await user.click(await dayHeader("Ср"));

    await waitFor(() => {
      expect(history.location.search).toContain("view=day&date=2026-11-18");
    });
    expect(history.location.search).toContain("groupIds");
  });
});

describe("режим «День»", () => {
  const morning = sessionAt(1, today, "10:00", "11:00", { status: "COMPLETED" });
  const live = sessionAt(2, today, "14:30", "16:00", { coachesOverridden: true });
  const soon = sessionAt(3, today, "15:40", "16:40", { isManual: true });
  const cancelled = sessionAt(4, today, "18:00", "19:00", {
    status: "CANCELLED",
    rescheduledFrom: monday,
  });
  const daySessions = [morning, live, soon, cancelled];

  it("показывает занятия по времени и отметку текущего времени после начавшихся", async () => {
    openApp("/schedule?view=day&date=2026-11-18", server(daySessions).fetch);

    const nowLine = await screen.findByRole("separator", { name: "Сейчас 15:00" });
    expect(nowLine.previousElementSibling).toHaveTextContent("Группа 2");
    expect(nowLine.nextElementSibling).toHaveTextContent("Группа 3");
    const rows = screen.getAllByRole("link", { name: /Группа \d/ }).map((l) => l.textContent);
    expect(rows.map((text) => /Группа (\d)/.exec(text)?.[1])).toEqual(["1", "2", "3", "4"]);
  });

  it("для другого дня отметки текущего времени нет", async () => {
    openApp("/schedule?view=day&date=2026-11-16", server().fetch);

    expect(await screen.findByText(session.group.name)).toBeVisible();
    expect(screen.queryByRole("separator")).toBeNull();
  });

  it("показывает метки состояния без меток посещаемости", async () => {
    openApp("/schedule?view=day&date=2026-11-18", server(daySessions).fetch);

    const row = async (name: string) => screen.findByRole("link", { name: new RegExp(name) });
    expect(await row("Группа 2")).toHaveTextContent(ru["schedule.tag.live"]);
    expect(await row("Группа 2")).toHaveTextContent(ru["schedule.tag.coachesOverridden"]);
    expect(await row("Группа 3")).toHaveTextContent("через 40 мин");
    expect(await row("Группа 3")).toHaveTextContent(ru["schedule.tag.manual"]);
    expect(await row("Группа 4")).toHaveTextContent(ru["schedule.cancelled"]);
    expect(await row("Группа 4")).toHaveTextContent(ru["schedule.tag.rescheduled"]);
    expect((await row("Группа 1")).textContent).toBe("10:0011:00Группа 1Зал АЗал А");
  });

  it("полоса дней отмечает неотменённые занятия", async () => {
    openApp("/schedule?view=day&date=2026-11-18", server(daySessions).fetch);

    const strip = await screen.findByRole("navigation", { name: ru["schedule.days"] });
    const wednesday = within(strip).getByRole("link", { name: "среда, 18 ноября" });
    expect(within(wednesday).getAllByTestId("day-dot")).toHaveLength(3);
    expect(wednesday).toHaveAttribute("aria-current");
    expect(within(strip).getByRole("link", { name: "вторник, 17 ноября" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("выбор дня в полосе открывает этот день", async () => {
    const { history } = openApp("/schedule?view=day&date=2026-11-18", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("link", { name: "понедельник, 16 ноября" }));

    await waitFor(() => {
      expect(history.location.search).toBe("?view=day&date=2026-11-16");
    });
    expect(await screen.findByText(session.group.name)).toBeVisible();
  });

  it("пустой день — сообщение и добавление занятия на этот день", async () => {
    openApp("/schedule?view=day&date=2026-11-20", server().fetch);
    const user = userEvent.setup();

    expect(await screen.findByText(ru["schedule.dayEmpty"])).toBeVisible();
    const add = screen.getAllByRole("button", { name: ru["schedule.addSessionAction"] }).at(-1);
    if (add === undefined) {
      throw new Error("нет кнопки добавления");
    }
    await user.click(add);

    const dialog = await screen.findByRole("dialog", { name: ru["schedule.create.title"] });
    expect(within(dialog).getByLabelText(ru["schedule.create.date"])).toHaveValue("2026-11-20");
  });

  it("под фильтр ничего не подходит — сообщение и доступные фильтры", async () => {
    openApp(groupFilterPath.replace("?", "?view=day&date=2026-11-20&"), server().fetch);

    expect(await screen.findByText(ru["schedule.emptyFiltered"])).toBeVisible();
    expect(screen.getByRole("button", { name: ru["schedule.filter.removeGroup"] })).toBeVisible();
  });

  it("клик по строке открывает страницу занятия", async () => {
    const { history } = openApp("/schedule?view=day&date=2026-11-16", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("link", { name: new RegExp(session.group.name) }));

    expect(history.location.pathname).toBe(`/sessions/${session.id}`);
  });
});

describe("неделя на узком экране", () => {
  it("список по дням с числом занятий, дни без занятий опущены", async () => {
    const sessions = [
      sessionAt(1, monday, "10:00", "11:00"),
      sessionAt(2, today, "18:00", "19:00"),
      sessionAt(3, today, "19:00", "20:00", { status: "CANCELLED" }),
    ];
    openApp("/schedule?view=week", server(sessions).fetch);

    const wednesday = await screen.findByRole("region", { name: "среда, 18 ноября" });
    expect(wednesday).toHaveTextContent("среда, 18 ноября · сегодня");
    expect(wednesday).toHaveTextContent("1 занятие");
    expect(screen.getByRole("region", { name: "понедельник, 16 ноября" })).toBeVisible();
    expect(screen.queryByRole("region", { name: "вторник, 17 ноября" })).toBeNull();
  });

  it("пустая неделя — сообщение вместо списка", async () => {
    openApp("/schedule?view=week", server([]).fetch);

    expect(await screen.findAllByText(ru["schedule.empty"])).toHaveLength(2);
    expect(screen.queryByRole("region", { name: /ноября/ })).toBeNull();
  });
});

describe("панель фильтров", () => {
  it("кнопка раздела открывает панель на нём и применяет выбор", async () => {
    const fake = server();
    const { history } = openApp("/schedule", fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["schedule.filter.halls"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["schedule.filters"] });
    const sections = within(dialog).getAllByRole("region");
    expect(sections.map((s) => s.getAttribute("aria-label"))).toEqual([
      ru["schedule.filter.halls"],
      ru["schedule.filter.disciplines"],
      ru["schedule.filter.employees"],
    ]);
    await user.click(within(dialog).getByRole("checkbox", { name: session.hall.name }));
    await user.click(within(dialog).getByRole("button", { name: ru["schedule.filter.apply"] }));

    await waitFor(() => {
      expect(history.location.search).toContain("hallIds");
    });
    await waitFor(() => {
      expect(
        ScheduleListRequestSchema.parse(fake.to("schedule/list").at(-1)?.body).hallIds,
      ).toEqual([session.hall.id]);
    });
  });

  it("одна кнопка «Фильтры» с суммарным счётчиком, «Очистить» снимает отметки", async () => {
    const hallIds = encodeURIComponent(JSON.stringify([session.hall.id]));
    const employeeIds = encodeURIComponent(JSON.stringify([ivanov.id]));
    const { history } = openApp(
      `/schedule?hallIds=${hallIds}&employeeIds=${employeeIds}`,
      server().fetch,
    );
    const user = userEvent.setup();

    const all = await screen.findByRole("button", { name: /^Фильтры/ });
    expect(all).toHaveTextContent("Фильтры2");
    await user.click(all);
    const dialog = await screen.findByRole("dialog", { name: ru["schedule.filters"] });
    expect(within(dialog).getByRole("checkbox", { name: ivanov.name })).toBeChecked();
    await user.click(within(dialog).getByRole("button", { name: ru["schedule.filter.clear"] }));
    expect(within(dialog).getByRole("checkbox", { name: ivanov.name })).not.toBeChecked();
    await user.click(within(dialog).getByRole("button", { name: ru["schedule.filter.apply"] }));

    await waitFor(() => {
      expect(history.location.search).toBe("");
    });
  });
});
