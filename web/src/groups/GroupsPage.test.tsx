import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  EmployeeListItemSchema,
  GroupDisciplineSchema,
  GroupListItemSchema,
  GroupListRequestSchema,
  type DayOfWeek,
  type GroupListItem,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const karate = GroupDisciplineSchema.parse({
  id: "0199a0b2-0000-7000-8000-800000000001",
  name: "Каратэ",
});
const ofp = GroupDisciplineSchema.parse({
  id: "0199a0b2-0000-7000-8000-800000000002",
  name: "ОФП",
});

const kim = EmployeeListItemSchema.parse({
  id: "0199a0b2-0000-7000-8000-700000000001",
  name: "Сергей Ким",
  avatarId: null,
  isOwner: false,
  status: "ACTIVE",
  lastSeenAt: null,
  online: false,
  joinedAt: "2026-01-01T00:00:00Z",
  roles: [],
  phoneNo: null,
  email: null,
});

const HALL_ID = "0199a0b2-0000-7000-8000-900000000001";

/** Слот расписания в «Зале 2»: день [day], время [startAt]–[endAt]. */
const slot = (day: DayOfWeek, startAt: string, endAt: string) => ({
  dayOfWeek: day,
  startAt,
  endAt,
  hallId: HALL_ID,
  hallName: "Зал 2",
  validity: null,
});

/** Группа с номером [n], названием [name] и дисциплинами [disciplineIds]. */
const group = (
  n: number,
  name: string,
  disciplineIds: readonly string[],
  extra: Partial<Record<"schedule" | "scheduleChangeAt" | "employees", unknown>> = {},
) => ({
  disciplineIds,
  item: GroupListItemSchema.parse({
    id: `0199a0b2-0000-7000-8000-6000000000${String(n).padStart(2, "0")}`,
    name,
    schedule: [],
    scheduleChangeAt: null,
    employees: [],
    ...extra,
  }),
});

const gymnastics = group(1, "Гимнастика, старшая", [ofp.id], {
  schedule: [
    slot("MONDAY", "17:00", "18:30"),
    slot("WEDNESDAY", "17:00", "18:30"),
    slot("FRIDAY", "17:00", "18:30"),
    slot("SATURDAY", "11:00", "12:30"),
  ],
  scheduleChangeAt: "2026-12-01",
  employees: [
    { id: kim.id, name: "Ольга Смирнова", avatarId: null },
    { id: "0199a0b2-0000-7000-8000-700000000002", name: "Мария Белова", avatarId: null },
  ],
});

/** 9 групп: две по «Каратэ», остальные по «ОФП». */
const nineGroups = [
  gymnastics,
  group(2, "Каратэ, младшая", [karate.id]),
  group(3, "Каратэ, старшая", [karate.id]),
  ...[4, 5, 6, 7, 8, 9].map((n) => group(n, `ОФП ${String(n)}`, [ofp.id])),
];

/** Сервер списка групп [all]: фильтрует по названию и дисциплинам, `total` — без фильтров. */
function groupsServer(all: readonly { disciplineIds: readonly string[]; item: GroupListItem }[]) {
  return appServer({
    "groups/list": ({ body }) => {
      const request = GroupListRequestSchema.parse(body);
      const name = request.name?.toLowerCase();
      const disciplineIds = request.disciplineIds ?? [];
      const groups = all
        .filter((g) => name === undefined || g.item.name.toLowerCase().includes(name))
        .filter(
          (g) =>
            disciplineIds.length === 0 || disciplineIds.some((id) => g.disciplineIds.includes(id)),
        )
        .map((g) => g.item);
      return json({ groups, total: all.length });
    },
    "disciplines/list": () => json({ disciplines: [karate, ofp] }),
    "employees/list": () => json({ employees: [kim], total: 1 }),
    "halls/list": () => json({ halls: [] }),
  });
}

/** Таблица списка групп (широкий экран); список строк на узком экране дублирует текст. */
const table = async () => within(await screen.findByRole("table"));

