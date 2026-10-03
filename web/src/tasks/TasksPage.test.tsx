import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AssignTaskRequestSchema,
  TaskListItemSchemaSchema,
  TaskListRequestSchema,
  UpdateTaskStatusRequestSchema,
  type TaskListItemSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const anna = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000401",
  name: "Анна Иванова",
  avatarId: null,
  isOwner: true,
  isActive: true,
  joinedAt: "2024-01-01T00:00:00Z",
  roles: [],
  phoneNo: null,
  email: "anna@example.com",
};
const boris = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000402",
  name: "Борис Петров",
  avatarId: null,
  isOwner: false,
  isActive: true,
  joinedAt: "2024-01-01T00:00:00Z",
  roles: [],
  phoneNo: null,
  email: "boris@example.com",
};

const callAnna = TaskListItemSchemaSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  title: "Позвонить Анне",
  assigneeId: anna.id,
  assigneeName: anna.name,
  clientId: null,
  clientName: null,
  status: "PENDING",
  dueDate: "2026-01-10T10:00:00Z",
  dueDateEnd: null,
});

const preparePlan = TaskListItemSchemaSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000502",
  title: "Подготовить план тренировок",
  assigneeId: null,
  assigneeName: null,
  clientId: null,
  clientName: null,
  status: "IN_PROGRESS",
  dueDate: null,
  dueDateEnd: null,
});

/**
 * Сервер со списком задач [tasks]: фильтрует по `onlyMine`, статусам и тексту, как сервер.
 * [assignFailure] — текст ошибки назначения исполнителя.
 */
function tasksServer(tasks: readonly TaskListItemSchema[], assignFailure: string | null = null) {
  return appServer({
    "tasks/list": ({ body }) => {
      const request = TaskListRequestSchema.parse(body);
      const filtered = tasks.filter((task) => {
        if (request.onlyMine === true && task.assigneeId !== anna.id) {
          return false;
        }
        if (
          request.statuses !== undefined &&
          request.statuses.length > 0 &&
          !request.statuses.includes(task.status)
        ) {
          return false;
        }
        if (
          request.searchText !== null &&
          request.searchText !== undefined &&
          request.searchText !== "" &&
          !task.title.toLowerCase().includes(request.searchText.toLowerCase())
        ) {
          return false;
        }
        return true;
      });
      return json({ tasks: filtered, total: filtered.length });
    },
    "employees/list": () => json({ employees: [anna, boris], total: 2 }),
    "tasks/status": () => json({ updated: 1 }),
    "tasks/assign": () =>
      assignFailure === null
        ? json({ updated: 1 })
        : json({ code: "CONFLICT", message: assignFailure, fields: null }, 409),
    "tasks/unassign": () => json({ updated: 1 }),
  });
}

/** Таблица списка задач (широкий экран); карточки на узком экране дублируют текст. */
function table() {
  return within(screen.getByRole("table"));
}

