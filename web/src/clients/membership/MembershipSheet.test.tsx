import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  ExtendMembershipRequestSchema,
  FreezeMembershipRequestSchema,
  type MembershipSchema,
} from "@/api/generated/contracts";
import { testSale } from "@/test/finance";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";
import { activeState, testMembership } from "@/test/memberships";

const client = ClientDetailResponseSchema.parse({
  createdAt: "2024-01-01T10:00:00Z",
  id: "0199a0b2-7c3e-7d2a-9f10-000000000505",
  name: "Борис Орлов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000901", name: "Утренняя группа" }],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

const ending = testMembership({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000a11",
  name: "1111",
  sessionsTotal: 12,
  sessionsRemaining: 12,
  state: activeState("2026-10-01", "2026-10-20", { endingSoon: ["BY_DATE"] }),
});
const upcoming = testMembership({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000a12",
  name: "Борьба",
  sessionsTotal: null,
  sessionsRemaining: null,
  state: activeState("2026-11-01", "2026-11-30", { termPosition: "NOT_STARTED" }),
});
const closed = testMembership({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000a13",
  name: "Летний",
  sessionsRemaining: 0,
  state: {
    type: "closed",
    startDate: "2026-06-01",
    endDate: "2026-06-30",
    closedOn: "2026-07-01",
    reason: "LAST_SESSION",
  },
});

/** Три неоплаченных занятия клиента. */
const unpaidSessions = [
  {
    sessionId: "0199a0b2-7c3e-7d2a-9f10-000000000e01",
    date: "2026-10-01",
    startTime: "17:00:00",
    groupId: "0199a0b2-7c3e-7d2a-9f10-000000000901",
    groupName: "Утренняя группа",
  },
  {
    sessionId: "0199a0b2-7c3e-7d2a-9f10-000000000e02",
    date: "2026-10-03",
    startTime: "17:00:00",
    groupId: "0199a0b2-7c3e-7d2a-9f10-000000000901",
    groupName: "Утренняя группа",
  },
  {
    sessionId: "0199a0b2-7c3e-7d2a-9f10-000000000e03",
    date: "2026-10-05",
    startTime: "17:00:00",
    groupId: "0199a0b2-7c3e-7d2a-9f10-000000000901",
    groupName: "Утренняя группа",
  },
];

const issuedEntry = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000c01",
  occurredAt: "2026-10-01T09:00:00Z",
  actorId: "0199a0b2-7c3e-7d2a-9f10-000000000d01",
  actorName: "Анна Админ",
  event: {
    type: "issued",
    activationMode: "IMMEDIATE",
    activateOn: null,
    activationDeadline: null,
    closeMode: "MANUAL",
  },
};

/**
 * Поддельный сервер карточки клиента с абонементами [initial]. `memberships/close` закрывает
 * абонемент; `memberships/revoke` отвечает ошибкой [revokeError], если она задана;
 * `memberships/freeze` ставит действующую заморозку, `memberships/unfreeze` её снимает;
 * `memberships/extend` переносит дату окончания или отвечает ошибкой [extendError], если она задана.
 */
