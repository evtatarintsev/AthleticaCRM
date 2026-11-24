import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  CashJournalRequestSchema,
  SaleDetailResponseSchema,
  SaveCashAccountRequestSchema,
  SaveCashArticleRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { empty, json, openApp } from "@/test/app";
import { cashAccount, financeServer, rentArticle, receiptsArticle, testSale } from "@/test/finance";

const path = "/finance?from=2026-10-01&to=2026-10-31";

describe("раздел Финансы", () => {
  it("после возврата скрывает отмену оплаты в продаже и уже открытой операции", async () => {
    const paymentId = "0199a0b2-7c3e-7d2a-9f10-000000001501";
    let returned = false;
    let sale = SaleDetailResponseSchema.parse({
      ...testSale,
      paid: testSale.total,
      debt: cashAccount.balance,
      paymentState: "PAID",
      operations: [
        {
          id: paymentId,
          kind: "PAYMENT",
          occurredOn: testSale.soldOn,
          amount: testSale.total,
          cash: [
            {
              cashAccountId: cashAccount.id,
              cashAccountName: cashAccount.name,
              amount: testSale.total,
            },
          ],
          fromAdvance: cashAccount.balance,
          performedBy: testSale.performedBy,
          comment: null,
          reversed: false,
          canReverse: true,
        },
      ],
    });
    const api = financeServer({
      "sales/detail": () => json(sale),
      "sales/return": () => {
        returned = true;
        sale = {
          ...sale,
          returned: { minorUnits: 100000, currency: "RUB" },
          operations: sale.operations.map((operation) => ({ ...operation, canReverse: false })),
        };
        return empty();
      },
      "finance/journal": () =>
        json({
          entries: [
            {
              id: paymentId,
              kind: "PAYMENT",
              occurredOn: testSale.soldOn,
              cashAccountId: cashAccount.id,
              cashAccountName: cashAccount.name,
              articleId: receiptsArticle.id,
              articleName: receiptsArticle.name,
              amount: testSale.total,
              clientId: testSale.clientId,
              clientName: "Клиент",
              saleId: testSale.id,
              counterparty: null,
              comment: null,
              performedBy: testSale.performedBy,
              reversed: false,
              reversalReason: null,
              reversedBy: null,
              counterCashAccountId: null,
              counterCashAccountName: null,
              canReverse: !returned,
            },
          ],
          count: 1,
          receipts: testSale.total,
          expenses: cashAccount.balance,
        }),
    });
    openApp(path, api.fetch);
    const user = userEvent.setup();
    const table = await screen.findByRole("table");
    await user.click(
      await within(table).findByRole("button", { name: ru["finance.kind.PAYMENT"] }),
    );
    const operation = await screen.findByRole("dialog", { name: ru["finance.operations"] });
    expect(
      within(operation).getByRole("button", { name: ru["finance.cancelOperation"] }),
    ).toBeInTheDocument();
    await user.click(within(operation).getByRole("button", { name: ru["finance.sale"] }));
    const panel = await screen.findByRole("dialog", { name: ru["finance.sale"] });
    await user.click(await within(panel).findByRole("button", { name: ru["finance.return"] }));
    const form = await screen.findByRole("dialog", { name: ru["finance.return"] });
    await user.type(within(form).getByLabelText(ru["finance.amount"]), "1000");
    await user.type(within(form).getByLabelText(ru["finance.reason"]), "Возврат товара");
    await user.click(within(form).getByRole("button", { name: ru["action.save"] }));
    await waitFor(() => {
      expect(form).not.toBeInTheDocument();
    });
    expect(
      within(panel).queryByRole("button", { name: ru["finance.cancelOperation"] }),
    ).not.toBeInTheDocument();
    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(panel).not.toBeInTheDocument();
    });
    expect(
      within(operation).queryByRole("button", { name: ru["finance.cancelOperation"] }),
    ).not.toBeInTheDocument();
    expect(api.to("finance/journal").length).toBeGreaterThan(1);
  });

  it("без права скрывает навигацию и не загружает данные при прямом переходе", async () => {
    const api = financeServer({}, []);
    openApp(path, api.fetch);
    expect(await screen.findByText(ru["finance.noAccess"])).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: ru["nav.finance"] })).not.toBeInTheDocument();
    expect(api.to("finance/cash-accounts")).toHaveLength(0);
    expect(api.to("finance/journal")).toHaveLength(0);
  });

  it("показывает раздел только для чтения и сохраняет фильтр кассы в адресе", async () => {
    const api = financeServer({}, ["CAN_VIEW_FINANCE"]);
    const { history } = openApp(path, api.fetch);
    const user = userEvent.setup();
    expect(await screen.findByRole("link", { name: ru["nav.finance"] })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ru["finance.expense"] })).not.toBeInTheDocument();
    const accounts = await screen.findByLabelText(ru["finance.allAccounts"]);
    await screen.findByRole("option", { name: cashAccount.name });
    await user.selectOptions(accounts, cashAccount.id);
    await waitFor(() => {
      const last = api.to("finance/journal").at(-1);
      expect(CashJournalRequestSchema.parse(last?.body).cashAccountIds).toEqual([cashAccount.id]);
      expect(history.location.search).toContain(cashAccount.id);
    });
  });

  it("расход отправляет кассу статью сумму и обновляет журнал", async () => {
    const api = financeServer({ "finance/expense": () => empty() });
    openApp(path, api.fetch);
    const user = userEvent.setup();
    const create = await screen.findByRole("button", { name: ru["finance.expense"] });
    await waitFor(() => {
      expect(create).toBeEnabled();
    });
    await user.click(create);
    const dialog = await screen.findByRole("dialog", { name: ru["finance.expense"] });
    await user.type(within(dialog).getByLabelText(ru["finance.amount"]), "2500");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));
    await waitFor(() => {
      expect(dialog).not.toBeInTheDocument();
    });
    expect(api.to("finance/expense")[0]?.body).toMatchObject({
      cashAccountId: cashAccount.id,
      articleId: rentArticle.id,
      amount: { minorUnits: 250000, currency: "RUB" },
    });
    expect(api.to("finance/journal").length).toBeGreaterThan(1);
  });

  it("сумма статьи в ДДС открывает журнал с месяцем и статьёй", async () => {
    const api = financeServer({
      "finance/cash-flow": () =>
        json({
          months: ["2026-10"],
          rows: [
            {
              articleId: rentArticle.id,
              name: rentArticle.name,
              parentId: null,
              parentName: null,
              direction: "EXPENSE",
              kind: "article",
              amounts: [{ minorUnits: 300000, currency: "RUB" }],
              total: { minorUnits: 300000, currency: "RUB" },
            },
          ],
          opening: cashAccount.balance,
          closing: cashAccount.balance,
          receipts: cashAccount.balance,
          expenses: { minorUnits: 300000, currency: "RUB" },
        }),
    });
    const { history } = openApp(`${path}&tab=cashFlow`, api.fetch);
    const user = userEvent.setup();
    const amount = await screen.findByRole("button", { name: /3\s000,00/ });
    await user.click(amount);
    await waitFor(() => {
      expect(history.location.search).toContain("operations");
      expect(CashJournalRequestSchema.parse(api.to("finance/journal").at(-1)?.body)).toMatchObject({
        from: "2026-10-01",
        to: "2026-10-31",
        articleIds: [rentArticle.id],
      });
    });
  });

  it("создаёт кассу с начальным остатком и показывает новый итог", async () => {
    let accounts = [cashAccount];
    const api = financeServer({
      "finance/cash-accounts": () =>
        json({
          accounts,
          total: {
            ...cashAccount.balance,
            minorUnits: accounts.reduce((sum, a) => sum + a.balance.minorUnits, 0),
          },
        }),
      "finance/cash-accounts/save": ({ body }) => {
        const request = SaveCashAccountRequestSchema.parse(body);
        accounts = [
          ...accounts,
          {
            id: request.id,
            name: request.name,
            kind: request.kind,
            branchId: request.branchId ?? null,
            archived: false,
            balance: request.openingBalance ?? cashAccount.balance,
          },
        ];
        return empty();
      },
    });
    openApp(`${path}&tab=accounts`, api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["finance.createAccount"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["finance.createAccount"] });
    await user.type(within(dialog).getByLabelText(ru["finance.name"]), "Сейф");
    const opening = within(dialog).getByLabelText(ru["finance.openingBalance"]);
    await user.clear(opening);
    await user.type(opening, "15000");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));
    expect(await screen.findByText("Сейф")).toBeInTheDocument();
    expect(
      SaveCashAccountRequestSchema.parse(api.to("finance/cash-accounts/save")[0]?.body)
        .openingBalance?.minorUnits,
    ).toBe(1500000);
  });

  it("касса без названия не сохраняется и подсвечивает поле", async () => {
    const api = financeServer();
    openApp(`${path}&tab=accounts`, api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["finance.createAccount"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["finance.createAccount"] });
    await user.type(within(dialog).getByLabelText(ru["finance.name"]), "   ");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));
    expect(await within(dialog).findByText(ru["error.required"])).toBeInTheDocument();
    expect(within(dialog).getByLabelText(ru["finance.name"])).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.to("finance/cash-accounts/save")).toHaveLength(0);
  });

  it("системная статья не имеет действий, пользовательская группа сохраняется", async () => {
    const api = financeServer();
    openApp(`${path}&tab=articles`, api.fetch);
    const user = userEvent.setup();
    const system = await screen.findByText(receiptsArticle.name);
    const row = system.closest("li");
    if (row === null) {
      throw new Error("System article row is missing");
    }
    expect(within(row).getByText(ru["finance.system"])).toBeInTheDocument();
    expect(within(row).queryByRole("button")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: ru["finance.createArticle"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["finance.createArticle"] });
    await user.type(within(dialog).getByLabelText(ru["finance.name"]), "Помещение");
    await user.click(within(dialog).getByLabelText(ru["finance.isGroup"]));
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));
    await waitFor(() => {
      expect(api.to("finance/cash-articles/save")).toHaveLength(1);
    });
    expect(
      SaveCashArticleRequestSchema.parse(api.to("finance/cash-articles/save")[0]?.body),
    ).toMatchObject({ name: "Помещение", isGroup: true, parentId: null });
  });

  it("выгружает журнал с текущими фильтрами", async () => {
    const objectUrl = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:finance");
    vi.spyOn(URL, "revokeObjectURL").mockReturnValue(undefined);
    const api = financeServer({
      "finance/journal/export": () =>
        new Response("Date,Kind\\n2026-10-16,payment", { headers: { "Content-Type": "text/csv" } }),
    });
    openApp(path, api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["finance.export"] }));
    await waitFor(() => {
      expect(objectUrl).toHaveBeenCalled();
    });
    expect(CashJournalRequestSchema.parse(api.to("finance/journal/export")[0]?.body)).toMatchObject(
      { from: "2026-10-01", to: "2026-10-31" },
    );
  });
});
