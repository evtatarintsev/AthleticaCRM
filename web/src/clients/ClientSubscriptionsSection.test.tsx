import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  IssueMembershipRequestSchema,
  type ClientDetailResponse,
  type MembershipSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";
import { activeState, testMembership } from "@/test/memberships";

const dana = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000504",
  name: "Дана Лебедева",
  avatarId: null,
  birthday: null,
  gender: "FEMALE",
  groups: [],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

function subscriptionsServer(
  initial: ClientDetailResponse,
  existing: readonly MembershipSchema[] = [],
) {
  const client = initial;
  let memberships: MembershipSchema[] = [...existing];
  return appServer({
    "custom-fields/list": () => json([]),
    "org/settings": () => json({ name: "Лига", timezone: "Europe/Moscow", currency: "RUB" }),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "tariffs/list": () => json({ tariffs: [] }),
    "memberships/list": () => json({ memberships }),
    "memberships/issue": ({ body }) => {
      const request = IssueMembershipRequestSchema.parse(body);
      memberships = [
        ...memberships,
        testMembership({
          id: request.id,
          name: request.name,
          sessionsTotal: request.sessions,
          sessionsRemaining: request.sessions,
          state: activeState("2026-10-16", "2026-11-15"),
        }),
      ];
      return empty();
    },
  });
}

describe("абонементы клиента", () => {
  it("выдаёт индивидуальный абонемент и показывает его в истории", async () => {
    const api = subscriptionsServer(dana);
    openApp(`/clients/${dana.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByText(ru["clients.detail.subscriptionsEmpty"]);
    await user.click(screen.getByRole("button", { name: ru["clients.detail.issueSubscription"] }));

    const panel = await screen.findByRole("dialog", {
      name: ru["clients.detail.issueSubscription"],
    });
    await user.click(
      await within(panel).findByRole("button", {
        name: new RegExp(`^${ru["clients.detail.subscriptionIndividual"]}`),
      }),
    );
    await user.type(screen.getByLabelText(ru["clients.detail.subscriptionSessionsField"]), "10");
    await user.type(
      screen.getByLabelText(ru["clients.detail.subscriptionPrice"].replace("{currency}", "RUB")),
      "5000",
    );
    await user.click(within(panel).getByRole("button", { name: ru["clients.issue.submit"] }));

    await waitFor(() => {
      expect(api.to("memberships/issue")).toHaveLength(1);
    });
    const request = IssueMembershipRequestSchema.parse(api.to("memberships/issue").at(-1)?.body);
    expect(request.clientId).toBe(dana.id);
    expect(request.sessions).toBe(10);
    expect(request.price).toEqual({ minorUnits: 500000, currency: "RUB" });
    expect(request.tariffPlanId).toBeNull();
    expect(request.name).toBe(ru["clients.detail.subscriptionIndividual"]);
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    expect(await screen.findAllByText(ru["clients.detail.subscriptionIndividual"])).toHaveLength(2);
  });
  it("заканчивается по занятиям — подсвечен остаток, а не дата", async () => {
    const lastSession = testMembership({
      name: "Последнее",
      sessionsRemaining: 1,
      state: activeState("2026-10-01", "2026-10-31", { endingSoon: ["BY_SESSIONS"] }),
    });
    openApp(`/clients/${dana.id}`, subscriptionsServer(dana, [lastSession]).fetch);

    const section = within(
      (await screen.findByRole("heading", { name: ru["clients.detail.subscriptions"] })).closest(
        "section",
      ) ?? document.body,
    );
    expect(await section.findByText("Осталось 1 из 8")).toHaveClass("text-amber-700");
    expect(section.getByText(/31/)).not.toHaveClass("text-amber-700");
  });

  it("к закрытию — пометка в строке абонемента", async () => {
    const pending = testMembership({
      name: "Закончился",
      sessionsRemaining: 0,
      state: activeState("2026-09-01", "2026-09-30", {
        termPosition: "EXPIRED",
        closePending: true,
      }),
    });
    openApp(`/clients/${dana.id}`, subscriptionsServer(dana, [pending]).fetch);

    expect(await screen.findAllByText(ru["membership.phase.closePending"])).not.toHaveLength(0);
    expect(
      screen.getAllByText(`${ru["membership.expired"]} · ${ru["membership.exhausted"]}`),
    ).not.toHaveLength(0);
  });
});
