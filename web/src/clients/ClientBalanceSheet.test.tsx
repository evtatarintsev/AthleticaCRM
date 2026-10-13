import { screen, waitFor, within } from "@testing-library/react";
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

/**
 * Сопоставитель доступного имени плитки баланса с суммой [amount]; пробелы сравниваются
 * без учёта вида, потому что сумма форматируется с неразрывными пробелами.
 */
function tileName(amount: string): (name: string) => boolean {
  const expected = ru["clientCard.balanceOpen"].replace("{amount}", amount);
  return (name) => name.replace(/\s/gu, " ") === expected;
}

/**
 * Сервер карточки клиента [initial] с правами [permissions]; при [adjustFails] корректировка
 * отклоняется бизнес-ошибкой.
 */
function balanceServer(
  initial: ClientDetailResponse,
  permissions: readonly string[],
  adjustFails = false,
) {
  let client = initial;
  return appServer({
    "auth/me": () => json(session(undefined, undefined, undefined, permissions)),
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "clients/balance/adjust": ({ body }) => {
      if (adjustFails) {
        return json({ code: "BALANCE_LOCKED", message: "Баланс заблокирован", fields: null }, 409);
      }
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
            amount: { minorUnits: -5000, currency: "RUB" },
            balanceAfter: { minorUnits: 10000, currency: "RUB" },
            operationType: "admin_debit",
            note: "Долг за май",
            performedBy: { id: session().employeeId, name: "Иванова" },
            createdAt: "2024-01-01T10:00:00Z",
          },
        ],
      }),
  });
}

/** Открывает карточку клиента и панель «Баланс» кликом по плитке. */
async function openBalance(api: ReturnType<typeof balanceServer>) {
  openApp(`/clients/${bob.id}`, api.fetch);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: tileName("100,00 ₽") }));
  const panel = await screen.findByRole("dialog", { name: ru["clientCard.balance"] });
  return { user, panel };
}

