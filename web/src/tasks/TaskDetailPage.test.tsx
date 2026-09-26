import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AssignTaskRequestSchema,
  TaskDetailResponseSchema,
  UpdateTaskStatusRequestSchema,
  type TaskDetailResponse,
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

const task = TaskDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  createdBy: anna.id,
  createdByName: anna.name,
  assigneeId: null,
  assigneeName: null,
  clientId: null,
  clientName: null,
  title: "Позвонить Анне",
  description: "Уточнить расписание",
  status: "PENDING",
  dueDate: null,
  dueDateEnd: null,
  completedAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  attachments: [],
});

/** Сервер с одной задачей [current], меняющий её статус и исполнителя по запросам. */
function taskServer(current: TaskDetailResponse) {
  let state = current;
  return appServer({
    "employees/list": () => json({ employees: [anna], total: 1 }),
    "tasks/detail": () => json(state),
    "tasks/status": ({ body }) => {
      const request = UpdateTaskStatusRequestSchema.parse(body);
      state = { ...state, status: request.status };
      return json({ updated: request.taskIds.length });
    },
    "tasks/assign": ({ body }) => {
      const request = AssignTaskRequestSchema.parse(body);
      state = { ...state, assigneeId: request.assigneeId, assigneeName: anna.name };
      return json({ updated: request.taskIds.length });
    },
  });
}

describe("карточка задачи", () => {
  it("показывает заголовок, описание и статус", async () => {
    const api = taskServer(task);
    openApp(`/tasks/${task.id}`, api.fetch);

    expect(await screen.findByText(task.title)).toBeVisible();
    expect(screen.getByText(task.description)).toBeVisible();
    expect(screen.getByText(ru["taskStatus.PENDING"])).toBeVisible();
  });

  it("меняет статус через меню", async () => {
    const api = taskServer(task);
    openApp(`/tasks/${task.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByText(task.title);
    await user.click(screen.getByRole("button", { name: ru["tasks.changeStatus"] }));
    await user.click(await screen.findByRole("menuitem", { name: ru["taskStatus.COMPLETED"] }));

    expect(await screen.findByText(ru["taskStatus.COMPLETED"])).toBeVisible();
    expect(api.to("tasks/status").map((r) => UpdateTaskStatusRequestSchema.parse(r.body))).toEqual([
      { taskIds: [task.id], status: "COMPLETED" },
    ]);
  });

  it("назначает исполнителя через выбор", async () => {
    const api = taskServer(task);
    openApp(`/tasks/${task.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByText(task.title);
    await user.selectOptions(screen.getByLabelText(ru["tasks.field.assignee"]), anna.name);

    expect(await screen.findByDisplayValue(anna.name)).toBeVisible();
    expect(api.to("tasks/assign").map((r) => AssignTaskRequestSchema.parse(r.body))).toEqual([
      { taskIds: [task.id], assigneeId: anna.id },
    ]);
  });
});
