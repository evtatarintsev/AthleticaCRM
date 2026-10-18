import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { appServer, json, openApp } from "@/test/app";

const actorId = "0199a0b2-7c3e-7d2a-9f10-000000000011";
const clientId = "0199a0b2-7c3e-7d2a-9f10-000000000021";
const documentId = "0199a0b2-7c3e-7d2a-9f10-000000000031";

describe("журнал действий", () => {
  it("показывает описание, ведёт документ к клиенту и фильтрует по автору", async () => {
    const api = appServer({
      "audit/log/users": () => json({ items: [{ id: actorId, username: "Анна" }] }),
      "audit/log": () =>
        json({
          items: [
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000041",
              userId: actorId,
              username: "Анна",
              actionType: "create",
              description: "Анна: добавила документ «Паспорт»",
              entityType: "client_doc",
              entityId: documentId,
              subject: {
                type: "client_doc",
                id: documentId,
                title: "Паспорт",
                isAvailable: true,
                targetId: clientId,
              },
              data: null,
              ipAddress: null,
              createdAt: "2026-10-17T10:00:00Z",
            },
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000042",
              userId: actorId,
              username: "Анна",
              actionType: "delete",
              description: "Анна: удалила клиента",
              entityType: "client",
              entityId: clientId,
              subject: {
                type: "client",
                id: clientId,
                title: "Удалённый клиент",
                isAvailable: false,
                targetId: null,
              },
              data: null,
              ipAddress: null,
              createdAt: "2026-10-17T09:00:00Z",
            },
          ],
          total: 2,
          page: 0,
          pageSize: 50,
        }),
    });
    const { history } = openApp("/settings/activity-log", api.fetch);
    const user = userEvent.setup();

    expect(await screen.findByText("Анна: добавила документ «Паспорт»")).toBeVisible();
    expect(screen.getByRole("link", { name: "Паспорт" })).toHaveAttribute(
      "href",
      `/clients/${clientId}`,
    );
    expect(screen.getByText("Удалённый клиент").closest("a")).toBeNull();

    await user.selectOptions(screen.getByLabelText("Сотрудник"), actorId);

    expect(history.location.pathname).toBe("/settings/activity-log");
    expect(api.to("audit/log").at(-1)?.query.get("userId")).toBe(actorId);
  });
});
