import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientListItemSchema,
  CreateTaskRequestSchema,
  TaskDetailResponseSchema,
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

const petrov = ClientListItemSchema.parse({
  id: "0199a0b2-0000-7000-8000-000000000001",
  name: "Пётр Петров",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [],
  balance: { minorUnits: 0, currency: "RUB" },
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

/** Сервер, принимающий создание задачи; [failure] — текст ошибки создания. */
function createServer(failure: string | null = null) {
  return appServer({
    "tasks/list": () => json({ tasks: [], total: 0 }),
    "employees/list": () => json({ employees: [anna], total: 1 }),
    "clients/list": () => json({ clients: [petrov], total: 1 }),
    upload: () =>
      json({
        id: "0199a0b2-7c3e-7d2a-9f10-000000000601",
        url: "https://files.example/photo.png",
        originalName: "photo.png",
        contentType: "image/png",
        sizeBytes: 2048,
      }),
    "tasks/create": ({ body }) => {
      if (failure !== null) {
        return json({ code: "CONFLICT", message: failure, fields: null }, 409);
      }
      const request = CreateTaskRequestSchema.parse(body);
      return json(
        TaskDetailResponseSchema.parse({
          id: request.id,
          createdBy: anna.id,
          createdByName: anna.name,
          assigneeId: request.assigneeId,
          assigneeName: null,
          clientId: request.clientId,
          clientName: null,
          title: request.title,
          description: request.description,
          status: "PENDING",
          dueDate: null,
          dueDateEnd: null,
          completedAt: null,
          createdAt: "2026-01-01T00:00:00Z",
          attachments: [],
        }),
      );
    },
  });
}

/** Панель создания задачи. */
const createSheet = async () =>
  within(await screen.findByRole("dialog", { name: ru["tasks.newTitle"] }));

describe("создание задачи", () => {
  it("кнопка в списке открывает панель и добавляет её в адрес", async () => {
    const { history } = openApp("/tasks", createServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("link", { name: ru["tasks.create"] }));

    await createSheet();
    expect(history.location.search).toContain("create=true");
  });

  it("требует заголовок", async () => {
    const api = createServer();
    openApp("/tasks?create=true", api.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.click(sheet.getByRole("button", { name: ru["action.create"] }));

    expect(await sheet.findByText(ru["error.required"])).toBeVisible();
    expect(api.to("tasks/create")).toHaveLength(0);
  });

  it("создаёт задачу с клиентом и исполнителем и закрывает панель", async () => {
    const api = createServer();
    const { history } = openApp("/tasks?create=true", api.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(sheet.getByLabelText(ru["tasks.field.title"]), "Новая задача");

    await user.click(sheet.getByRole("button", { name: /^Клиент/ }));
    const clients = within(await screen.findByRole("dialog", { name: ru["tasks.field.client"] }));
    await user.click(await clients.findByRole("button", { name: petrov.name }));
    expect(await sheet.findByRole("button", { name: `Клиент ${petrov.name}` })).toBeVisible();

    await user.click(sheet.getByRole("button", { name: /^Исполнитель/ }));
    const assignees = within(
      await screen.findByRole("dialog", { name: ru["tasks.field.assignee"] }),
    );
    await user.click(await assignees.findByRole("button", { name: anna.name }));
    expect(await sheet.findByRole("button", { name: `Исполнитель ${anna.name}` })).toBeVisible();

    await user.click(sheet.getByRole("button", { name: ru["action.create"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    const created = api.to("tasks/create").map((r) => CreateTaskRequestSchema.parse(r.body));
    expect(created).toHaveLength(1);
    expect(created[0]).toMatchObject({
      title: "Новая задача",
      clientId: petrov.id,
      assigneeId: anna.id,
    });
    expect(history.location.search).not.toContain("create");
  });

  it("закрытие с введёнными данными спрашивает подтверждение", async () => {
    const api = createServer();
    openApp("/tasks?create=true", api.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(sheet.getByLabelText(ru["tasks.field.title"]), "Черновик");
    await user.keyboard("{Escape}");

    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(api.to("tasks/create")).toHaveLength(0);
  });

  it("при ошибке сервера остаётся открытой с введёнными данными", async () => {
    openApp("/tasks?create=true", createServer("Задача уже существует").fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(sheet.getByLabelText(ru["tasks.field.title"]), "Новая задача");
    await user.click(sheet.getByRole("button", { name: ru["action.create"] }));

    expect(await sheet.findByRole("alert")).toHaveTextContent("Задача уже существует");
    expect(sheet.getByLabelText(ru["tasks.field.title"])).toHaveValue("Новая задача");
  });

  it("прикреплённое изображение открывается в просмотрщике, Esc не теряет введённое", async () => {
    openApp("/tasks?create=true", createServer().fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(sheet.getByLabelText(ru["tasks.field.title"]), "Черновик");
    const input = document.body.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    if (input !== null) {
      await user.upload(input, new File(["png"], "photo.png", { type: "image/png" }));
    }
    await user.click(await sheet.findByRole("button", { name: "Открыть «photo.png»" }));
    const viewer = within(
      await screen.findByRole("dialog", { name: ru["attachments.viewerTitle"] }),
    );
    expect(viewer.getByRole("img", { name: "photo.png" })).toBeVisible();

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(
        screen.queryByRole("dialog", { name: ru["attachments.viewerTitle"] }),
      ).not.toBeInTheDocument();
    });
    expect(screen.queryByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeNull();
    expect((await createSheet()).getByLabelText(ru["tasks.field.title"])).toHaveValue("Черновик");
  });
});