function membershipsServer(
  initial: readonly MembershipSchema[],
  revokeError?: string,
  extendError?: string,
  showClientDebt = false,
) {
  let memberships = [...initial];
  let debt = showClientDebt ? unpaidSessions : [];
  const entries: unknown[] = [issuedEntry];
  return appServer({
    "sales/detail": () => json(testSale),
    "custom-fields/list": () => json([]),
    "clients/detail": () =>
      json(
        showClientDebt ? { ...client, balance: { minorUnits: -10000, currency: "RUB" } } : client,
      ),
    "clients/unpaid-sessions": () => json({ sessions: debt }),
    "clients/notes/list": () => json({ notes: [] }),
    "memberships/list": () => json({ memberships }),
    "memberships/history": () => json({ entries }),
    "memberships/unpaid-sessions": () => json({ sessions: unpaidSessions }),
    "memberships/repay-debt": () => {
      debt = [];
      return empty();
    },
    "memberships/close": () => {
      memberships = memberships.map((membership) =>
        membership.state.type === "active"
          ? {
              ...membership,
              state: {
                type: "closed",
                startDate: membership.state.startDate,
                endDate: membership.state.endDate,
                closedOn: membership.state.startDate,
                reason: "MANUAL",
              },
            }
          : membership,
      );
      entries.unshift({
        id: "0199a0b2-7c3e-7d2a-9f10-000000000c02",
        occurredAt: "2026-10-16T09:00:00Z",
        actorId: issuedEntry.actorId,
        actorName: issuedEntry.actorName,
        event: { type: "closed", reason: "MANUAL", closedOn: "2026-10-16" },
      });
      return empty();
    },
    "memberships/freeze": ({ body }) => {
      const request = FreezeMembershipRequestSchema.parse(body);
      memberships = memberships.map((membership) =>
        membership.id === request.membershipId && membership.state.type === "active"
          ? {
              ...membership,
              state: activeState(membership.state.startDate, membership.state.endDate, {
                freeze: { ...request, status: "ACTIVE" },
              }),
            }
          : membership,
      );
      return empty();
    },
    "memberships/extend": ({ body }) => {
      if (extendError !== undefined) {
        return json({ code: "EXTENSION_NOT_LATER", message: extendError, fields: null }, 400);
      }
      const request = ExtendMembershipRequestSchema.parse(body);
      memberships = memberships.map((membership) =>
        membership.id === request.membershipId && membership.state.type === "active"
          ? {
              ...membership,
              state: activeState(membership.state.startDate, request.endDate),
            }
          : membership,
      );
      return empty();
    },
    "memberships/unfreeze": () => {
      memberships = memberships.map((membership) =>
        membership.state.type === "active"
          ? { ...membership, state: { ...membership.state, freeze: null } }
          : membership,
      );
      return empty();
    },
    "memberships/revoke": () =>
      revokeError === undefined
        ? empty()
        : json({ code: "MEMBERSHIP_HAS_DEBITS", message: revokeError, fields: null }, 400),
  });
}

/** Кнопка плитки абонемента [name] в быстрой информации (первая на странице). */
async function tileButton(name: string) {
  const buttons = await screen.findAllByRole("button", {
    name: ru["clientCard.membership.open"].replace("{name}", name),
  });
  const first = buttons[0];
  if (first === undefined) {
    throw new Error(`Нет плитки абонемента «${name}»`);
  }
  return first;
}

