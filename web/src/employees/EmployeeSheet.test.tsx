import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  BlockEmployeeRequestSchema,
  CreateEmployeeRequestSchema,
  CreateRoleRequestSchema,
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
  status: "ACTIVE",
  lastSeenAt: null,
  online: false,
  joinedAt: "2024-03-01T00:00:00Z",
  roles: [],
  phoneNo: "+79990001122",
  email: "victor@example.com",
  grantedPermissions: [],
  revokedPermissions: [],
  allBranchesAccess: true,
  branchIds: [],
};

const north = { id: "0199a0b2-7c3e-7d2a-9f10-000000000004", name: "Север" };
const south = { id: "0199a0b2-7c3e-7d2a-9f10-000000000005", name: "Юг" };

/** Сотрудник с филиалом «Центр», заходивший 3 часа назад. */
const restricted = {
  ...existing,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000405",
  name: "Галина Центр",
  lastSeenAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
  allBranchesAccess: false,
  branchIds: [center.id, north.id],
};

/** Сотрудник без филиалов, которому раньше выдавали доступ, — заблокирован. */
const blocked = {
  ...existing,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000406",
  name: "Денис Заблокирован",
  status: "BLOCKED",
  allBranchesAccess: false,
  branchIds: [],
};

/** Карточка текущего пользователя сессии. */
const self = {
  ...existing,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000002",
  name: "Иван Петров",
};

const owner = {
  ...existing,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000404",
  name: "Ольга Владелец",
  isOwner: true,
  roles: [{ id: coachRole.id, name: coachRole.name }],
  grantedPermissions: ["CAN_MANAGE_TASKS"],
  revokedPermissions: ["CAN_VIEW_ALL_TASKS"],
  allBranchesAccess: false,
  branchIds: [center.id],
};

/**
 * Сервер с ролями [roles], филиалами и сотрудниками [existing] и [owner] в памяти; [failSave] — сохранение
 * сотрудника отклоняется. Созданные роли добавляются к [roles].
 */
function formServer({
  failSave = false,
  roles = [coachRole],
}: { failSave?: boolean; roles?: readonly unknown[] } = {}) {
  let orgRoles = roles;
  const detailsById = new Map<string, Record<string, unknown>>([
    [existing.id, existing],
    [owner.id, owner],
    [restricted.id, restricted],
    [blocked.id, blocked],
    [self.id, self],
  ]);
  const setStatus = (body: unknown, status: string) => {
    const { employeeId } = BlockEmployeeRequestSchema.parse(body);
    detailsById.set(employeeId, { ...detailsById.get(employeeId), status });
    return json(undefined);
  };
  const conflict = () => json({ code: "EMAIL_TAKEN", message: "Email уже занят" }, 409);
  return appServer({
    "employees/list": () => json({ employees: [...detailsById.values()], total: detailsById.size }),
    "employees/roles": () => json({ roles: orgRoles }),
    "employees/roles/create": ({ body }) => {
      const role = CreateRoleRequestSchema.parse(body);
      orgRoles = [...orgRoles, role];
      return json(role);
    },
    "branches/list": () => json({ branches: [center, north, south] }),
    "employees/block": ({ body }) => setStatus(body, "BLOCKED"),
    "employees/unblock": ({ body }) => setStatus(body, "ACTIVE"),
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
        status: "NO_ACCESS",
        lastSeenAt: null,
        online: false,
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
        ...detailsById.get(request.id),
        name: request.name,
        phoneNo: request.phoneNo ?? null,
        email: request.email,
        allBranchesAccess: request.allBranchesAccess,
        branchIds: request.branchIds,
      });
      return json(undefined);
    },
  });
}

/** Панель с заголовком [name]. */
const sheet = (name: string) => within(screen.getByRole("dialog", { name }));

