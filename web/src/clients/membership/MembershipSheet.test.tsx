import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ClientDetailResponseSchema, type MembershipSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";
import { activeState, testMembership } from "@/test/memberships";

const client = ClientDetailResponseSchema.parse({
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
 * абонемент; `memberships/revoke` отвечает ошибкой [revokeError], если она задана.
 */
function membershipsServer(initial: readonly MembershipSchema[], revokeError?: string) {
  let memberships = [...initial];
  const entries: unknown[] = [issuedEntry];
  return appServer({
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "memberships/list": () => json({ memberships }),
    "memberships/history": () => json({ entries }),
    "memberships/unpaid-sessions": () => json({ sessions: unpaidSessions }),
    "memberships/repay-debt": () => empty(),
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
  it("показывает плитку на каждый действующий абонемент и выделяет причину с сервера", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([ending, upcoming, closed]).fetch);

    const endingTile = within(await tileButton("1111"));
    expect(endingTile.getByText("Осталось 12 из 12")).not.toHaveClass("text-amber-700");
    expect(endingTile.getByText(/^до /)).toHaveClass("text-amber-700");

    const upcomingTile = within(await tileButton("Борьба"));
    expect(upcomingTile.getByText(/^с /)).toBeInTheDocument();

    expect(screen.getAllByText(ru["clientCard.quick.subscription"])).toHaveLength(2);
    expect(screen.queryByRole("list", { name: ru["clientCard.alerts"] })).not.toBeInTheDocument();
  });

  it("без действующих абонементов — одна некликабельная плитка", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([closed]).fetch);

    expect(await screen.findByText(ru["clientCard.quick.noSubscription"])).toBeInTheDocument();
    expect(screen.getAllByText(ru["clientCard.quick.subscription"])).toHaveLength(1);
    const alerts = within(screen.getByRole("list", { name: ru["clientCard.alerts"] }));
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
    openApp(`/clients/${client.id}`, membershipsServer([closed]).fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: ru["clientCard.membership.open"].replace("{name}", "Летний"),
      }),
    );

    const sheet = within(await screen.findByRole("dialog", { name: "Летний" }));
    expect(sheet.getByText(ru["membership.phase.closed"])).toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["clientCard.membership.closeLabel"] }),
    ).not.toBeInTheDocument();
    expect(sheet.queryByRole("button", { name: ru["membership.revoke"] })).not.toBeInTheDocument();
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
