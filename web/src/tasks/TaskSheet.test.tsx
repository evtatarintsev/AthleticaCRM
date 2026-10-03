import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AssignTaskRequestSchema,
  AttachTaskUploadRequestSchema,
  TaskDetailResponseSchema,
  UnassignTaskRequestSchema,
  UpdateTaskRequestSchema,
  UpdateTaskStatusRequestSchema,
  UploadIdSchema,
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

const clientId = "0199a0b2-0000-7000-8000-000000000001";
const uploadId = UploadIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000601");

const task = TaskDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  createdBy: anna.id,
  createdByName: anna.name,
  assigneeId: null,
  assigneeName: null,
  clientId,
  clientName: "Пётр Петров",
  title: "Позвонить Петрову",
  description: "Уточнить расписание",
  status: "PENDING",
  dueDate: "2026-01-10T10:00:00Z",
  dueDateEnd: null,
  completedAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  attachments: [],
});

/**
 * Сервер с одной задачей [current], меняющий её по запросам. [assignFailure] — текст
 * ошибки назначения исполнителя.
 */
function taskServer(current: TaskDetailResponse, assignFailure: string | null = null) {
  let state = current;
  return appServer({
    "tasks/list": () =>
      json({
        tasks: [
          {
            id: state.id,
            title: state.title,
            assigneeId: state.assigneeId,
            assigneeName: state.assigneeName,
            clientId: state.clientId,
            clientName: state.clientName,
            status: state.status,
            dueDate: state.dueDate,
            dueDateEnd: state.dueDateEnd,
          },
        ],
        total: 1,
      }),
    "employees/list": () => json({ employees: [anna], total: 1 }),
    "clients/list": () => json({ clients: [], total: 0 }),
    "tasks/detail": () => json(state),
    "tasks/status": ({ body }) => {
      const request = UpdateTaskStatusRequestSchema.parse(body);
      state = { ...state, status: request.status };
      return json({ updated: request.taskIds.length });
    },
    "tasks/assign": ({ body }) => {
      if (assignFailure !== null) {
        return json({ code: "CONFLICT", message: assignFailure, fields: null }, 409);
      }
      const request = AssignTaskRequestSchema.parse(body);
      state = { ...state, assigneeId: request.assigneeId, assigneeName: anna.name };
      return json({ updated: request.taskIds.length });
    },
    "tasks/unassign": ({ body }) => {
      const request = UnassignTaskRequestSchema.parse(body);
      state = { ...state, assigneeId: null, assigneeName: null };
      return json({ updated: request.taskIds.length });
    },
    "tasks/update": ({ body }) => {
      const request = UpdateTaskRequestSchema.parse(body);
      state = {
        ...state,
        title: request.title,
        description: request.description,
        clientId: request.clientId,
        clientName: request.clientId === null ? null : state.clientName,
        dueDate: request.dueDate,
        dueDateEnd: request.dueDateEnd,
      };
      return json(state);
    },
    upload: () =>
      json({
        id: uploadId,
        url: "https://files.example/plan.pdf",
        originalName: "plan.pdf",
        contentType: "application/pdf",
        sizeBytes: 3,
      }),
    "tasks/attach": ({ body }) => {
      AttachTaskUploadRequestSchema.parse(body);
      state = {
        ...state,
        attachments: [
          {
            id: uploadId,
            url: "https://files.example/plan.pdf",
            originalName: "plan.pdf",
            contentType: "application/pdf",
            sizeBytes: 3,
          },
        ],
      };
      return json(state);
    },
  });
}

/** Панель карточки задачи с заголовком [title]. */
const card = async (title = task.title) =>
  within(await screen.findByRole("dialog", { name: title }));