/** Открывает вложенную панель секции [section] формы [form] кнопкой «Изменить». */
async function editSection(
  user: UserEvent,
  section: string,
  form: string = ru["employees.create"],
) {
  await user.click(
    sheet(form).getByRole("button", {
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

describe("создание роли из списка ролей сотрудника", () => {
  /** Открывает список ролей из формы [form]. */
  const openRoles = async (user: ReturnType<typeof userEvent.setup>, form: HTMLElement) => {
    await user.click(
      within(form).getByRole("button", {
        name: `${ru["employees.editSection"]}: ${ru["employees.columnRoles"]}`,
      }),
    );
    return within(await screen.findByRole("dialog", { name: ru["employees.columnRoles"] }));
  };

  it("без ролей создаёт роль, отмечает её в списке и не создаёт сотрудника", async () => {
    const api = formServer({ roles: [] });
    openApp("/employees?create=true", api.fetch);
    const user = userEvent.setup();

    const form = await screen.findByRole("dialog", { name: ru["employees.create"] });
    await user.type(await within(form).findByLabelText(ru["employees.name"]), "Мария Кузнецова");
    expect(within(form).getByText(ru["employees.rolesEmpty"])).toBeVisible();
    expect(within(form).queryByRole("button", { name: ru["roles.add"] })).toBeNull();

    const roles = await openRoles(user, form);
    await user.click(roles.getByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.type(within(editor).getByLabelText(ru["roles.name"]), "Тренер");
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));
    await sheetClosed(ru["roles.create"]);

    expect(await roles.findByRole("checkbox", { name: "Тренер" })).toBeChecked();
    await user.click(roles.getByRole("button", { name: ru["action.done"] }));
    await sheetClosed(ru["employees.columnRoles"]);

    expect(await within(form).findByText("Тренер")).toBeVisible();
    expect(within(form).getByLabelText(ru["employees.name"])).toHaveValue("Мария Кузнецова");
    expect(api.to("employees/roles/create")).toHaveLength(1);
    expect(api.to("employees/create")).toHaveLength(0);
  });

  it("при редактировании добавляет новую роль к уже отмеченным", async () => {
    const api = formServer();
    openApp(`/employees?employee=${existing.id}&edit=true`, api.fetch);
    const user = userEvent.setup();

    const form = await screen.findByRole("dialog", { name: ru["employees.edit"] });
    await within(form).findByLabelText(ru["employees.name"]);
    const roles = await openRoles(user, form);
    await user.click(roles.getByRole("checkbox", { name: coachRole.name }));

    await user.click(roles.getByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.type(within(editor).getByLabelText(ru["roles.name"]), "Кассир");
    await user.click(within(editor).getByRole("button", { name: ru["action.save"] }));
    await sheetClosed(ru["roles.create"]);
    expect(await roles.findByRole("checkbox", { name: "Кассир" })).toBeChecked();
    expect(roles.getByRole("checkbox", { name: coachRole.name })).toBeChecked();
    await user.click(roles.getByRole("button", { name: ru["action.done"] }));
    await sheetClosed(ru["employees.columnRoles"]);

    await user.click(within(form).getByRole("button", { name: ru["action.save"] }));
    await sheetClosed(ru["employees.edit"]);
    const [role] = api
      .to("employees/roles/create")
      .map((r) => CreateRoleRequestSchema.parse(r.body));
    const [updated] = api
      .to("employees/update")
      .map((r) => UpdateEmployeeRequestSchema.parse(r.body));
    expect(updated?.roleIds).toEqual([coachRole.id, role?.id]);
  });

  it("подставляет в название роли строку поиска, по которой ничего не нашлось", async () => {
    const api = formServer();
    openApp("/employees?create=true", api.fetch);
    const user = userEvent.setup();

    const form = await screen.findByRole("dialog", { name: ru["employees.create"] });
    await within(form).findByLabelText(ru["employees.name"]);
    const roles = await openRoles(user, form);
    await user.type(roles.getByLabelText(ru["employees.searchRoles"]), "Бухгалтер");
    await user.click(
      roles.getByRole("button", {
        name: ru["roles.addNamed"].replace("{name}", "Бухгалтер"),
      }),
    );

    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    expect(within(editor).getByLabelText(ru["roles.name"])).toHaveValue("Бухгалтер");
  });

  it("отмена панели роли оставляет список ролей открытым и ничего не создаёт", async () => {
    const api = formServer();
    openApp("/employees?create=true", api.fetch);
    const user = userEvent.setup();

    const form = await screen.findByRole("dialog", { name: ru["employees.create"] });
    await within(form).findByLabelText(ru["employees.name"]);
    const roles = await openRoles(user, form);
    await user.click(roles.getByRole("button", { name: ru["roles.add"] }));
    const editor = await screen.findByRole("dialog", { name: ru["roles.create"] });
    await user.click(within(editor).getByRole("button", { name: ru["action.cancel"] }));
    await sheetClosed(ru["roles.create"]);

    expect(roles.getByRole("checkbox", { name: coachRole.name })).not.toBeChecked();
    expect(api.to("employees/roles/create")).toHaveLength(0);
    await user.click(roles.getByRole("button", { name: ru["action.done"] }));
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

describe("панель редактирования владельца", () => {
  it("вместо ролей, прав и филиалов показывает блок полного доступа", async () => {
    openApp(`/employees?employee=${owner.id}&edit=true`, formServer().fetch);

    const form = await screen.findByRole("dialog", { name: ru["employees.edit"] });
    expect(await within(form).findByLabelText(ru["employees.name"])).toHaveValue(owner.name);
    expect(within(form).getByText(ru["employees.ownerAccessTitle"])).toBeVisible();
    expect(within(form).getByText(ru["employees.ownerAccessHint"])).toBeVisible();
    expect(within(form).queryByText(ru["employees.columnRoles"])).toBeNull();
    expect(within(form).queryByText(ru["employees.sectionPermissions"])).toBeNull();
    expect(within(form).queryByText(ru["employees.sectionBranchAccess"])).toBeNull();
    expect(
      within(form).queryByRole("button", { name: new RegExp(ru["employees.editSection"]) }),
    ).toBeNull();
    expect(within(form).queryByRole("button", { name: ru["roles.add"] })).toBeNull();
  });

  it("сохраняет контакты, не меняя роли, права и филиалы", async () => {
    const api = formServer();
    openApp(`/employees?employee=${owner.id}&edit=true`, api.fetch);
    const user = userEvent.setup();

    const phone = await screen.findByLabelText(ru["employees.phone"]);
    await user.clear(phone);
    await user.type(phone, "+79995554433");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await sheetClosed(ru["employees.edit"]);
    const [updated] = api
      .to("employees/update")
      .map((r) => UpdateEmployeeRequestSchema.parse(r.body));
    expect(updated).toMatchObject({
      id: owner.id,
      phoneNo: "+79995554433",
      roleIds: [coachRole.id],
      grantedPermissions: owner.grantedPermissions,
      revokedPermissions: owner.revokedPermissions,
      allBranchesAccess: false,
      branchIds: [center.id],
    });
  });

  it("у обычного сотрудника блока полного доступа нет, секции на месте", async () => {
    openApp(`/employees?employee=${existing.id}&edit=true`, formServer().fetch);

    const form = await screen.findByRole("dialog", { name: ru["employees.edit"] });
    await within(form).findByLabelText(ru["employees.name"]);
    expect(within(form).queryByText(ru["employees.ownerAccessTitle"])).toBeNull();
    expect(within(form).getByText(ru["employees.columnRoles"])).toBeVisible();
    expect(within(form).getByText(ru["employees.sectionPermissions"])).toBeVisible();
    expect(within(form).getByText(ru["employees.sectionBranchAccess"])).toBeVisible();
  });
});

/** Секция «Доступ к филиалам» карточки сотрудника [name]. */
async function branchSection(name: string) {
  const card = await screen.findByRole("dialog", { name });
  const heading = await within(card).findByRole("heading", {
    name: ru["employees.sectionBranchAccess"],
  });
  const section = heading.closest("section");
  if (section === null) {
    throw new Error("секция филиалов не найдена");
  }
  return within(section);
}

describe("доступ к филиалам при редактировании", () => {
  it("ограничивает сотрудника со всеми филиалами одним филиалом", async () => {
    const api = formServer();
    openApp(`/employees?employee=${existing.id}&edit=true`, api.fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    await editSection(user, ru["employees.sectionBranchAccess"], ru["employees.edit"]);
    const picker = sheet(ru["employees.sectionBranchAccess"]);
    [center, north, south].forEach((branch) => {
      expect(picker.getByRole("checkbox", { name: branch.name })).toBeChecked();
    });
    await user.click(picker.getByRole("checkbox", { name: north.name }));
    await user.click(picker.getByRole("checkbox", { name: south.name }));
    await user.click(picker.getByRole("button", { name: ru["action.done"] }));
    await sheetClosed(ru["employees.sectionBranchAccess"]);
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await sheetClosed(ru["employees.edit"]);
    const [updated] = api
      .to("employees/update")
      .map((r) => UpdateEmployeeRequestSchema.parse(r.body));
    expect(updated).toMatchObject({ allBranchesAccess: false, branchIds: [center.id] });
    const section = await branchSection(existing.name);
    expect(await section.findByText(center.name)).toBeVisible();
    expect(section.queryByText(north.name)).toBeNull();
  });

  it("меняет выбранный филиал на другой", async () => {
    const api = formServer();
    openApp(`/employees?employee=${restricted.id}&edit=true`, api.fetch);
    const user = userEvent.setup();

    await screen.findByLabelText(ru["employees.name"]);
    await editSection(user, ru["employees.sectionBranchAccess"], ru["employees.edit"]);
    const picker = sheet(ru["employees.sectionBranchAccess"]);
    await user.click(picker.getByRole("checkbox", { name: center.name }));
    await user.click(picker.getByRole("button", { name: ru["action.done"] }));
    await sheetClosed(ru["employees.sectionBranchAccess"]);
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await sheetClosed(ru["employees.edit"]);
    const [updated] = api
      .to("employees/update")
      .map((r) => UpdateEmployeeRequestSchema.parse(r.body));
    expect(updated).toMatchObject({ allBranchesAccess: false, branchIds: [north.id] });
  });
});

describe("карточка сотрудника: доступ, статус и блокировка", () => {
  it("показывает выбранные филиалы", async () => {
    openApp(`/employees?employee=${restricted.id}`, formServer().fetch);

    const section = await branchSection(restricted.name);
    expect(await section.findByText(center.name)).toBeVisible();
    expect(section.getByText(north.name)).toBeVisible();
  });

  it("показывает «Все филиалы» при доступе ко всем", async () => {
    openApp(`/employees?employee=${existing.id}`, formServer().fetch);

    expect(
      (await branchSection(existing.name)).getByText(ru["employees.allBranches"]),
    ).toBeVisible();
  });

  it("сообщает об отсутствии доступа к филиалам", async () => {
    openApp(`/employees?employee=${blocked.id}`, formServer().fetch);

    expect(
      (await branchSection(blocked.name)).getByText(ru["employees.noBranchAccess"]),
    ).toBeVisible();
  });

  it("показывает время последней активности", async () => {
    openApp(`/employees?employee=${restricted.id}`, formServer().fetch);

    const card = await screen.findByRole("dialog", { name: restricted.name });
    expect(
      within(card).getByText(ru["employees.lastSeen"].replace("{ago}", "3 часа назад")),
    ).toBeVisible();
  });

  it("блокирует сотрудника после подтверждения", async () => {
    const api = formServer();
    openApp(`/employees?employee=${existing.id}`, api.fetch);
    const user = userEvent.setup();

    const card = await screen.findByRole("dialog", { name: existing.name });
    await user.click(within(card).getByRole("button", { name: ru["employees.block"] }));
    const confirm = await screen.findByRole("dialog", { name: ru["employees.blockConfirmTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["employees.block"] }));

    expect(await within(card).findByText(ru["employees.statusBlocked"])).toBeVisible();
    expect(within(card).getByRole("button", { name: ru["employees.unblock"] })).toBeVisible();
    expect(api.to("employees/block")).toHaveLength(1);
  });

  it("отмена подтверждения не блокирует", async () => {
    const api = formServer();
    openApp(`/employees?employee=${existing.id}`, api.fetch);
    const user = userEvent.setup();

    const card = await screen.findByRole("dialog", { name: existing.name });
    await user.click(within(card).getByRole("button", { name: ru["employees.block"] }));
    const confirm = await screen.findByRole("dialog", { name: ru["employees.blockConfirmTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["action.cancel"] }));

    await sheetClosed(ru["employees.blockConfirmTitle"]);
    expect(api.to("employees/block")).toHaveLength(0);
    expect(within(card).getByText(ru["employees.statusActive"])).toBeVisible();
  });

  it("разблокирует сразу, без «Отправить доступ» у заблокированного", async () => {
    const api = formServer();
    openApp(`/employees?employee=${blocked.id}`, api.fetch);
    const user = userEvent.setup();

    const card = await screen.findByRole("dialog", { name: blocked.name });
    expect(within(card).queryByRole("button", { name: ru["employees.sendAccess"] })).toBeNull();
    await user.click(within(card).getByRole("button", { name: ru["employees.unblock"] }));

    expect(await within(card).findByText(ru["employees.statusActive"])).toBeVisible();
    expect(api.to("employees/unblock")).toHaveLength(1);
  });

  it("у владельца и в своей карточке кнопок блокировки нет", async () => {
    openApp(`/employees?employee=${owner.id}`, formServer().fetch);
    const ownerCard = await screen.findByRole("dialog", { name: owner.name });
    expect(within(ownerCard).queryByRole("button", { name: ru["employees.block"] })).toBeNull();

    cleanup();
    openApp(`/employees?employee=${self.id}`, formServer().fetch);
    const selfCard = await screen.findByRole("dialog", { name: self.name });
    expect(within(selfCard).getByRole("button", { name: ru["action.edit"] })).toBeVisible();
    expect(within(selfCard).queryByRole("button", { name: ru["employees.block"] })).toBeNull();
    expect(within(selfCard).queryByRole("button", { name: ru["employees.unblock"] })).toBeNull();
  });
});