describe("список задач", () => {
  it("показывает задачи с числом найденного", async () => {
    openApp("/tasks", tasksServer([callAnna, preparePlan]).fetch);

    expect(await screen.findByRole("table")).toBeVisible();
    expect(table().getByText("Позвонить Анне")).toBeVisible();
    expect(table().getByText("Подготовить план тренировок")).toBeVisible();
    expect(screen.getByText("2 задачи")).toBeVisible();
  });

  it("поиск фильтрует список и сохраняется в адресе", async () => {
    const api = tasksServer([callAnna, preparePlan]);
    const { history } = openApp("/tasks", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.type(screen.getByRole("searchbox", { name: ru["tasks.searchPlaceholder"] }), "Анне");

    await screen.findByText("1 задача");
    expect(table().queryByText("Подготовить план тренировок")).not.toBeInTheDocument();
    expect(history.location.search).toContain("q=");
  });

  it("быстрый вид «Мои задачи» переживает перезагрузку", async () => {
    const api = tasksServer([callAnna, preparePlan]);
    openApp("/tasks?onlyMine=true", api.fetch);

    await screen.findByRole("table");
    expect(table().getByText("Позвонить Анне")).toBeVisible();
    expect(table().queryByText("Подготовить план тренировок")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: ru["tasks.view.mine"] })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("фильтр по статусу применяется через панель и виден чипом", async () => {
    const api = tasksServer([callAnna, preparePlan]);
    openApp("/tasks", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: ru["tasks.filters"] }));
    const sheet = await screen.findByRole("dialog", { name: ru["tasks.filters"] });
    await user.click(within(sheet).getByRole("button", { name: ru["taskStatus.IN_PROGRESS"] }));
    await user.click(within(sheet).getByRole("button", { name: ru["tasks.filter.apply"] }));

    expect(await screen.findAllByText(ru["taskStatus.IN_PROGRESS"])).not.toHaveLength(0);
    expect(table().queryByText("Позвонить Анне")).not.toBeInTheDocument();
    const requests = api.to("tasks/list").map((r) => TaskListRequestSchema.parse(r.body));
    expect(requests.at(-1)?.statuses).toEqual(["IN_PROGRESS"]);
  });

  it("массовая смена статуса отправляет выбранные задачи и снимает выбор", async () => {
    const api = tasksServer([callAnna, preparePlan]);
    openApp("/tasks", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(table().getByRole("checkbox", { name: "Выбрать «Позвонить Анне»" }));
    expect(screen.getByText("Выбрано: 1")).toBeVisible();

    await user.click(screen.getByRole("button", { name: ru["tasks.bulkChangeStatus"] }));
    await user.click(await screen.findByRole("menuitem", { name: ru["taskStatus.COMPLETED"] }));

    expect(api.to("tasks/status").map((r) => UpdateTaskStatusRequestSchema.parse(r.body))).toEqual([
      { taskIds: [callAnna.id], status: "COMPLETED" },
    ]);
    await table().findByText("Позвонить Анне");
    expect(screen.queryByText(/Выбрано/)).not.toBeInTheDocument();
  });

  it("массовое назначение через панель выбора отправляет выбранные задачи и снимает выбор", async () => {
    const api = tasksServer([callAnna, preparePlan]);
    openApp("/tasks", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(
      table().getByRole("checkbox", { name: "Выбрать «Подготовить план тренировок»" }),
    );
    await user.click(screen.getByRole("button", { name: ru["tasks.bulkAssign"] }));
    const picker = within(await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }));
    expect(picker.queryByRole("button", { current: true })).not.toBeInTheDocument();
    await user.click(await picker.findByRole("button", { name: boris.name }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(api.to("tasks/assign").map((r) => AssignTaskRequestSchema.parse(r.body))).toEqual([
      { taskIds: [preparePlan.id], assigneeId: boris.id },
    ]);
    expect(api.to("tasks/unassign")).toHaveLength(0);
    expect(screen.queryByText(/Выбрано/)).not.toBeInTheDocument();
  });

  it("«Не назначен» в массовом назначении снимает исполнителя", async () => {
    const api = tasksServer([callAnna, preparePlan]);
    openApp("/tasks", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(table().getByRole("checkbox", { name: "Выбрать «Позвонить Анне»" }));
    await user.click(screen.getByRole("button", { name: ru["tasks.bulkAssign"] }));
    const picker = within(await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }));
    await user.click(picker.getByRole("button", { name: ru["tasks.assigneeUnassigned"] }));

    await waitFor(() => {
      expect(api.to("tasks/unassign")).toHaveLength(1);
    });
    expect(api.to("tasks/assign")).toHaveLength(0);
  });

  it("ошибка массового назначения оставляет панель открытой и выбор в списке", async () => {
    openApp("/tasks", tasksServer([callAnna, preparePlan], "Нет прав").fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(table().getByRole("checkbox", { name: "Выбрать «Позвонить Анне»" }));
    await user.click(screen.getByRole("button", { name: ru["tasks.bulkAssign"] }));
    const picker = within(await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }));
    await user.click(await picker.findByRole("button", { name: boris.name }));

    expect(await picker.findByRole("alert")).toHaveTextContent("Нет прав");
    await user.keyboard("{Escape}");
    expect(await screen.findByText("Выбрано: 1")).toBeVisible();
  });

  it("«Создать задачу» открывает панель создания поверх списка", async () => {
    const { history } = openApp("/tasks?onlyMine=true", tasksServer([callAnna]).fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(screen.getByRole("link", { name: ru["tasks.create"] }));

    expect(await screen.findByRole("dialog", { name: ru["tasks.newTitle"] })).toBeVisible();
    expect(history.location.search).toContain("create=true");
    expect(history.location.search).toContain("onlyMine=true");
  });

  it("клик по задаче открывает карточку, закрытие сохраняет фильтр", async () => {
    const api = tasksServer([callAnna]);
    const { history } = openApp("/tasks?onlyMine=true", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("table");
    await user.click(table().getByRole("link", { name: "Позвонить Анне" }));

    expect(history.location.search).toContain(`task=${callAnna.id}`);
    expect(await screen.findByRole("dialog")).toBeVisible();
    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(history.location.search).not.toContain("task=");
    });
    expect(history.location.search).toContain("onlyMine=true");
  });
});