describe("баланс клиента", () => {
  it("без права CAN_VIEW_CLIENT_BALANCE плитка показывает сумму, но не кликается", async () => {
    const api = balanceServer(bob, []);
    openApp(`/clients/${bob.id}`, api.fetch);

    await screen.findByRole("heading", { name: bob.name });
    expect(screen.getByText(ru["clientCard.balance"])).toBeInTheDocument();
    expect(screen.getByText("100,00 ₽")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: tileName("100,00 ₽") })).not.toBeInTheDocument();
  });

  it("журнала баланса нет в ленте, он открывается панелью по клику на плитку", async () => {
    const api = balanceServer(bob, ["CAN_VIEW_CLIENT_BALANCE"]);
    openApp(`/clients/${bob.id}`, api.fetch);
    const user = userEvent.setup();

    const tile = await screen.findByRole("button", { name: tileName("100,00 ₽") });
    expect(tile).toHaveAttribute("aria-haspopup", "dialog");
    expect(screen.queryByText("Долг за май")).not.toBeInTheDocument();

    await user.click(tile);
    const panel = await screen.findByRole("dialog", { name: ru["clientCard.balance"] });
    expect(await within(panel).findByText("Долг за май")).toBeInTheDocument();
    expect(within(panel).getByText(ru["clientCard.operation.admin_debit"])).toBeInTheDocument();
    expect(within(panel).getByText(/Иванова/)).toBeInTheDocument();
    expect(within(panel).getByText("остаток 100,00 ₽")).toBeInTheDocument();
    expect(
      within(panel).getByRole("button", { name: ru["clients.detail.balanceAdjust"] }),
    ).toBeInTheDocument();
  });

  it("панель открывается с клавиатуры и возвращает фокус на плитку при закрытии", async () => {
    const api = balanceServer(bob, ["CAN_VIEW_CLIENT_BALANCE"]);
    openApp(`/clients/${bob.id}`, api.fetch);
    const user = userEvent.setup();

    const tile = await screen.findByRole("button", { name: tileName("100,00 ₽") });
    tile.focus();
    await user.keyboard("{Enter}");
    const panel = await screen.findByRole("dialog", { name: ru["clientCard.balance"] });

    await user.click(within(panel).getByRole("button", { name: ru["action.close"] }));
    await waitFor(() => {
      expect(panel).not.toBeInTheDocument();
    });
    expect(tile).toHaveFocus();
  });

  it("корректирует баланс во вложенной панели и обновляет плитку, панель и журнал", async () => {
    const api = balanceServer(bob, ["CAN_VIEW_CLIENT_BALANCE"]);
    const { user, panel } = await openBalance(api);

    await user.click(
      within(panel).getByRole("button", { name: ru["clients.detail.balanceAdjust"] }),
    );
    const form = await screen.findByRole("dialog", {
      name: ru["clients.detail.balanceAdjustTitle"],
    });
    await user.click(within(form).getByLabelText(ru["clients.detail.balanceDebit"]));
    await user.type(within(form).getByLabelText(ru["clients.detail.balanceAmount"]), "50");
    await user.type(within(form).getByLabelText(ru["clients.detail.balanceNote"]), "Списание");
    await user.click(within(form).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(form).not.toBeInTheDocument();
    });
    const request = AdjustBalanceRequestSchema.parse(api.to("clients/balance/adjust").at(-1)?.body);
    expect(request.amount).toEqual({ minorUnits: -5000, currency: "RUB" });
    expect(request.note).toBe("Списание");

    expect(
      screen.getByRole("button", { name: tileName("50,00 ₽"), hidden: true }),
    ).toBeInTheDocument();
    expect(within(panel).getByText("50,00 ₽")).toBeInTheDocument();
    await waitFor(() => {
      expect(api.to("clients/balance/history").length).toBeGreaterThan(1);
    });
  });

  it("некорректная сумма не отправляется", async () => {
    const api = balanceServer(bob, ["CAN_VIEW_CLIENT_BALANCE"]);
    const { user, panel } = await openBalance(api);

    await user.click(
      within(panel).getByRole("button", { name: ru["clients.detail.balanceAdjust"] }),
    );
    const form = await screen.findByRole("dialog", {
      name: ru["clients.detail.balanceAdjustTitle"],
    });
    await user.type(within(form).getByLabelText(ru["clients.detail.balanceAmount"]), "0");
    await user.type(within(form).getByLabelText(ru["clients.detail.balanceNote"]), "Ошибка");
    await user.click(within(form).getByRole("button", { name: ru["action.save"] }));

    expect(await within(form).findByText(ru["error.invalidAmount"])).toBeInTheDocument();
    expect(api.to("clients/balance/adjust")).toHaveLength(0);
  });

  it("при ошибке сервера вложенная панель остаётся открытой с введёнными значениями", async () => {
    const api = balanceServer(bob, ["CAN_VIEW_CLIENT_BALANCE"], true);
    const { user, panel } = await openBalance(api);

    await user.click(
      within(panel).getByRole("button", { name: ru["clients.detail.balanceAdjust"] }),
    );
    const form = await screen.findByRole("dialog", {
      name: ru["clients.detail.balanceAdjustTitle"],
    });
    await user.type(within(form).getByLabelText(ru["clients.detail.balanceAmount"]), "50");
    await user.type(within(form).getByLabelText(ru["clients.detail.balanceNote"]), "Пополнение");
    await user.click(within(form).getByRole("button", { name: ru["action.save"] }));

    expect(await within(form).findByText("Баланс заблокирован")).toBeInTheDocument();
    expect(within(form).getByLabelText(ru["clients.detail.balanceAmount"])).toHaveValue("50");
    expect(within(form).getByLabelText(ru["clients.detail.balanceNote"])).toHaveValue("Пополнение");
  });

  it("для клиента в архиве журнал виден, а корректировки нет", async () => {
    const api = balanceServer({ ...bob, state: "ARCHIVED" }, ["CAN_VIEW_CLIENT_BALANCE"]);
    const { panel } = await openBalance(api);

    expect(await within(panel).findByText("Долг за май")).toBeInTheDocument();
    expect(
      within(panel).queryByRole("button", { name: ru["clients.detail.balanceAdjust"] }),
    ).not.toBeInTheDocument();
  });
});
