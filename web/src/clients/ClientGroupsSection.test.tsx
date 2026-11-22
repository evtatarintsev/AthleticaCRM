import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  AddClientToGroupsRequestSchema,
  ClientDetailResponseSchema,
  GroupIdSchema,
  GroupDetailRequestSchema,
  ScheduleListRequestSchema,
  RemoveClientFromGroupRequestSchema,
  type ClientDetailResponse,
  type GroupId,
} from "@/api/generated/contracts";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

const morning = {
  id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000901"),
  name: "Утренняя группа",
};
const evening = {
  id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000902"),
  name: "Вечерняя группа",
};
const stretching = {
  id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000903"),
  name: "Стретчинг",
};
const allGroups = [morning, evening, stretching];

const carl = ClientDetailResponseSchema.parse({
  createdAt: "2024-01-01T10:00:00Z",
  id: "0199a0b2-7c3e-7d2a-9f10-000000000503",
  name: "Карл Орлов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [morning],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

/**
 * Сервер карточки клиента [initial]; запись, затрагивающая группу из [failing], завершается
 * ошибкой целиком — как транзакция на сервере.
 */
function groupsServer(initial: ClientDetailResponse, failing = new Set<GroupId>()) {
  let client = initial;
  return appServer({
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "groups/list-for-select": () => json(allGroups),
    "clients/add-to-groups": ({ body }) => {
      const request = AddClientToGroupsRequestSchema.parse(body);
      if (request.groupIds.some((id) => failing.has(id))) {
        return json({ code: "GROUP_NOT_FOUND", message: "Группа не найдена", fields: null }, 404);
      }
      const added = allGroups.filter((group) => request.groupIds.includes(group.id));
      client = { ...client, groups: [...client.groups, ...added] };
      return empty();
    },
    "clients/remove-from-group": ({ body }) => {
      const request = RemoveClientFromGroupRequestSchema.parse(body);
      client = { ...client, groups: client.groups.filter((g) => g.id !== request.groupId) };
      return empty();
    },
  });
}

/** Открывает карточку клиента и панель выбора групп. */
async function openPanel(api: ReturnType<typeof groupsServer>) {
  openApp(`/clients/${carl.id}`, api.fetch);
  const user = userEvent.setup();
  await screen.findByText(morning.name);
  await user.click(screen.getByRole("button", { name: ru["clients.detail.addToGroup"] }));
  const panel = await screen.findByRole("dialog", { name: ru["clients.detail.addToGroupsTitle"] });
  await within(panel).findByRole("checkbox", { name: evening.name });
  return { user, panel };
}

/** Запросы записи клиента в группы, по порядку. */
function addRequests(api: ReturnType<typeof groupsServer>) {
  return api
    .to("clients/add-to-groups")
    .map((request) => AddClientToGroupsRequestSchema.parse(request.body));
}

describe("группы клиента", () => {
  it("в панели все группы филиала, группа клиента заблокирована с пометкой", async () => {
    const { panel } = await openPanel(groupsServer(carl));

    const member = within(panel).getByRole("checkbox", { name: new RegExp(morning.name) });
    expect(member).toBeDisabled();
    expect(within(panel).getByText(ru["groups.detail.alreadyInGroup"])).toBeVisible();
    expect(within(panel).getByRole("checkbox", { name: evening.name })).toBeEnabled();
    expect(within(panel).getByRole("checkbox", { name: stretching.name })).toBeEnabled();
    expect(within(panel).getByRole("button", { name: ru["action.add"] })).toBeDisabled();
  });

  it("записывает клиента в две группы за раз", async () => {
    const api = groupsServer(carl);
    const { user, panel } = await openPanel(api);

    await user.click(within(panel).getByRole("checkbox", { name: evening.name }));
    await user.click(within(panel).getByRole("checkbox", { name: stretching.name }));
    await user.click(within(panel).getByRole("button", { name: ru["action.add"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(addRequests(api)).toEqual([
      { clientId: carl.id, groupIds: [evening.id, stretching.id] },
    ]);
    expect(toast.success).toHaveBeenLastCalledWith(
      ru["clients.detail.groupsAdded"].replace("{count}", "2"),
    );
    expect(await screen.findByText(evening.name)).toBeVisible();
    expect(screen.getByText(stretching.name)).toBeVisible();
  });

  it("при ошибке не записывает ни в одну группу, повтор записывает обе", async () => {
    const failing = new Set([stretching.id]);
    const api = groupsServer(carl, failing);
    const { user, panel } = await openPanel(api);

    await user.click(within(panel).getByRole("checkbox", { name: evening.name }));
    await user.click(within(panel).getByRole("checkbox", { name: stretching.name }));
    await user.click(within(panel).getByRole("button", { name: ru["action.add"] }));

    expect(await within(panel).findByRole("alert")).toHaveTextContent("Группа не найдена");
    expect(toast.success).not.toHaveBeenCalled();
    expect(within(panel).getByRole("checkbox", { name: evening.name })).toBeChecked();
    expect(within(panel).getByRole("checkbox", { name: stretching.name })).toBeChecked();
    expect(screen.getAllByText(evening.name)).toHaveLength(1);

    failing.clear();
    await user.click(within(panel).getByRole("button", { name: ru["action.add"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(addRequests(api)).toHaveLength(2);
    expect(addRequests(api).at(-1)?.groupIds).toEqual([evening.id, stretching.id]);
    expect(toast.success).toHaveBeenLastCalledWith(
      ru["clients.detail.groupsAdded"].replace("{count}", "2"),
    );
  });

  it("закрытие с отмеченной группой требует подтверждения отказа", async () => {
    const api = groupsServer(carl);
    const { user, panel } = await openPanel(api);

    await user.click(within(panel).getByRole("checkbox", { name: evening.name }));
    await user.click(within(panel).getByRole("button", { name: ru["action.cancel"] }));

    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(api.to("clients/add-to-groups")).toHaveLength(0);
  });

  it("без отметок закрывается сразу", async () => {
    const { user, panel } = await openPanel(groupsServer(carl));

    await user.click(within(panel).getByRole("button", { name: ru["action.cancel"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.queryByText(ru["editSheet.discardTitle"])).not.toBeInTheDocument();
  });

  it("убирает клиента из группы крестиком", async () => {
    openApp(`/clients/${carl.id}`, groupsServer(carl).fetch);
    const user = userEvent.setup();

    await screen.findByText(morning.name);
    await user.click(
      screen.getByRole("button", {
        name: ru["clients.detail.removeFromGroupAria"].replace("{name}", morning.name),
      }),
    );

    await waitFor(() => {
      expect(screen.queryByText(morning.name)).not.toBeInTheDocument();
    });
  });
});

it("shows each group's schedule, hall and nearest session using the shared schedule request", async () => {
  const today = todayLocalDate();
  const group = (name: typeof morning) => ({
    ...name,
    schedule: [
      {
        dayOfWeek: "MONDAY",
        startAt: "18:00",
        endAt: "19:00",
        hallId: "0199a0b2-7c3e-7d2a-9f10-000000000d01",
        hallName: "Зал 1",
        validity: null,
      },
    ],
    scheduleChangeAt: null,
    disciplines: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000d02", name: "ОФП" }],
    employees: [],
    shownInWidgets: true,
  });
  const session = (id: string, date: string, status: string) => ({
    id,
    group: morning,
    date,
    startTime: "18:00",
    endTime: "19:00",
    hall: { id: "0199a0b2-7c3e-7d2a-9f10-000000000d01", name: "Зал 1" },
    coaches: [],
    disciplines: [],
    status,
    colorKey: "GREEN",
    coachesOverridden: false,
    rescheduledFrom: null,
    isManual: false,
  });
  const upcomingId = "0199a0b2-7c3e-7d2a-9f10-000000000c01";
  const api = appServer({
    "clients/detail": () => json({ ...carl, groups: [morning, evening] }),
    "groups/detail": ({ query }) =>
      json(
        group(
          GroupDetailRequestSchema.parse({ id: query.get("id") }).id === morning.id
            ? morning
            : evening,
        ),
      ),
    "schedule/list": () =>
      json({
        sessions: [
          session("0199a0b2-7c3e-7d2a-9f10-000000000c02", addDays(today, -1), "SCHEDULED"),
          session("0199a0b2-7c3e-7d2a-9f10-000000000c03", today, "CANCELLED"),
          session(upcomingId, addDays(today, 1), "SCHEDULED"),
        ],
      }),
  });
  openApp(`/clients/${carl.id}`, api.fetch);
  const next = await screen.findByRole("link", { name: /Открыть занятие: Утренняя группа/ });
  expect(next).toHaveAttribute("href", `/sessions/${upcomingId}`);
  const row = next.closest("li");
  if (row === null) throw new Error("Missing group row");
  expect(await within(row).findByText(/Пн · 18:00.*19:00 · Зал 1/)).toBeVisible();
  expect(within(row).getByText("ОФП")).toBeVisible();
  expect(await screen.findByText(ru["clientOverview.noUpcoming"])).toBeVisible();
  expect(api.to("schedule/list")).toHaveLength(1);
  expect(ScheduleListRequestSchema.parse(api.to("schedule/list")[0]?.body).groupIds).toEqual([
    morning.id,
    evening.id,
  ]);
});
