import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  EmployeeIdSchema,
  GroupDetailResponseSchema,
  GroupSessionsRequestSchema,
  HallIdSchema,
  LocalTimeSchema,
  ScheduleListRequestSchema,
  SessionIdSchema,
  type GroupSessionSchema,
  type GroupSessionsResponse,
  type LocalDate,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { appServer, json, openApp } from "@/test/app";
import { defaultSessionsWindow, shiftSessionsWindow } from "./groupSessionsWindow";

const group = GroupDetailResponseSchema.parse({
  id: "0199a0b2-0000-7000-8000-600000000001",
  name: "Юниоры",
  schedule: [],
  scheduleChangeAt: null,
  disciplines: [],
  employees: [],
  clients: [],
});

const today = todayLocalDate();

/** Занятие группы номер [n] в день [date] со значениями строки по умолчанию и правками [overrides]. */
const groupSession = (
  n: number,
  date: LocalDate,
  overrides: Partial<GroupSessionSchema> = {},
): GroupSessionSchema => ({
  id: SessionIdSchema.parse(`0199a0b2-0000-7000-8000-a0000000000${String(n)}`),
  date,
  startTime: LocalTimeSchema.parse("18:00"),
  endTime: LocalTimeSchema.parse("19:30"),
  hall: { id: HallIdSchema.parse("0199a0b2-0000-7000-8000-900000000001"), name: "Большой" },
  coaches: [{ id: EmployeeIdSchema.parse("0199a0b2-0000-7000-8000-700000000001"), name: "Иванов" }],
  coachesOverridden: false,
  status: "SCHEDULED",
  rescheduledFrom: null,
  isManual: false,
  attendance: null,
  ...overrides,
});

const completed = groupSession(1, addDays(today, -7), {
  status: "COMPLETED",
  attendance: { present: 8, total: 12 },
});
const moved = groupSession(2, addDays(today, 3), {
  rescheduledFrom: addDays(today, 2),
  coachesOverridden: true,
  coaches: [{ id: EmployeeIdSchema.parse("0199a0b2-0000-7000-8000-700000000002"), name: "Петров" }],
});
const manual = groupSession(3, addDays(today, 5), { isManual: true, coaches: [] });
const cancelled = groupSession(4, addDays(today, 9), { status: "CANCELLED" });

/** Поддельный сервер карточки группы; занятия отдаёт [sessions], сбой — при [fail]. */
function server(
  sessions: (request: { from: LocalDate; to: LocalDate }) => GroupSessionsResponse,
  { fail = false }: { fail?: boolean } = {},
) {
  return appServer({
    "groups/detail": () => json(group),
    "disciplines/list": () => json({ disciplines: [] }),
    "employees/list": () => json({ employees: [], total: 0 }),
    "halls/list": () => json({ halls: [] }),
    "groups/list-for-select": () => json([{ id: group.id, name: group.name }]),
    "schedule/list": () => json({ sessions: [] }),
    "groups/sessions": ({ query }) => {
      if (fail) {
        return json({ code: "INTERNAL", message: "" }, 500);
      }
      const request = GroupSessionsRequestSchema.parse(Object.fromEntries(query));
      return json(sessions(request));
    },
  });
}

const standard = () => ({
  sessions: [completed, moved, manual, cancelled],
  last: completed,
  next: moved,
});

/** Блок «Занятия» карточки группы. */
const sessionsBlock = async () => {
  const heading = await screen.findByRole("heading", { name: ru["groups.sessions.title"] });
  const block = heading.closest("section");
  if (block === null) {
    throw new Error("Блок занятий не найден");
  }
  return block;
};

describe("блок занятий в карточке группы", () => {
  it("запрашивает окно по умолчанию и показывает строки с бейджами и посещаемостью", async () => {
    const fake = server(standard);
    openApp(`/groups/${group.id}`, fake.fetch);
    const block = await sessionsBlock();

    expect(await within(block).findAllByText(/8 из 12/)).not.toHaveLength(0);
    expect(within(block).getAllByText(/Перенесено с/)).not.toHaveLength(0);
    expect(within(block).getAllByText(/Петров \(замена\)/)).not.toHaveLength(0);
    expect(within(block).getByText(ru["groups.sessions.manual"])).toBeVisible();
    expect(within(block).getByText(ru["sessionDetail.status.CANCELLED"])).toBeVisible();
    expect(within(block).getByText(ru["groups.sessions.last"])).toBeVisible();
    expect(within(block).getByText(ru["groups.sessions.next"])).toBeVisible();

    const request = GroupSessionsRequestSchema.parse(
      Object.fromEntries(fake.to("groups/sessions")[0]?.query ?? []),
    );
    expect(request).toEqual({ groupId: group.id, ...defaultSessionsWindow(today) });
  });

  it("без последнего и ближайшего не показывает закреплённые позиции", async () => {
    openApp(`/groups/${group.id}`, server(() => ({ sessions: [], last: null, next: null })).fetch);
    const block = await sessionsBlock();

    expect(await within(block).findByText(ru["groups.sessions.empty"])).toBeVisible();
    expect(within(block).queryByText(ru["groups.sessions.last"])).not.toBeInTheDocument();
    expect(within(block).queryByText(ru["groups.sessions.next"])).not.toBeInTheDocument();
  });

  it("сдвигает период назад и запрашивает занятия нового окна", async () => {
    const fake = server(standard);
    openApp(`/groups/${group.id}`, fake.fetch);
    const user = userEvent.setup();
    const block = await sessionsBlock();

    await user.click(within(block).getByRole("button", { name: ru["groups.sessions.prevPeriod"] }));

    const previous = shiftSessionsWindow(defaultSessionsWindow(today), -1);
    await waitFor(() => {
      const requests = fake
        .to("groups/sessions")
        .map((r) => GroupSessionsRequestSchema.parse(Object.fromEntries(r.query)));
      expect(requests).toContainEqual({ groupId: group.id, ...previous });
    });
  });

  it("клик по строке открывает карточку занятия", async () => {
    const { history } = openApp(`/groups/${group.id}`, server(standard).fetch);
    const user = userEvent.setup();
    const block = await sessionsBlock();

    await user.click((await within(block).findAllByText(ru["groups.sessions.manual"]))[0] ?? block);

    expect(history.location.pathname).toBe(`/sessions/${manual.id}`);
  });

  it("«Календарь» открывает расписание с фильтром по группе", async () => {
    const fake = server(standard);
    const { history } = openApp(`/groups/${group.id}`, fake.fetch);
    const user = userEvent.setup();
    const block = await sessionsBlock();

    await user.click(within(block).getByRole("link", { name: ru["groups.sessions.calendar"] }));

    expect(history.location.pathname).toBe("/schedule");
    await waitFor(() => {
      expect(fake.to("schedule/list")).not.toHaveLength(0);
    });
    expect(ScheduleListRequestSchema.parse(fake.to("schedule/list")[0]?.body).groupIds).toEqual([
      group.id,
    ]);
    expect(
      await screen.findByText(ru["schedule.filter.group"].replace("{name}", group.name)),
    ).toBeVisible();
  });

  it("ошибка загрузки показывает повтор, остальные секции карточки работают", async () => {
    openApp(`/groups/${group.id}`, server(standard, { fail: true }).fetch);
    const block = await sessionsBlock();

    expect(
      await within(block).findByRole("button", { name: ru["action.retry"] }, { timeout: 5000 }),
    ).toBeVisible();
    expect(screen.getByRole("heading", { name: ru["groups.detail.clientsTitle"] })).toBeVisible();
  });
});
