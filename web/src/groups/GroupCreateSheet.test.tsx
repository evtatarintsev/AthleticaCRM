import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  EmployeeListItemSchema,
  GroupCreateRequestSchema,
  GroupDisciplineSchema,
  type GroupDetailResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

/** Сотрудник списка с порядковым номером [n] и именем [name]. */
const employee = (n: number, name: string) =>
  EmployeeListItemSchema.parse({
    id: `0199a0b2-0000-7000-8000-70000000000${String(n)}`,
    name,
    avatarId: null,
    isOwner: false,
    isActive: true,
    joinedAt: "2026-01-01T00:00:00Z",
    roles: [],
    phoneNo: null,
    email: null,
  });

const ivanov = employee(1, "Иванов");
const sidorova = employee(2, "Сидорова");

const boxing = GroupDisciplineSchema.parse({
  id: "0199a0b2-0000-7000-8000-800000000001",
  name: "Бокс",
});

/**
 * Поддельный сервер списка групп и создания группы: созданная группа отдаётся карточкой.
 * [failCreate] — создание отвечает бизнес-ошибкой.
 */
function createServer({ failCreate = false }: { failCreate?: boolean } = {}) {
  let created: GroupDetailResponse | null = null;
  const staff = [ivanov, sidorova];
  return appServer({
    "groups/list": () => json({ groups: [], total: 0 }),
    "disciplines/list": () => json({ disciplines: [boxing] }),
    "employees/list": () => json({ employees: staff, total: staff.length }),
    "halls/list": () => json({ halls: [] }),
    "groups/create": ({ body }) => {
      if (failCreate) {
        return json({ code: "GROUP_EXISTS", message: "Группа уже есть", fields: null }, 409);
      }
      const request = GroupCreateRequestSchema.parse(body);
      created = {
        id: request.id,
        name: request.name,
        schedule: [],
        scheduleChangeAt: null,
        disciplines: [boxing].filter((d) => (request.disciplineIds ?? []).includes(d.id)),
        employees: staff
          .filter((e) => (request.employeeIds ?? []).includes(e.id))
          .map((e) => ({ id: e.id, name: e.name, avatarId: e.avatarId })),
        clients: [],
      };
      return json(created);
    },
    "groups/detail": () => json(created),
  });
}

/** Панель создания группы. */
const createSheet = () => screen.findByRole("dialog", { name: ru["groups.create"] });

/** Поле названия в панели [sheet]. */
const nameField = (sheet: HTMLElement) =>
  within(sheet).getByLabelText(ru["groups.name"], { exact: false });

/** Секция панели [sheet] с заголовком [title]. */
const sheetSection = (sheet: HTMLElement, title: string) => {
  const found = within(sheet).getByRole("heading", { name: title }).closest("section");
  if (!(found instanceof HTMLElement)) {
    throw new Error(`Нет секции «${title}»`);
  }
  return found;
};

