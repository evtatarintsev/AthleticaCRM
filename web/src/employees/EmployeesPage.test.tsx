import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SendEmployeeAccessRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, empty, json, openApp } from "@/test/app";

/** Первый элемент [items] или ошибка, если список пуст — для строгого `noUncheckedIndexedAccess`. */
function first<T>(items: readonly T[]): T {
  const [item] = items;
  if (item === undefined) {
    throw new Error("список пуст");
  }
  return item;
}

const anna = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000401",
  name: "Анна Иванова",
  avatarId: null,
  isOwner: true,
  isActive: true,
  joinedAt: "2024-01-01T00:00:00Z",
  roles: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000301", name: "Тренер" }],
  phoneNo: "+79990000000",
  email: "anna@example.com",
};
const boris = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000402",
  name: "Борис Петров",
  avatarId: null,
  isOwner: false,
  isActive: false,
  joinedAt: "2024-02-01T00:00:00Z",
  roles: [],
  phoneNo: null,
  email: "boris@example.com",
};

/** Детали сотрудника [employee] для ответа `employees/detail`. */
function detailOf(employee: typeof anna | typeof boris) {
  return {
    ...employee,
    grantedPermissions: [],
    revokedPermissions: [],
    allBranchesAccess: true,
    branchIds: [],
  };
}

/** Сервер со списком сотрудников, ролями и филиалами в памяти. */
function employeesServer() {
  return appServer({
    "employees/list": () => json({ employees: [anna, boris], total: 2 }),
    "employees/roles": () => json({ roles: [] }),
    "branches/list": () => json({ branches: [center] }),
    "employees/detail": ({ query }) => {
      const id = query.get("id");
      return json(detailOf(id === boris.id ? boris : anna));
    },
    "employees/send-access": () => empty(),
  });
}

describe("список сотрудников", () => {
  // Список рендерит таблицу для широких экранов и карточки для узких одновременно (переключение
  // между ними — только классами Tailwind), поэтому в jsdom каждая запись присутствует в DOM дважды;
  // тексты и кнопки строк ищутся через `getAllBy…`, как и в `app/router.test.tsx` для навигации.
  it("показывает сотрудников с ролями, статусом и контактом", async () => {
    openApp("/employees", employeesServer().fetch);

    expect((await screen.findAllByText(anna.name))[0]).toBeVisible();
    expect(screen.getAllByText(boris.name)[0]).toBeVisible();
    expect(screen.getAllByText(ru["employees.statusActive"]).length).toBeGreaterThan(0);
    expect(screen.getAllByText(ru["employees.statusInactive"]).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Тренер").length).toBeGreaterThan(0);
  });

  it("фильтрует по тексту поиска", async () => {
    openApp("/employees", employeesServer().fetch);
    const user = userEvent.setup();

    await screen.findAllByText(anna.name);
    await user.type(screen.getByPlaceholderText(ru["employees.searchPlaceholder"]), "Борис");

    expect(screen.queryAllByText(anna.name)).toHaveLength(0);
    expect(screen.getAllByText(boris.name)[0]).toBeVisible();
  });

  it("быстрый фильтр «только активные» скрывает неактивных", async () => {
    openApp("/employees", employeesServer().fetch);
    const user = userEvent.setup();

    await screen.findAllByText(boris.name);
    await user.click(screen.getByRole("button", { name: ru["employees.filterOnlyActive"] }));

    expect(screen.queryAllByText(boris.name)).toHaveLength(0);
    expect(screen.getAllByText(anna.name)[0]).toBeVisible();
  });

  it("отправляет доступ неактивному сотруднику прямо из списка", async () => {
    const api = employeesServer();
    openApp("/employees", api.fetch);
    const user = userEvent.setup();

    await screen.findAllByText(boris.name);
    const sendAccessButton = first(
      screen.getAllByRole("button", {
        name: ru["employees.sendAccessFor"].replace("{name}", boris.name),
      }),
    );
    await user.click(sendAccessButton);
    const dialog = await screen.findByRole("dialog", { name: ru["employees.sendAccess"] });
    expect(within(dialog).getByLabelText(ru["employees.sendAccessEmail"])).toHaveValue(boris.email);
    await user.type(within(dialog).getByLabelText(ru["employees.sendAccessPassword"]), "secret123");
    await user.click(within(dialog).getByRole("button", { name: ru["employees.sendAccess"] }));

    await screen.findAllByText(boris.name);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const [sent] = api
      .to("employees/send-access")
      .map((r) => SendEmployeeAccessRequestSchema.parse(r.body));
    expect(sent).toMatchObject({ employeeId: boris.id, email: boris.email, password: "secret123" });
  });

  it("переход к карточке сотрудника показывает контакты и роли", async () => {
    openApp("/employees", employeesServer().fetch);
    const user = userEvent.setup();

    const nameLink = first(await screen.findAllByText(anna.name));
    await user.click(nameLink);

    expect(await screen.findByRole("heading", { name: anna.name })).toBeVisible();
    expect(screen.getByText(anna.email)).toBeVisible();
    expect(screen.getByText(anna.phoneNo)).toBeVisible();
    expect(screen.getByText("Тренер")).toBeVisible();
    expect(screen.getByText(ru["employees.owner"])).toBeVisible();
    expect(
      screen.queryByRole("button", { name: ru["employees.sendAccess"] }),
    ).not.toBeInTheDocument();
  });
});
