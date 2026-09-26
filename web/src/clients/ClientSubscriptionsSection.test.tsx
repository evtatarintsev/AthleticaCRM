import { screen, waitFor } from "@testing-library/react";
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

function subscriptionsServer(initial: ClientDetailResponse) {
  const client = initial;
  let memberships: MembershipSchema[] = [];
  return appServer({
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "tariffs/list": () => json({ tariffs: [] }),
    "memberships/list": () => json({ memberships }),
    "memberships/issue": ({ body }) => {
      const request = IssueMembershipRequestSchema.parse(body);
      memberships = [
        ...memberships,
        {
          id: request.id,
          name: request.name,
          sessionsTotal: request.sessions,
          sessionsRemaining: request.sessions,
          startDate: request.startDate,
          endDate: request.startDate,
          price: request.price,
          status: "ACTIVE",
        },
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
    await user.click(screen.getByRole("link", { name: ru["clients.detail.issueSubscription"] }));

    await screen.findByRole("heading", { name: ru["clients.detail.issueSubscription"] });
    await user.type(screen.getByLabelText(ru["clients.detail.subscriptionSessionsField"]), "10");
    await user.type(
      screen.getByLabelText(ru["clients.detail.subscriptionPrice"].replace("{currency}", "RUB")),
      "5000",
    );
    await user.click(screen.getByRole("button", { name: ru["clients.detail.issueSubscription"] }));

    await waitFor(() => {
      expect(api.to("memberships/issue")).toHaveLength(1);
    });
    const request = IssueMembershipRequestSchema.parse(api.to("memberships/issue").at(-1)?.body);
    expect(request.clientId).toBe(dana.id);
    expect(request.sessions).toBe(10);
    expect(request.price).toEqual({ minorUnits: 500000, currency: "RUB" });

    await screen.findByText(ru["clients.detail.subscriptionIndividual"]);
  });
});