describe("список групп", () => {
  it("под заголовком — число показанных групп, с фильтром — отфильтрованных", async () => {
    openApp("/groups", groupsServer(nineGroups).fetch);
    expect(await screen.findByText("9 групп")).toBeVisible();

    openApp(
      `/groups?disciplineIds=${encodeURIComponent(JSON.stringify([karate.id]))}`,
      groupsServer(nineGroups).fetch,
    );
    expect(await screen.findByText("2 группы")).toBeVisible();
  });

  it("на телефоне «Фильтры» показывает сумму выбранных и открывает выбор тренеров", async () => {
    const search = `disciplineIds=${encodeURIComponent(JSON.stringify([karate.id]))}&employeeIds=${encodeURIComponent(JSON.stringify([kim.id]))}`;
    openApp(`/groups?${search}`, groupsServer(nineGroups).fetch);
    const user = userEvent.setup();

    const filters = await screen.findByRole("button", { name: ru["groups.filters"] });
    expect(filters).toHaveTextContent("2");
    await user.click(filters);
    await user.click(await screen.findByRole("menuitem", { name: /Тренеры/ }));
    expect(
      await screen.findByRole("dialog", { name: ru["groups.filter.employees"] }),
    ).toBeVisible();
  });

  it("крестик чипа снимает одну дисциплину, «Сбросить фильтры» очищает адрес", async () => {
    const both = encodeURIComponent(JSON.stringify([karate.id, ofp.id]));
    const { history } = openApp(
      `/groups?q=${encodeURIComponent("гим")}&disciplineIds=${both}`,
      groupsServer(nineGroups).fetch,
    );
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Убрать фильтр «Каратэ»" }));
    await waitFor(() => {
      expect(history.location.search).not.toContain(karate.id);
    });
    expect(history.location.search).toContain(ofp.id);

    await user.click(screen.getByRole("button", { name: ru["groups.resetFilters"] }));
    await waitFor(() => {
      expect(history.location.search).toBe("");
    });
    expect(screen.getByRole("searchbox", { name: ru["groups.search"] })).toHaveValue("");
  });

  it("расписание строками с залом, пометка о смене с датой, клик по строке открывает группу", async () => {
    const { history } = openApp("/groups", groupsServer(nineGroups).fetch);
    const user = userEvent.setup();
    const rows = await table();

    const row = rows.getByRole("link", { name: gymnastics.item.name }).closest("tr");
    if (!(row instanceof HTMLElement)) {
      throw new Error("Нет строки группы");
    }
    const cells = within(row).getAllByRole("cell");
    const scheduleCell = cells[1];
    if (scheduleCell === undefined) {
      throw new Error("Нет ячейки расписания");
    }
    const lines = within(scheduleCell)
      .getAllByRole("listitem")
      .map((li) => li.textContent);
    expect(lines).toEqual(["Пн, Ср, Пт 17:00–18:30 · Зал 2", "Сб 11:00–12:30 · Зал 2"]);
    expect(within(row).getByText("Новое расписание с 01.12.2026")).toBeVisible();
    expect(within(row).getByText("Ольга Смирнова, Мария Белова")).toBeVisible();
    expect(rows.getAllByText(ru["groups.noSchedule"]).length).toBeGreaterThan(0);

    await user.click(rows.getByRole("link", { name: gymnastics.item.name }));
    await waitFor(() => {
      expect(history.location.pathname).toBe(`/groups/${gymnastics.item.id}`);
    });
  });

  it("пустой филиал: «Групп пока нет» без поиска, кнопка открывает панель создания", async () => {
    openApp(`/groups?q=${encodeURIComponent("старое")}`, groupsServer([]).fetch);
    const user = userEvent.setup();

    expect(await screen.findByRole("heading", { name: ru["groups.empty"] })).toBeVisible();
    expect(screen.queryByRole("searchbox")).toBeNull();
    const links = screen.getAllByRole("link", { name: ru["groups.create"] });
    const emptyLink = links.at(-1);
    if (emptyLink === undefined) {
      throw new Error("Нет кнопки «Новая группа»");
    }
    await user.click(emptyLink);
    expect(await screen.findByRole("dialog", { name: ru["groups.create"] })).toBeVisible();
  });

  it("поиск без результатов: «Сбросить фильтры» возвращает список", async () => {
    openApp(`/groups?q=${encodeURIComponent("плав")}`, groupsServer(nineGroups).fetch);
    const user = userEvent.setup();

    expect(await screen.findByRole("heading", { name: ru["groups.noResults"] })).toBeVisible();
    await user.click(screen.getByRole("button", { name: ru["groups.resetFilters"] }));
    expect(await screen.findByText("9 групп")).toBeVisible();
  });
});