describe("абонементы в карточке клиента", () => {
  it("показывает основной абонемент и переход к остальным и выделяет причину с сервера", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([ending, upcoming, closed]).fetch);

    const endingTile = within(await tileButton("1111"));
    expect(endingTile.getByText("Осталось 12 из 12")).not.toHaveClass("text-amber-700");
    expect(endingTile.getByText(/^до /)).toHaveClass("text-amber-700");

    expect(screen.getByRole("button", { name: /Открыть все абонементы: 3/ })).toBeVisible();
    expect(screen.getAllByText(ru["clientCard.quick.subscription"])).toHaveLength(1);
    expect(screen.queryByRole("list", { name: ru["clientCard.alerts"] })).not.toBeInTheDocument();
  });

  it("без действующих абонементов — одна некликабельная плитка", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([closed]).fetch);

    expect(await screen.findByText(ru["clientCard.quick.noSubscription"])).toBeInTheDocument();
    expect(screen.getAllByText(ru["clientCard.quick.subscription"])).toHaveLength(1);
    const alerts = within(
      screen.getByRole("heading", { name: ru["clientOverview.attention"] }).closest("section") ??
        document.body,
    );
    expect(alerts.getByText(ru["clientCard.alert.noActiveSubscription"])).toBeInTheDocument();
  });

  it("панель показывает параметры и историю с автором", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([ending]).fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));

    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    expect(sheet.getByText(ru["clientCard.subscriptionEnding"])).toBeInTheDocument();
    expect(sheet.getByText(ru["membership.close.MANUAL"])).toBeInTheDocument();
    expect(await sheet.findByText("Анна Админ", { exact: false })).toBeInTheDocument();
  });

  it("закрытие из панели с подтверждением", async () => {
    const api = membershipsServer([ending]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["clientCard.membership.closeLabel"] }));

    const confirm = within(
      await screen.findByRole("dialog", { name: ru["membership.closeConfirmTitle"] }),
    );
    expect(api.to("memberships/close")).toHaveLength(0);
    await user.click(confirm.getByRole("button", { name: ru["clientCard.membership.close"] }));

    await waitFor(() => {
      expect(api.to("memberships/close")).toHaveLength(1);
    });
    expect(api.to("memberships/close")[0]?.body).toEqual({ membershipId: ending.id });
    expect(await sheet.findByText(ru["membership.phase.closed"])).toBeInTheDocument();
    expect(
      await sheet.findByText(
        ru["membership.history.closed"].replace("{reason}", ru["membership.closeReason.MANUAL"]),
      ),
    ).toBeInTheDocument();
  });

  it("у закрытого абонемента действий нет", async () => {
    openApp(`/clients/${client.id}?tab=memberships`, membershipsServer([closed]).fetch);
    await userEvent
      .setup()
      .click(await screen.findByRole("button", { name: /Открыть все абонементы/ }));
    const user = userEvent.setup();

    const row = await screen.findByRole("button", {
      name: ru["clientCard.membership.open"].replace("{name}", "Летний"),
    });
    await user.click(row);

    const sheet = within(await screen.findByRole("dialog", { name: "Летний" }));
    expect(sheet.getByText(ru["membership.phase.closed"])).toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["clientCard.membership.closeLabel"] }),
    ).not.toBeInTheDocument();
    expect(sheet.queryByRole("button", { name: ru["membership.revoke"] })).not.toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["membership.extend.action"] }),
    ).not.toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(
      await screen.findByRole("dialog", { name: ru["clients.detail.subscriptions"] }),
    ).toBeVisible();
    await waitFor(() => {
      expect(row).toHaveFocus();
    });
  });

  it("продление из панели обновляет дату окончания в панели и плитке", async () => {
    const api = membershipsServer([ending]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.extend.action"] }));
    const form = within(await screen.findByRole("dialog", { name: ru["membership.extend.title"] }));
    const submit = form.getByRole("button", { name: ru["membership.extend.action"] });
    await user.type(form.getByLabelText(ru["membership.extend.end"]), "2099-12-31");
    await user.type(form.getByLabelText(ru["membership.extend.reason"]), "   ");
    expect(submit).toBeDisabled();
    await user.type(form.getByLabelText(ru["membership.extend.reason"]), "перенос сборов");
    await user.click(submit);

    await waitFor(() => {
      expect(api.to("memberships/extend")).toHaveLength(1);
    });
    expect(api.to("memberships/extend")[0]?.body).toEqual({
      membershipId: ending.id,
      endDate: "2099-12-31",
      reason: "перенос сборов",
    });
    await waitFor(() => {
      expect(
        screen.queryByRole("dialog", { name: ru["membership.extend.title"] }),
      ).not.toBeInTheDocument();
    });
    expect(await sheet.findByText(/2099/)).toBeInTheDocument();
    expect(screen.getAllByText(/2099/).length).toBeGreaterThan(1);
  });

  it("отказ в продлении оставляет форму открытой и показывает ошибку", async () => {
    const message = "Новая дата окончания должна быть позже текущей";
    openApp(`/clients/${client.id}`, membershipsServer([ending], undefined, message).fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.extend.action"] }));
    const form = within(await screen.findByRole("dialog", { name: ru["membership.extend.title"] }));
    await user.type(form.getByLabelText(ru["membership.extend.end"]), "2099-12-31");
    await user.type(form.getByLabelText(ru["membership.extend.reason"]), "перенос сборов");
    await user.click(form.getByRole("button", { name: ru["membership.extend.action"] }));

    expect(await form.findByText(message)).toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: ru["membership.extend.title"] })).toBeInTheDocument();
  });

  it("отказ в отзыве показывает ошибку сервера", async () => {
    const message = "Сначала верните списанные занятия";
    const api = membershipsServer([ending], message);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.revoke"] }));
    const confirm = within(
      await screen.findByRole("dialog", { name: ru["membership.revokeConfirmTitle"] }),
    );
    await user.click(confirm.getByRole("button", { name: ru["membership.revoke"] }));

    expect(await sheet.findByText(message)).toBeInTheDocument();
    expect(sheet.getByText(ru["clientCard.subscriptionEnding"])).toBeInTheDocument();
  });

  it("заморозка из панели показывает период и причину", async () => {
    const api = membershipsServer([ending]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.freeze.action"] }));
    const form = within(await screen.findByRole("dialog", { name: ru["membership.freeze.title"] }));
    expect(form.queryByRole("status")).not.toBeInTheDocument();
    await user.type(form.getByLabelText(ru["membership.freeze.end"]), "2099-12-31");
    await user.type(form.getByLabelText(ru["membership.freeze.reason"]), "отпуск");
    await user.click(form.getByRole("button", { name: ru["membership.freeze.action"] }));

    await waitFor(() => {
      expect(api.to("memberships/freeze")).toHaveLength(1);
    });
    expect(api.to("memberships/freeze")[0]?.body).toMatchObject({
      membershipId: ending.id,
      endDate: "2099-12-31",
      reason: "отпуск",
    });
    expect(await sheet.findByText(/Заморожен до/)).toBeInTheDocument();
    expect(sheet.getByText(/отпуск/)).toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["membership.freeze.action"] }),
    ).not.toBeInTheDocument();
  });

  it("форма заморозки предупреждает о другом абонементе в ту же группу", async () => {
    const other = testMembership({
      id: "0199a0b2-7c3e-7d2a-9f10-000000000a14",
      name: "Второй",
      state: activeState("2026-10-01", "2026-10-31"),
    });
    openApp(`/clients/${client.id}`, membershipsServer([ending, other]).fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.freeze.action"] }));
    const form = within(await screen.findByRole("dialog", { name: ru["membership.freeze.title"] }));

    expect(form.getByRole("status")).toHaveTextContent(/Второй/);
  });

  it("действующую заморозку можно снять с подтверждением, плитка и строка её показывают", async () => {
    const frozen = testMembership({
      id: "0199a0b2-7c3e-7d2a-9f10-000000000a15",
      name: "Замороженный",
      state: activeState("2026-10-01", "2026-11-14", {
        freeze: {
          startDate: "2026-10-10",
          endDate: "2026-10-23",
          reason: "болезнь",
          status: "ACTIVE",
        },
      }),
    });
    const api = membershipsServer([frozen]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    const tile = await tileButton("Замороженный");
    expect(within(tile).getByText(/Заморожен до/)).toBeInTheDocument();
    expect(screen.getAllByText(/Заморожен до/)).toHaveLength(1);
    await user.click(tile);
    const sheet = within(await screen.findByRole("dialog", { name: "Замороженный" }));
    expect(sheet.getByText(/болезнь/)).toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["membership.freeze.action"] }),
    ).not.toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["membership.extend.action"] }),
    ).not.toBeInTheDocument();
    await user.click(sheet.getByRole("button", { name: ru["membership.unfreeze"] }));
    const confirm = within(
      await screen.findByRole("dialog", { name: ru["membership.unfreezeConfirmTitle"] }),
    );
    await user.click(confirm.getByRole("button", { name: ru["membership.unfreeze"] }));

    await waitFor(() => {
      expect(api.to("memberships/unfreeze")).toHaveLength(1);
    });
    expect(api.to("memberships/unfreeze")[0]?.body).toEqual({ membershipId: frozen.id });
    expect(
      await sheet.findByRole("button", { name: ru["membership.freeze.action"] }),
    ).toBeInTheDocument();
  });

  it("запланированную заморозку можно отменить", async () => {
    const scheduled = testMembership({
      id: "0199a0b2-7c3e-7d2a-9f10-000000000a16",
      name: "С заморозкой",
      state: activeState("2026-10-01", "2026-11-14", {
        freeze: {
          startDate: "2099-01-10",
          endDate: "2099-01-23",
          reason: "отпуск",
          status: "SCHEDULED",
        },
      }),
    });
    const api = membershipsServer([scheduled]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("С заморозкой"));
    const sheet = within(await screen.findByRole("dialog", { name: "С заморозкой" }));
    expect(sheet.getAllByText(/Заморозка с/).length).toBeGreaterThan(0);
    await user.click(sheet.getByRole("button", { name: ru["membership.cancelFreeze"] }));
    const confirm = within(
      await screen.findByRole("dialog", { name: ru["membership.cancelFreezeConfirmTitle"] }),
    );
    await user.click(confirm.getByRole("button", { name: ru["membership.cancelFreeze"] }));

    await waitFor(() => {
      expect(api.to("memberships/unfreeze")).toHaveLength(1);
    });
  });

  it("плитка открывается с клавиатуры", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([ending]).fetch);
    const user = userEvent.setup();

    (await tileButton("1111")).focus();
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("dialog", { name: "1111" })).toBeInTheDocument();
  });
  it("погашение части долга во вложенной панели", async () => {
    const api = membershipsServer([ending]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));
    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.repay.action"] }));
    const repay = within(await screen.findByRole("dialog", { name: ru["membership.repay.title"] }));
    const boxes = await repay.findAllByRole("checkbox");
    expect(boxes).toHaveLength(3);
    boxes.forEach((box) => {
      expect(box).toBeChecked();
    });
    const last = boxes[2];
    if (last === undefined) {
      throw new Error("нет третьего занятия");
    }
    await user.click(last);
    await user.click(
      repay.getByRole("button", { name: ru["membership.repay.submit"].replace("{count}", "2") }),
    );

    await waitFor(() => {
      expect(api.to("memberships/repay-debt")).toHaveLength(1);
    });
    expect(api.to("memberships/repay-debt")[0]?.body).toEqual({
      membershipId: ending.id,
      sessionIds: unpaidSessions.slice(0, 2).map((session) => session.sessionId),
    });
  });

  it("больше остатка выбрать нельзя", async () => {
    const one = testMembership({
      id: "0199a0b2-7c3e-7d2a-9f10-000000000a14",
      name: "Разовый",
      sessionsRemaining: 1,
      state: activeState("2026-10-01", "2026-10-31"),
    });
    openApp(`/clients/${client.id}`, membershipsServer([one]).fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("Разовый"));
    const sheet = within(await screen.findByRole("dialog", { name: "Разовый" }));
    await user.click(sheet.getByRole("button", { name: ru["membership.repay.action"] }));
    const repay = within(await screen.findByRole("dialog", { name: ru["membership.repay.title"] }));

    expect(
      await repay.findByRole("button", {
        name: ru["membership.repay.submit"].replace("{count}", "3"),
      }),
    ).toBeDisabled();
    expect(repay.getByText(ru["membership.repay.tooMany"].replace("{count}", "1"))).toBeVisible();
  });
});

