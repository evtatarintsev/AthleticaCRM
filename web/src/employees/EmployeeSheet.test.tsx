import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  CreateEmployeeRequestSchema,
  UpdateEmployeeRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, json, openApp } from "@/test/app";

const coachRole = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000301",
  name: "Тренер",
  permissions: ["CAN_VIEW_CLIENT_BALANCE"],
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

/** Сервер с ролями, филиалами и сотрудником [existing] в памяти; [failSave] — сохранение отклоняется. */
function formServer({ failSave = false }: { failSave?: boolean } = {}) {
  const detailsById = new Map<string, Record<string, unknown>>([[existing.id, existing]]);
  const conflict = () => json({ code: "EMAIL_TAKEN", message: "Email уже занят" }, 409);
  return appServer({
    "employees/list": () => json({ employees: [...detailsById.values()], total: detailsById.size }),
    "employees/roles": () => json({ roles: [coachRole] }),
    "branches/list": () => json({ branches: [center] }),
    "employees/detail": ({ query }) => {
      const detail = detailsById.get(query.get("id") ?? "");
      return detail === undefined ? json({ code: "NOT_FOUND", message: "" }, 404) : json(detail);
    },
    "employees/create": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      const request = CreateEmployeeRequestSchema.parse(body);
      const detail = {
        ...existing,
        id: request.id,
        name: request.name,
        isActive: false,
        phoneNo: request.phoneNo ?? null,
        email: request.email,
      };
      detailsById.set(request.id, detail);
      return json(detail);
    },
    "employees/update": ({ body }) => {
      if (failSave) {
        return conflict();
      }
      const request = UpdateEmployeeRequestSchema.parse(body);
      detailsById.set(request.id, {
        ...existing,
        name: request.name,
        phoneNo: request.phoneNo ?? null,
        email: request.email,
      });
      return json(undefined);
    },
  });
}

/** Панель с заголовком [name]. */
const sheet = (name: string) => within(screen.getByRole("dialog", { name }));

/** Открывает вложенную панель секции [section] кнопкой «Изменить». */
async function editSection(user: UserEvent, section: string) {
  await user.click(
    sheet(ru["employees.create"]).getByRole("button", {
      name: `${ru["employees.editSection"]}: ${section}`,
    }),
  );
}

/** Ждёт, пока панель с заголовком [name] закроется. */
const sheetClosed = (name: string) =>
  waitFor(() => {
    expect(screen.queryByRole("dialog", { name })).not.toBeInTheDocument();
  });

describe("панель создания сотрудника", () => {
  it("«Добавить сотрудника» открывает панель поверх списка, закрытие убирает её из адреса", async () => {
    const { history } = openApp("/employees", formServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("link", { name: ru["employees.add"] }));

    expect(await screen.findByRole("dialog", { name: ru["employees.create"] })).toBeVisible();
    expect(history.location.search).toContain("create=true");
    await user.keyboard("{Escape}");
    await sheetClosed(ru["employees.create"]);
    expect(history.location.search).not.toContain("create");
  });

  it("создаёт сотрудника с ролью, правом и филиалом из вложенных панелей", async () => {
    const api = formServer();
    openApp("/employees?create=true", api.fetch);
    const user = userEvent.setup();

    const form = await screen.findByRole("dialog", { name: ru["employees.create"] });
    await user.type(await within(form).findByLabelText(ru["employees.name"]), "Мария Кузнецова");
    await user.type(within(form).getByLabelText(ru["employees.email"]), "maria@example.com");

    await editSection(user, ru["employees.columnRoles"]);
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("checkbox", { name: coachRole.name }),
    );
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("button", { name: ru["action.done"] }),
    );
    await sheetClosed(ru["employees.columnRoles"]);
    expect(within(form).getByText(coachRole.name)).toBeVisible();

    await editSection(user, ru["employees.sectionPermissions"]);
    const permissions = sheet(ru["employees.sectionPermissions"]);
    const manageTasks = permissions.getByRole("radiogroup", {
      name: ru["permission.CAN_MANAGE_TASKS.name"],
    });
    await user.click(
      within(manageTasks).getByRole("radio", { name: ru["employees.permissionGrant"] }),
    );
    await user.click(permissions.getByRole("button", { name: ru["action.done"] }));
    await sheetClosed(ru["employees.sectionPermissions"]);
    expect(
      within(form).getByText(
        ru["employees.permissionsSummary"].replace("{granted}", "1").replace("{revoked}", "0"),
      ),
    ).toBeVisible();

    await editSection(user, ru["employees.sectionBranchAccess"]);
    await user.click(
      sheet(ru["employees.sectionBranchAccess"]).getByRole("checkbox", { name: center.name }),
    );
    await user.click(
      sheet(ru["employees.sectionBranchAccess"]).getByRole("button", { name: ru["action.done"] }),
    );
    await sheetClosed(ru["employees.sectionBranchAccess"]);

    await user.click(within(form).getByRole("button", { name: ru["action.create"] }));

    await sheetClosed(ru["employees.create"]);
    expect(await screen.findAllByText("Мария Кузнецова")).not.toHaveLength(0);
    const [created] = api
      .to("employees/create")
      .map((r) => CreateEmployeeRequestSchema.parse(r.body));
    expect(created).toMatchObject({
      name: "Мария Кузнецова",
      email: "maria@example.com",
      roleIds: [coachRole.id],
      grantedPermissions: ["CAN_MANAGE_TASKS"],
      revokedPermissions: [],
      allBranchesAccess: false,
      branchIds: [center.id],
    });
  });

  it("помечает в панели прав права выбранной в форме роли", async () => {
    openApp("/employees?create=true", formServer().fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    await editSection(user, ru["employees.columnRoles"]);
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("checkbox", { name: coachRole.name }),
    );
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("button", { name: ru["action.done"] }),
    );
    await sheetClosed(ru["employees.columnRoles"]);
    await editSection(user, ru["employees.sectionPermissions"]);

    const row = sheet(ru["employees.sectionPermissions"])
      .getByRole("radiogroup", { name: ru["permission.CAN_VIEW_CLIENT_BALANCE.name"] })
      .closest("li");
    expect(row).toHaveTextContent(ru["employees.permissionFromRole"]);
  });

  it("не отправляет форму без email", async () => {
    const api = formServer();
    openApp("/employees?create=true", api.fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(ru["employees.name"]), "Мария Кузнецова");
    await user.click(screen.getByRole("button", { name: ru["action.create"] }));

    expect(await screen.findByText(ru["error.invalidEmail"])).toBeVisible();
    expect(api.to("employees/create")).toHaveLength(0);
    expect(screen.getByRole("dialog", { name: ru["employees.create"] })).toBeVisible();
  });

  it("при ошибке сервера остаётся открытой с введёнными данными", async () => {
    openApp("/employees?create=true", formServer({ failSave: true }).fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(ru["employees.name"]), "Мария Кузнецова");
    await user.type(screen.getByLabelText(ru["employees.email"]), "maria@example.com");
    await user.click(screen.getByRole("button", { name: ru["action.create"] }));

    expect(await screen.findByRole("alert")).toBeVisible();
    expect(screen.getByLabelText(ru["employees.name"])).toHaveValue("Мария Кузнецова");
  });

  it("закрытие после выбора роли спрашивает подтверждение", async () => {
    openApp("/employees?create=true", formServer().fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    await editSection(user, ru["employees.columnRoles"]);
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("checkbox", { name: coachRole.name }),
    );
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("button", { name: ru["action.done"] }),
    );
    await sheetClosed(ru["employees.columnRoles"]);
    await user.keyboard("{Escape}");

    expect(await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeVisible();
  });

  it("закрытие вложенной панели без «Готово» не меняет роли", async () => {
    openApp("/employees?create=true", formServer().fetch);
    const user = userEvent.setup();

    const form = await screen.findByRole("dialog", { name: ru["employees.create"] });
    await within(form).findByLabelText(ru["employees.name"]);
    await editSection(user, ru["employees.columnRoles"]);
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("checkbox", { name: coachRole.name }),
    );
    await user.click(
      sheet(ru["employees.columnRoles"]).getByRole("button", { name: ru["action.cancel"] }),
    );
    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await sheetClosed(ru["employees.columnRoles"]);

    expect(within(form).getByText(ru["employees.rolesNone"])).toBeVisible();
  });
});

