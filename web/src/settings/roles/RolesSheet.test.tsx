import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CreateRoleRequestSchema, UpdateRoleRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const coach = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000301",
  name: "Тренер",
  permissions: ["CAN_VIEW_CLIENT_BALANCE"],
};

/** Сервер с ролями [initial] в памяти; [failSave] — сохранение роли отклоняется. */
function rolesServer({
  initial = [coach],
  failSave = false,
}: { initial?: readonly unknown[]; failSave?: boolean } = {}) {
  let roles = initial;
  const conflict = () => json({ code: "ROLE_EXISTS", message: "Роль уже есть" }, 409);
  return appServer({
    "employees/roles": () => json({ roles }),
    "employees/roles/create": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      const role = CreateRoleRequestSchema.parse(body);
      roles = [...roles, role];
      return json(role);
    },
    "employees/roles/update": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      const role = UpdateRoleRequestSchema.parse(body);
      roles = [role];
      return json(role);
    },
  });
}

/** Панель с заголовком [name]. */
const sheet = (name: string) => within(screen.getByRole("dialog", { name }));

describe("панель ролей", () => {
  it("показывает права ярлыками", async () => {
    openApp("/settings?panel=roles", rolesServer().fetch);

    const card = await screen.findByRole("button", { name: "Изменить «Тренер»" });
    expect(card).toHaveTextContent(ru["permission.CAN_VIEW_CLIENT_BALANCE.label"]);
  });

  it("без ролей показывает пустое состояние и кнопку добавления", async () => {
    openApp("/settings?panel=roles", rolesServer({ initial: [] }).fetch);

    expect(await screen.findByText(ru["roles.empty"])).toBeVisible();
    expect(sheet(ru["roles.title"]).getByRole("button", { name: ru["roles.add"] })).toBeVisible();
  });

  it("создаёт роль с выбранными правами", async () => {
    const api = rolesServer();
    openApp("/settings?panel=roles", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.type(within(editor).getByLabelText(ru["roles.name"]), "Администратор");
    await user.click(
      within(editor).getByRole("checkbox", {
        name: new RegExp(ru["permission.CAN_MANAGE_TASKS.name"]),
      }),
    );
    await user.click(
      within(editor).getByRole("checkbox", {
        name: new RegExp(ru["permission.CAN_MANAGE_ORG_BALANCE.name"]),
      }),
    );
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("button", { name: "Изменить «Администратор»" })).toBeVisible();
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["roles.create"] })).not.toBeInTheDocument();
    });
    const [created] = api
      .to("employees/roles/create")
      .map((r) => CreateRoleRequestSchema.parse(r.body));
    expect(created?.name).toBe("Администратор");
    expect(created?.permissions).toEqual(["CAN_MANAGE_ORG_BALANCE", "CAN_MANAGE_TASKS"]);
  });

  it("изменяет права существующей роли", async () => {
    const api = rolesServer();
    openApp("/settings?panel=roles", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить «Тренер»" }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.edit"] });
    const balance = within(editor).getByRole("checkbox", {
      name: new RegExp(ru["permission.CAN_VIEW_CLIENT_BALANCE.name"]),
    });
    expect(balance).toBeChecked();
    await user.click(balance);
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByText(ru["roles.noPermissions"])).toBeVisible();
    expect(api.to("employees/roles/update").map((r) => r.body)).toEqual([
      { id: coach.id, name: "Тренер", permissions: [] },
    ]);
  });
});

describe("панель роли", () => {
  it("не отправляет роль без названия", async () => {
    const api = rolesServer();
    openApp("/settings?panel=roles", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await within(editor).findByText(ru["error.required"])).toBeVisible();
    expect(api.to("employees/roles/create")).toHaveLength(0);
  });

  it("закрытие после отметки права спрашивает подтверждение", async () => {
    openApp("/settings?panel=roles", rolesServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.click(
      within(editor).getByRole("checkbox", {
        name: new RegExp(ru["permission.CAN_MANAGE_TASKS.name"]),
      }),
    );
    await user.keyboard("{Escape}");

    expect(await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeVisible();
  });

  it("при ошибке сервера остаётся открытой с введёнными данными", async () => {
    openApp("/settings?panel=roles", rolesServer({ failSave: true }).fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.type(within(editor).getByLabelText(ru["roles.name"]), "Администратор");
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));

    expect(await within(editor).findByRole("alert")).toBeVisible();
    expect(within(editor).getByLabelText(ru["roles.name"])).toHaveValue("Администратор");
  });
});