it("+N is a separate keyboard control and opens the list without the primary sheet", async () => {
  openApp(`/clients/${client.id}`, membershipsServer([ending, upcoming]).fetch);
  const user = userEvent.setup();
  const more = await screen.findByRole("button", { name: /Открыть все абонементы: 2/ });
  expect(more.closest("button")?.parentElement?.closest("button")).toBeNull();
  for (let i = 0; i < 35 && document.activeElement !== more; i++) {
    await user.tab();
  }
  expect(more).toHaveFocus();
  expect(more.className).toContain("focus-visible:ring");
  await user.keyboard(" ");
  expect(
    await screen.findByRole("dialog", { name: ru["clients.detail.subscriptions"] }),
  ).toBeVisible();
  expect(screen.queryByRole("dialog", { name: "1111" })).toBeNull();
  expect(await screen.findByRole("button", { name: /Борьба/ })).toBeVisible();
});

it("extending a non-primary membership updates its row and recalculates the overview", async () => {
  const later = {
    ...ending,
    id: upcoming.id,
    name: "Другой",
    state: activeState("2026-10-01", "2026-11-30"),
  };
  openApp(`/clients/${client.id}?tab=memberships`, membershipsServer([ending, later]).fetch);
  await userEvent
    .setup()
    .click(await screen.findByRole("button", { name: /Открыть все абонементы/ }));
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Другой/ }));
  const sheet = within(await screen.findByRole("dialog", { name: "Другой" }));
  await user.click(sheet.getByRole("button", { name: ru["membership.extend.action"] }));
  const form = within(await screen.findByRole("dialog", { name: ru["membership.extend.title"] }));
  const end = form.getByLabelText(ru["membership.extend.end"]);
  await user.clear(end);
  await user.type(end, "2099-12-31");
  await user.type(form.getByLabelText(ru["membership.extend.reason"]), "Запрос клиента");
  await user.click(form.getByRole("button", { name: ru["membership.extend.action"] }));
  await waitFor(() => {
    expect(sheet.getByText(/2099/)).toBeVisible();
  });
  await user.keyboard("{Escape}");
  await user.keyboard("{Escape}");
  expect(await tileButton("1111")).toBeVisible();
  expect(screen.queryByText(/2099/)).toBeNull();
});

