import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  CreateEmployeeRequestSchema,
  UpdateEmployeeRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, json, openApp } from "@/test/app";

/**
 * Чекбокс права [permissionName] в секции с заголовком [sectionTitle]: одно и то же право
 * показано и в выданных, и в отозванных правах, поэтому чекбокс ищется внутри своей секции.
 */
function permissionCheckbox(sectionTitle: string, permissionName: string): HTMLElement {
  const fieldset = screen.getByText(sectionTitle).closest("fieldset");
  if (fieldset === null) {
    throw new Error(`секция «${sectionTitle}» не найдена`);
  }
  return within(fieldset).getByRole("checkbox", { name: new RegExp(permissionName) });
}

const coachRole = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000301",
  name: "Тренер",
  permissions: [],
};

const existing = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000403",
  name: "Виктор Сидоров",
  avatarId: null,
  isOwner: false,
  isActive: true,
  joinedAt: "2024-03-01T00:00:00Z",
  roles: [],
  phoneNo: "+79990001122",
  email: "victor@example.com",
  grantedPermissions: [],
  revokedPermissions: [],
  allBranchesAccess: true,
  branchIds: [],
};

/** Сервер с ролями, филиалами и сотрудником [existing] в памяти. */
function formServer() {
  const detailsById = new Map<string, Record<string, unknown>>([[existing.id, existing]]);
  return appServer({
    "employees/list": () => json({ employees: [...detailsById.values()], total: detailsById.size }),
    "employees/roles": () => json({ roles: [coachRole] }),
    "branches/list": () => json({ branches: [center] }),
    "employees/detail": ({ query }) => {
      const id = query.get("id") ?? "";
      const detail = detailsById.get(id);
      return detail === undefined ? json({ code: "NOT_FOUND", message: "" }, 404) : json(detail);
    },
    "employees/create": ({ body }) => {
      const request = CreateEmployeeRequestSchema.parse(body);
      const detail = {
        id: request.id,
        name: request.name,
        avatarId: null,
        isOwner: false,
        isActive: false,
        joinedAt: "2024-03-02T00:00:00Z",
        roles: (request.roleIds ?? []).map((id) => ({ id, name: coachRole.name })),
        phoneNo: request.phoneNo ?? null,
        email: request.email,
        grantedPermissions: request.grantedPermissions ?? [],
        revokedPermissions: request.revokedPermissions ?? [],
        allBranchesAccess: request.allBranchesAccess ?? true,
        branchIds: request.branchIds ?? [],
      };
      detailsById.set(request.id, detail);
      return json(detail);
    },
    "employees/update": ({ body }) => {
      const request = UpdateEmployeeRequestSchema.parse(body);
      detailsById.set(request.id, {
        ...existing,
        id: request.id,
        name: request.name,
        phoneNo: request.phoneNo ?? null,
        email: request.email,
        grantedPermissions: request.grantedPermissions ?? [],
        revokedPermissions: request.revokedPermissions ?? [],
        allBranchesAccess: request.allBranchesAccess ?? true,
        branchIds: request.branchIds ?? [],
        roles: (request.roleIds ?? []).map((id) => ({ id, name: coachRole.name })),
      });
      return json(undefined);
    },
  });
}

describe("создание и редактирование сотрудника", () => {
  it("создаёт сотрудника с ролью и выданным правом", async () => {
    const api = formServer();
    openApp("/employees/new", api.fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(ru["employees.name"]), "Мария Кузнецова");
    await user.type(screen.getByLabelText(ru["employees.email"]), "maria@example.com");
    await user.click(screen.getByRole("checkbox", { name: coachRole.name }));
    await user.click(
      permissionCheckbox(
        ru["employees.sectionGrantedPermissions"],
        ru["permission.CAN_MANAGE_TASKS.name"],
      ),
    );
    await user.click(screen.getByRole("button", { name: ru["employees.submitCreate"] }));

    expect(await screen.findByRole("heading", { name: "Мария Кузнецова" })).toBeVisible();
    const [created] = api
      .to("employees/create")
      .map((r) => CreateEmployeeRequestSchema.parse(r.body));
    expect(created).toMatchObject({
      name: "Мария Кузнецова",
      email: "maria@example.com",
      roleIds: [coachRole.id],
      grantedPermissions: ["CAN_MANAGE_TASKS"],
    });
  });

  it("не отправляет форму без обязательных полей", async () => {
    const api = formServer();
    openApp("/employees/new", api.fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    await user.click(screen.getByRole("button", { name: ru["employees.submitCreate"] }));

    expect(await screen.findAllByText(ru["error.required"])).not.toHaveLength(0);
    expect(api.to("employees/create")).toHaveLength(0);
  });

  it("редактирует сотрудника с предзаполненной формой", async () => {
    const api = formServer();
    openApp(`/employees/${existing.id}/edit`, api.fetch);
    const user = userEvent.setup();

    expect(await screen.findByLabelText(ru["employees.name"])).toHaveValue(existing.name);
    expect(screen.getByLabelText(ru["employees.email"])).toHaveValue(existing.email);
    await user.click(screen.getByRole("checkbox", { name: coachRole.name }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await screen.findByRole("heading", { name: existing.name });
    const [updated] = api
      .to("employees/update")
      .map((r) => UpdateEmployeeRequestSchema.parse(r.body));
    expect(updated).toMatchObject({ id: existing.id, roleIds: [coachRole.id] });
  });

  it("роли и права взаимоисключающие: выдача права снимает его отзыв", async () => {
    openApp("/employees/new", formServer().fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    const revokedCheckbox = permissionCheckbox(
      ru["employees.sectionRevokedPermissions"],
      ru["permission.CAN_MANAGE_TASKS.name"],
    );
    await user.click(revokedCheckbox);
    expect(revokedCheckbox).toBeChecked();

    const grantedCheckbox = permissionCheckbox(
      ru["employees.sectionGrantedPermissions"],
      ru["permission.CAN_MANAGE_TASKS.name"],
    );
    await user.click(grantedCheckbox);

    expect(grantedCheckbox).toBeChecked();
    expect(revokedCheckbox).not.toBeChecked();
  });
});
