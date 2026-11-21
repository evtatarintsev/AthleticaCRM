import {
  CashAccountSchemaSchema,
  CashArticleSchemaSchema,
  ClientDetailResponseSchema,
  ClientLedgerResponseSchema,
  SaleDetailResponseSchema,
} from "@/api/generated/contracts";
import { appServer, empty, json, session, type FakeHandler } from "./app";

export const cashAccount = CashAccountSchemaSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000001001",
  name: "Наличные",
  kind: "CASH",
  branchId: null,
  archived: false,
  balance: { minorUnits: 0, currency: "RUB" },
});
export const bankAccount = CashAccountSchemaSchema.parse({
  ...cashAccount,
  id: "0199a0b2-7c3e-7d2a-9f10-000000001002",
  name: "Эквайринг",
  kind: "ACQUIRING",
});
export const rentArticle = CashArticleSchemaSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000001101",
  name: "Аренда",
  direction: "EXPENSE",
  parentId: null,
  isGroup: false,
  systemCode: null,
  archived: false,
});
export const receiptsArticle = CashArticleSchemaSchema.parse({
  ...rentArticle,
  id: "0199a0b2-7c3e-7d2a-9f10-000000001102",
  name: "Поступления от клиентов",
  direction: "INCOME",
  systemCode: "client_receipts",
});
export const financeClient = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000001201",
  name: "Иван Финансов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [],
  balance: { minorUnits: -450000, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});
export const testSale = SaleDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000001301",
  clientId: financeClient.id,
  soldOn: "2026-10-16",
  performedBy: { id: session().employeeId, name: "Админ" },
  items: [
    {
      id: "0199a0b2-7c3e-7d2a-9f10-000000001401",
      kind: "SERVICE",
      name: "Разовое занятие",
      quantity: 1,
      unitPrice: { minorUnits: 450000, currency: "RUB" },
      amount: { minorUnits: 450000, currency: "RUB" },
    },
  ],
  total: { minorUnits: 450000, currency: "RUB" },
  paid: { minorUnits: 0, currency: "RUB" },
  returned: { minorUnits: 0, currency: "RUB" },
  debt: { minorUnits: 450000, currency: "RUB" },
  paymentState: "UNPAID",
  cancelledAt: null,
  cancelReason: null,
  operations: [],
});
export const clientLedger = ClientLedgerResponseSchema.parse({
  balance: financeClient.balance,
  advance: { minorUnits: 0, currency: "RUB" },
  debt: testSale.debt,
  entries: [
    {
      id: "0199a0b2-7c3e-7d2a-9f10-000000001501",
      kind: "SALE",
      occurredOn: testSale.soldOn,
      createdAt: "2026-10-16T10:00:00Z",
      amount: financeClient.balance,
      balanceAfter: financeClient.balance,
      fromAdvance: { minorUnits: 0, currency: "RUB" },
      performedBy: testSale.performedBy,
      comment: null,
      saleId: testSale.id,
      reversed: false,
    },
  ],
  salesWithDebt: [{ saleId: testSale.id, soldOn: testSale.soldOn, debt: testSale.debt }],
});

/** Поддельный сервер финансов; отдельный тест переопределяет только нужные ответы. */
export function financeServer(
  overrides: Readonly<Record<string, FakeHandler>> = {},
  permissions: readonly string[] = [
    "CAN_VIEW_FINANCE",
    "CAN_MANAGE_FINANCE",
    "CAN_ACCEPT_PAYMENTS",
    "CAN_VIEW_CLIENT_BALANCE",
  ],
) {
  return appServer({
    "auth/me": () => json(session(undefined, undefined, undefined, permissions)),
    "finance/cash-accounts": () =>
      json({ accounts: [cashAccount, bankAccount], total: cashAccount.balance }),
    "finance/cash-articles": () => json({ articles: [rentArticle, receiptsArticle] }),
    "finance/client-ledger": () => json(clientLedger),
    "finance/client-payment-options": () => json(clientLedger),
    "finance/journal": () =>
      json({ entries: [], count: 0, receipts: cashAccount.balance, expenses: cashAccount.balance }),
    "finance/cash-flow": () =>
      json({
        months: ["2026-10"],
        rows: [],
        opening: cashAccount.balance,
        closing: cashAccount.balance,
        receipts: cashAccount.balance,
        expenses: cashAccount.balance,
      }),
    "finance/cash-accounts/save": () => empty(),
    "finance/cash-articles/save": () => empty(),
    "sales/create": () => empty(),
    "sales/detail": () => json(testSale),
    "sales/pay": () => empty(),
    "sales/return": () => empty(),
    "clients/detail": () => json(financeClient),
    "memberships/list": () => json({ memberships: [] }),
    "clients/notes/list": () => json({ notes: [] }),
    "custom-fields/list": () => json([]),
    "employees/list": () => json({ employees: [], total: 0 }),
    ...overrides,
  });
}