it("repayment refreshes client unpaid sessions and keeps the negative balance alert", async () => {
  const api = membershipsServer([ending], undefined, undefined, true);
  openApp(`/clients/${client.id}`, api.fetch);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: ru["membership.repay.action"] }));
  const repay = within(await screen.findByRole("dialog", { name: ru["membership.repay.title"] }));
  await user.click(await repay.findByRole("button", { name: /Списать занятия: 3/ }));
  await waitFor(() => {
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  expect(api.to("clients/unpaid-sessions").length).toBeGreaterThan(1);
  expect(screen.queryByText(/3 неоплаченных занятия/)).toBeNull();
  expect(screen.getByRole("heading", { name: ru["clientOverview.attention"] })).toBeVisible();
  expect(screen.getByText(/Долг/)).toBeVisible();
});

it("показывает частичную оплату и открывает продажу абонемента", async () => {
  const partial = testMembership({
    name: "Частичный",
    state: activeState("2026-10-01", "2026-11-30"),
    sale: {
      saleId: testSale.id,
      paid: { minorUnits: 150000, currency: "RUB" },
      debt: { minorUnits: 250000, currency: "RUB" },
      paymentState: "PARTIAL",
    },
  });
  const api = membershipsServer([partial]);
  openApp(`/clients/${client.id}`, api.fetch);
  const user = userEvent.setup();
  await user.click(await tileButton("Частичный"));
  const panel = await screen.findByRole("dialog", { name: "Частичный" });
  expect(within(panel).getByText(ru["finance.state.PARTIAL"])).toBeInTheDocument();
  expect(within(panel).getByText(/К оплате: 2\s500,00/)).toBeInTheDocument();
  await user.click(within(panel).getByRole("button", { name: ru["finance.sale"] }));
  expect(await screen.findByRole("dialog", { name: ru["finance.sale"] })).toBeInTheDocument();
  await waitFor(() => {
    expect(api.to("sales/detail")).toHaveLength(1);
  });
});