describe("создание группы в панели", () => {
  it("«Новая группа» открывает панель поверх списка и сохраняет фильтры в адресе", async () => {
    const { history } = openApp(`/groups?q=${encodeURIComponent("Бокс")}`, createServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("link", { name: ru["groups.create"] }));

    const sheet = await createSheet();
    expect(nameField(sheet)).toHaveValue("");
    expect(within(sheet).getByText(ru["groups.detail.disciplinesEmpty"])).toBeVisible();
    expect(within(sheet).getByText(ru["groups.detail.employeesEmpty"])).toBeVisible();
    expect(history.location.search).toContain("create=true");
    expect(history.location.search).toContain("q=");
  });

  it("пустое название — ошибка у поля без запроса", async () => {
    const server = createServer();
    openApp("/groups?create=true", server.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(nameField(sheet), "   ");
    await user.click(within(sheet).getByRole("button", { name: ru["action.create"] }));

    expect(await within(sheet).findByText(ru["error.required"])).toBeVisible();
    expect(server.to("groups/create")).toHaveLength(0);
  });

  it("выбор тренеров через «Готово» показывает чипы и ничего не сохраняет", async () => {
    const server = createServer();
    openApp("/groups?create=true", server.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.click(
      within(sheetSection(sheet, ru["groups.detail.employeesTitle"])).getByRole("button", {
        name: ru["groups.detail.editSection"],
      }),
    );
    const picker = await screen.findByRole("dialog", { name: ru["groups.picker.employeesTitle"] });
    await user.click(await within(picker).findByRole("checkbox", { name: ivanov.name }));
    await user.click(within(picker).getByRole("checkbox", { name: sidorova.name }));
    await user.click(within(picker).getByRole("button", { name: ru["action.done"] }));

    await waitFor(() => {
      expect(
        screen.queryByRole("dialog", { name: ru["groups.picker.employeesTitle"] }),
      ).not.toBeInTheDocument();
    });
    const employees = sheetSection(sheet, ru["groups.detail.employeesTitle"]);
    expect(within(employees).getByText(ivanov.name)).toBeVisible();
    expect(within(employees).getByText(sidorova.name)).toBeVisible();
    expect(server.requests.map((r) => r.path)).not.toContain("groups/create");
  });

  it("«Создать» отправляет название и выбор одним запросом и открывает карточку", async () => {
    const server = createServer();
    const { history } = openApp("/groups?create=true", server.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(nameField(sheet), "  Бокс дети ");
    await user.click(
      within(sheetSection(sheet, ru["groups.detail.disciplinesTitle"])).getByRole("button", {
        name: ru["groups.detail.editSection"],
      }),
    );
    const picker = await screen.findByRole("dialog", {
      name: ru["groups.picker.disciplinesTitle"],
    });
    await user.click(await within(picker).findByRole("checkbox", { name: boxing.name }));
    await user.click(within(picker).getByRole("button", { name: ru["action.done"] }));
    await within(sheetSection(sheet, ru["groups.detail.disciplinesTitle"])).findByText(boxing.name);
    await user.click(within(sheet).getByRole("button", { name: ru["action.create"] }));

    expect(await screen.findByRole("heading", { name: "Бокс дети" })).toBeVisible();
    const request = GroupCreateRequestSchema.parse(server.to("groups/create")[0]?.body);
    expect(request).toEqual({
      id: request.id,
      name: "Бокс дети",
      disciplineIds: [boxing.id],
      employeeIds: [],
    });
    expect(history.location.pathname).toBe(`/groups/${request.id}`);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("при ошибке сервера панель остаётся открытой с введёнными данными", async () => {
    openApp("/groups?create=true", createServer({ failCreate: true }).fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.type(nameField(sheet), "Бокс дети");
    await user.click(within(sheet).getByRole("button", { name: ru["action.create"] }));

    expect(await within(sheet).findByText("Группа уже есть")).toBeVisible();
    expect(nameField(sheet)).toHaveValue("Бокс дети");
  });

  it("закрытие после выбора тренера просит подтверждение, группа не создаётся", async () => {
    const server = createServer();
    const { history } = openApp("/groups?create=true", server.fetch);
    const user = userEvent.setup();

    const sheet = await createSheet();
    await user.click(
      within(sheetSection(sheet, ru["groups.detail.employeesTitle"])).getByRole("button", {
        name: ru["groups.detail.editSection"],
      }),
    );
    const picker = await screen.findByRole("dialog", { name: ru["groups.picker.employeesTitle"] });
    await user.click(await within(picker).findByRole("checkbox", { name: ivanov.name }));
    await user.click(within(picker).getByRole("button", { name: ru["action.done"] }));
    await within(sheetSection(sheet, ru["groups.detail.employeesTitle"])).findByText(ivanov.name);
    await user.click(within(sheet).getByRole("button", { name: ru["action.cancel"] }));

    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(server.to("groups/create")).toHaveLength(0);
    expect(history.location.search).not.toContain("create");
  });
});