describe("панель карточки сотрудника", () => {
  it("клик по сотруднику открывает карточку поверх списка, закрытие убирает её из адреса", async () => {
    const { history } = openApp("/employees", formServer().fetch);
    const user = userEvent.setup();

    const [row] = await screen.findAllByRole("link", { name: new RegExp(existing.name) });
    if (row === undefined) {
      throw new Error("строка сотрудника не найдена");
    }
    await user.click(row);

    const card = await screen.findByRole("dialog", { name: existing.name });
    expect(history.location.search).toContain(`employee=${existing.id}`);
    expect(within(card).getByText(existing.email)).toBeVisible();
    expect(
      screen.getByRole("heading", { name: ru["employees.title"], hidden: true }),
    ).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await sheetClosed(existing.name);
    expect(history.location.search).not.toContain("employee");
  });

  it("«Редактировать» открывает поверх карточки панель с текущими данными", async () => {
    const { history } = openApp(`/employees?employee=${existing.id}`, formServer().fetch);
    const user = userEvent.setup();

    const card = await screen.findByRole("dialog", { name: existing.name });
    await user.click(within(card).getByRole("button", { name: ru["action.edit"] }));

    const form = await screen.findByRole("dialog", { name: ru["employees.edit"] });
    expect(history.location.search).toContain("edit=true");
    expect(history.location.search).toContain(`employee=${existing.id}`);
    expect(await within(form).findByLabelText(ru["employees.name"])).toHaveValue(existing.name);
    expect(within(form).getByLabelText(ru["employees.email"])).toHaveValue(existing.email);
    expect(within(form).getByText(ru["employees.allBranchesAccess"])).toBeVisible();
  });

  it("Esc в панели редактирования без изменений закрывает только её", async () => {
    const { history } = openApp(`/employees?employee=${existing.id}&edit=true`, formServer().fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    await user.keyboard("{Escape}");

    await sheetClosed(ru["employees.edit"]);
    expect(screen.getByRole("dialog", { name: existing.name })).toBeVisible();
    expect(history.location.search).not.toContain("edit");
  });
});

describe("панель редактирования сотрудника", () => {
  it("сохраняет изменения, не трогая доступ ко всем филиалам", async () => {
    const api = formServer();
    const { history } = openApp(`/employees?employee=${existing.id}&edit=true`, api.fetch);
    const user = userEvent.setup();

    const phone = await screen.findByLabelText(ru["employees.phone"]);
    await user.clear(phone);
    await user.type(phone, "+79995554433");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await sheetClosed(ru["employees.edit"]);
    const card = screen.getByRole("dialog", { name: existing.name });
    expect(await within(card).findByText("+79995554433")).toBeVisible();
    expect(history.location.search).not.toContain("edit");
    const [updated] = api
      .to("employees/update")
      .map((r) => UpdateEmployeeRequestSchema.parse(r.body));
    expect(updated).toMatchObject({
      id: existing.id,
      phoneNo: "+79995554433",
      allBranchesAccess: true,
    });
  });
});
