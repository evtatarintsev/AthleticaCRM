import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AdjustBalanceRequestSchema,
  ClientDetailResponseSchema,
  type ClientDetailResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp, session } from "@/test/app";

const bob = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000502",
  name: "Борис Петров",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [],
  balance: { minorUnits: 10000, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

function balanceServer(initial: ClientDetailResponse, permissions: readonly string[]) {
  let client = initial;
  return appServer({
    "auth/me": () => json(session(undefined, undefined, undefined, permissions)),
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "clients/balance/adjust": ({ body }) => {
      const request = AdjustBalanceRequestSchema.parse(body);
      client = {
        ...client,
        balance: {
          currency: request.amount.currency,
          minorUnits: client.balance.minorUnits + request.amount.minorUnits,
        },
      };
      return json(client);
    },
    "clients/balance/history": () =>
      json({
        entries: [
          {
            id: "0199a0b2-7c3e-7d2a-9f10-000000000601",
            amount: { minorUnits: 5000, currency: "RUB" },
            balanceAfter: { minorUnits: 15000, currency: "RUB" },
            operationType: "MANUAL",
            note: "Пополнение наличными",
            performedBy: { id: session().employeeId, name: session().name },
            createdAt: "2024-01-01T10:00:00Z",
          },
        ],
      }),
  });
}

describe("баланс клиента", () => {
  it("без права CAN_VIEW_CLIENT_BALANCE блок не показывается", async () => {
    const api = balanceServer(bob, []);
    openApp(`/clients/${bob.id}`, api.fetch);

    await screen.findByRole("heading", { name: bob.name });
    expect(screen.queryByText(ru["clients.detail.balance"])).not.toBeInTheDocument();
  });

  it("корректирует баланс и показывает историю", async () => {
    const api = balanceServer(bob, ["CAN_VIEW_CLIENT_BALANCE"]);
    openApp(`/clients/${bob.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByText(ru["clients.detail.balance"]);
    await user.click(screen.getByRole("button", { name: ru["clients.detail.balanceAdjust"] }));

    const dialog = await screen.findByRole("dialog", {
      name: ru["clients.detail.balanceAdjustTitle"],
    });
    await user.click(screen.getByLabelText(ru["clients.detail.balanceDebit"]));
    await user.type(screen.getByLabelText(ru["clients.detail.balanceAmount"]), "50");
    await user.type(screen.getByLabelText(ru["clients.detail.balanceNote"]), "Списание");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(dialog).not.toBeInTheDocument();
    });
    const request = AdjustBalanceRequestSchema.parse(api.to("clients/balance/adjust").at(-1)?.body);
    expect(request.amount).toEqual({ minorUnits: -5000, currency: "RUB" });
    expect(request.note).toBe("Списание");

    await user.click(screen.getByRole("button", { name: ru["clients.detail.balanceHistory"] }));
    await screen.findByText("Пополнение наличными");
  });
});