describe("карточка задачи", () => {
  it("клик по строке списка открывает карточку и добавляет задачу в адрес", async () => {
    const { history } = openApp("/tasks?statuses=%5B%22PENDING%22%5D", taskServer(task).fetch);
    const user = userEvent.setup();

    await user.click(await within(await screen.findByRole("table")).findByText(task.title));

    const sheet = await card();
    expect(sheet.getByText(task.description)).toBeVisible();
    expect(history.location.search).toContain(`task=${task.id}`);

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(history.location.search).not.toContain("task=");
    expect(history.location.search).toContain("statuses=");
  });

  it("показывает статус, клиента и описание", async () => {
    openApp(`/tasks?task=${task.id}`, taskServer(task).fetch);

    const sheet = await card();
    expect(sheet.getByText(ru["taskStatus.PENDING"])).toBeVisible();
    expect(sheet.getByText("Пётр Петров")).toBeVisible();
    expect(sheet.getByText(task.description)).toBeVisible();
  });

  it("некорректный идентификатор в адресе не открывает панель", async () => {
    const api = taskServer(task);
    openApp("/tasks?task=not-a-uuid", api.fetch);

    await screen.findByRole("table");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(api.to("tasks/detail")).toHaveLength(0);
  });

  it("меняет статус через меню сразу", async () => {
    const api = taskServer(task);
    openApp(`/tasks?task=${task.id}`, api.fetch);
    const user = userEvent.setup();

    const sheet = await card();
    await user.click(sheet.getByRole("button", { name: ru["tasks.changeStatus"] }));
    await user.click(await screen.findByRole("menuitem", { name: ru["taskStatus.COMPLETED"] }));

    expect(await sheet.findByText(ru["taskStatus.COMPLETED"])).toBeVisible();
    expect(api.to("tasks/status").map((r) => UpdateTaskStatusRequestSchema.parse(r.body))).toEqual([
      { taskIds: [task.id], status: "COMPLETED" },
    ]);
  });

  it("назначает и снимает исполнителя через панель выбора", async () => {
    const api = taskServer(task);
    openApp(`/tasks?task=${task.id}`, api.fetch);
    const user = userEvent.setup();

    const sheet = await card();
    await user.click(sheet.getByRole("button", { name: /^Исполнитель/ }));
    const picker = within(await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }));
    await user.click(await picker.findByRole("button", { name: anna.name }));

    expect(await sheet.findByRole("button", { name: `Исполнитель ${anna.name}` })).toBeVisible();
    expect(api.to("tasks/assign").map((r) => AssignTaskRequestSchema.parse(r.body))).toEqual([
      { taskIds: [task.id], assigneeId: anna.id },
    ]);

    await user.click(sheet.getByRole("button", { name: /^Исполнитель/ }));
    const again = within(await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }));
    expect(again.getByRole("button", { name: anna.name })).toHaveAttribute("aria-current", "true");
    await user.click(again.getByRole("button", { name: ru["tasks.assigneeUnassigned"] }));

    expect(
      await sheet.findByRole("button", {
        name: `Исполнитель ${ru["tasks.assigneeUnassigned"]}`,
      }),
    ).toBeVisible();
    expect(api.to("tasks/unassign")).toHaveLength(1);
  });

  it("ошибка назначения оставляет панель выбора открытой с текстом ошибки", async () => {
    openApp(`/tasks?task=${task.id}`, taskServer(task, "Сотрудник уволен").fetch);
    const user = userEvent.setup();

    const sheet = await card();
    await user.click(sheet.getByRole("button", { name: /^Исполнитель/ }));
    const picker = within(await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }));
    await user.click(await picker.findByRole("button", { name: anna.name }));

    expect(await picker.findByRole("alert")).toHaveTextContent("Сотрудник уволен");
    await user.keyboard("{Escape}");
    expect(
      await sheet.findByRole("button", { name: `Исполнитель ${ru["tasks.assigneeUnassigned"]}` }),
    ).toBeVisible();
  });

  it("прикрепляет файл", async () => {
    const api = taskServer(task);
    openApp(`/tasks?task=${task.id}`, api.fetch);
    const user = userEvent.setup();

    await card();
    const input = document.body.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    if (input !== null) {
      await user.upload(input, new File(["pdf"], "plan.pdf", { type: "application/pdf" }));
    }

    expect(await screen.findByRole("link", { name: "plan.pdf" })).toBeVisible();
    expect(api.to("tasks/attach").map((r) => AttachTaskUploadRequestSchema.parse(r.body))).toEqual([
      { taskId: task.id, uploadId },
    ]);
  });
});

/** Открывает карточку задачи и в ней панель редактирования. */
async function openEdit() {
  const user = userEvent.setup();
  const sheet = await card();
  await user.click(sheet.getByRole("button", { name: ru["action.edit"] }));
  const form = within(await screen.findByRole("dialog", { name: ru["tasks.editTitle"] }));
  return { user, form };
}

describe("редактирование задачи", () => {
  it("сохраняет заголовок и описание, не теряя клиента и сроки", async () => {
    const api = taskServer(task);
    openApp(`/tasks?task=${task.id}`, api.fetch);
    const { user, form } = await openEdit();

    const title = form.getByLabelText(ru["tasks.field.title"]);
    await user.clear(title);
    await user.type(title, "Позвонить Петрову ещё раз");
    const description = form.getByLabelText(ru["tasks.field.description"]);
    await user.clear(description);
    await user.type(description, "Перенести на вечер");
    await user.click(form.getByRole("button", { name: ru["action.save"] }));

    await card("Позвонить Петрову ещё раз");
    expect(screen.queryByRole("dialog", { name: ru["tasks.editTitle"] })).not.toBeInTheDocument();
    expect(api.to("tasks/update").map((r) => UpdateTaskRequestSchema.parse(r.body))).toEqual([
      {
        id: task.id,
        title: "Позвонить Петрову ещё раз",
        description: "Перенести на вечер",
        clientId,
        dueDate: task.dueDate,
        dueDateEnd: null,
      },
    ]);
    expect(api.to("tasks/assign")).toHaveLength(0);
    expect(api.to("tasks/status")).toHaveLength(0);
  });

  it("отвязывает клиента", async () => {
    const api = taskServer(task);
    openApp(`/tasks?task=${task.id}`, api.fetch);
    const { user, form } = await openEdit();

    await user.click(form.getByRole("button", { name: ru["tasks.removeClient"] }));
    expect(
      form.getByRole("button", { name: `Клиент ${ru["tasks.noClient"]}` }),
    ).toBeInTheDocument();
    await user.click(form.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(api.to("tasks/update")).toHaveLength(1);
    });
    const [update] = api.to("tasks/update").map((r) => UpdateTaskRequestSchema.parse(r.body));
    expect(update?.clientId).toBeNull();
  });

  it("Esc после правки спрашивает подтверждение и закрывает только редактирование", async () => {
    const api = taskServer(task);
    openApp(`/tasks?task=${task.id}`, api.fetch);
    const { user, form } = await openEdit();

    await user.type(form.getByLabelText(ru["tasks.field.title"]), "!");
    await user.keyboard("{Escape}");
    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["tasks.editTitle"] })).not.toBeInTheDocument();
    });
    expect(await card()).toBeTruthy();
    expect(api.to("tasks/update")).toHaveLength(0);
  });
});
