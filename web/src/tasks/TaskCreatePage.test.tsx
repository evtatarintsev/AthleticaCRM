import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CreateTaskRequestSchema, TaskDetailResponseSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

/** Сервер, принимающий создание задачи и отдающий её карточку. */
function createServer() {
  return appServer({
    "employees/list": () => json({ employees: [], total: 0 }),
    "tasks/create": ({ body }) => {
      const request = CreateTaskRequestSchema.parse(body);
      return json(
        TaskDetailResponseSchema.parse({
          id: request.id,
          createdBy: "0199a0b2-7c3e-7d2a-9f10-000000000002",
          createdByName: "Иван Петров",
          assigneeId: null,
          assigneeName: null,
          clientId: null,
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

describe("создание задачи", () => {
  it("требует заголовок", async () => {
    const api = createServer();
    openApp("/tasks/new", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tasks.create"] }));

    expect(await screen.findByText(ru["error.required"])).toBeVisible();
    expect(api.to("tasks/create")).toHaveLength(0);
  });

  it("создаёт задачу с заголовком и переходит к её карточке", async () => {
    const api = createServer();
    const { history } = openApp("/tasks/new", api.fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(ru["tasks.field.title"]), "Новая задача");
    await user.click(screen.getByRole("button", { name: ru["tasks.create"] }));

    const created = api.to("tasks/create").map((r) => CreateTaskRequestSchema.parse(r.body));
    expect(created).toHaveLength(1);
    const [first] = created;
    if (first === undefined) {
      throw new Error("запрос создания задачи не найден");
    }
    expect(first.title).toBe("Новая задача");
    expect(history.location.pathname).toBe(`/web/tasks/${first.id}`);
  });
});
