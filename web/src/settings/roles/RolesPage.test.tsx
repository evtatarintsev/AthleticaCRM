import { screen, within } from "@testing-library/react";
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

/** Сервер с ролями в памяти. */
function rolesServer() {
  let roles: readonly unknown[] = [coach];
  return appServer({
    "employees/roles": () => json({ roles }),
    "employees/roles/create": ({ body }) => {
      const role = CreateRoleRequestSchema.parse(body);
      roles = [...roles, role];
      return json(role);
    },
    "employees/roles/update": ({ body }) => {
      const role = UpdateRoleRequestSchema.parse(body);
      roles = [role];
      return json(role);
    },
  });
}

describe("роли", () => {
  it("показывают права ярлыками", async () => {
    openApp("/settings/roles", rolesServer().fetch);

    const card = await screen.findByRole("button", { name: "Изменить «Тренер»" });
    expect(card).toHaveTextContent(ru["permission.CAN_VIEW_CLIENT_BALANCE.label"]);
  });

  it("создаёт роль с выбранными правами", async () => {
    const api = rolesServer();
    openApp("/settings/roles", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["roles.add"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.type(within(dialog).getByLabelText(ru["roles.name"]), "Администратор");
    await user.click(
      within(dialog).getByRole("checkbox", {
        name: new RegExp(ru["permission.CAN_MANAGE_TASKS.name"]),
      }),
    );
    await user.click(
      within(dialog).getByRole("checkbox", {
        name: new RegExp(ru["permission.CAN_MANAGE_ORG_BALANCE.name"]),
      }),
    );
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("button", { name: "Изменить «Администратор»" })).toBeVisible();
    const [created] = api
      .to("employees/roles/create")
      .map((r) => CreateRoleRequestSchema.parse(r.body));
    expect(created?.name).toBe("Администратор");
    expect(created?.permissions).toEqual(["CAN_MANAGE_ORG_BALANCE", "CAN_MANAGE_TASKS"]);
  });

  it("изменяет права существующей роли", async () => {
    const api = rolesServer();
    openApp("/settings/roles", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить «Тренер»" }));
    const dialog = await screen.findByRole("dialog", { name: ru["roles.edit"] });
    const balance = within(dialog).getByRole("checkbox", {
      name: new RegExp(ru["permission.CAN_VIEW_CLIENT_BALANCE.name"]),
    });
    expect(balance).toBeChecked();
    await user.click(balance);
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByText(ru["roles.noPermissions"])).toBeVisible();
    expect(api.to("employees/roles/update").map((r) => r.body)).toEqual([
      { id: coach.id, name: "Тренер", permissions: [] },
    ]);
  });
});
