import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  CreateSaleRequestSchema,
  ReturnSaleRequestSchema,
  SaleDetailResponseSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { empty, json, openApp, session } from "@/test/app";
import { cashAccount, financeClient, financeServer, testSale } from "@/test/finance";
import { financePeriod, FinanceSearchSchema } from "./financeSearch";

async function openSale(api: ReturnType<typeof financeServer>) {
  openApp(`/clients/${financeClient.id}`, api.fetch);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Открыть историю/ }));
  await user.click(await screen.findByRole("button", { name: ru["finance.kind.SALE"] }));
  const dialog = await screen.findByRole("dialog", { name: ru["finance.sale"] });
  await within(dialog).findByText("Разовое занятие · 1 × 4 500,00 ₽");
  return { user, dialog };
}

describe("продажи клиента", () => {
  it("дата продажи и период отчёта используют часовой пояс организации на границе месяца", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-12-01T01:30:00Z"));
    try {
      const timezone = "America/Los_Angeles";
      const api = financeServer({
        "auth/me": () => json({ ...session(), orgInfo: { ...session().orgInfo, timezone } }),
      });
      openApp(`/clients/${financeClient.id}`, api.fetch);
      const user = userEvent.setup();
      await user.click(await screen.findByRole("button", { name: ru["finance.sellService"] }));
      const dialog = await screen.findByRole("dialog", { name: ru["finance.sellService"] });
      expect(within(dialog).getByLabelText(ru["finance.date"])).toHaveValue("2026-11-30");
      expect(within(dialog).getByLabelText(ru["finance.date"])).toHaveAttribute(
        "max",
        "2026-11-30",
      );
      expect(financePeriod(FinanceSearchSchema.parse({}), timezone)).toEqual({
        from: "2026-11-01",
        to: "2026-11-30",
      });
    } finally {
      vi.useRealTimers();
    }
  });

  it("пустое название услуги отклоняется до запроса", async () => {
    const api = financeServer();
    openApp(`/clients/${financeClient.id}`, api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["finance.sellService"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["finance.sellService"] });
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));
    expect(within(dialog).getByLabelText(/^Название/)).toHaveAttribute("aria-invalid", "true");
    expect(api.to("sales/create")).toHaveLength(0);
  });

  it("услуга без права приёма оплат создаётся без оплаты", async () => {
    const api = financeServer({}, []);
    openApp(`/clients/${financeClient.id}`, api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["finance.sellService"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["finance.sellService"] });
    await user.type(within(dialog).getByLabelText(ru["finance.name"]), "Разовое занятие");
    await user.type(within(dialog).getByLabelText(ru["finance.price"]), "600");
    expect(within(dialog).queryByLabelText(ru["finance.payNow"])).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));
    await waitFor(() => {
      expect(dialog).not.toBeInTheDocument();
    });
    expect(CreateSaleRequestSchema.parse(api.to("sales/create")[0]?.body)).toMatchObject({
      items: [
        { name: "Разовое занятие", quantity: 1, unitPrice: { minorUnits: 60000, currency: "RUB" } },
      ],
      payment: null,
    });
  });

  it("частично оплаченная продажа открывается из истории и предлагает оплатить долг", async () => {
    const sale = SaleDetailResponseSchema.parse({
      ...testSale,
      paid: { minorUnits: 200000, currency: "RUB" },
      debt: { minorUnits: 250000, currency: "RUB" },
      paymentState: "PARTIAL",
      operations: [
        {
          id: "0199a0b2-7c3e-7d2a-9f10-000000001501",
          kind: "PAYMENT",
          occurredOn: testSale.soldOn,
          amount: { minorUnits: 200000, currency: "RUB" },
          cash: [
            {
              cashAccountId: cashAccount.id,
              cashAccountName: cashAccount.name,
              amount: { minorUnits: 200000, currency: "RUB" },
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
    const api = financeServer({ "sales/detail": () => json(sale) });
    const { dialog } = await openSale(api);
    expect(within(dialog).getByText(ru["finance.state.PARTIAL"])).toBeInTheDocument();
    expect(within(dialog).getByText("2 500,00 ₽")).toBeInTheDocument();
    expect(within(dialog).getByText("Наличные: 2 000,00 ₽")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: ru["finance.payment"] })).toBeInTheDocument();
  });

  it("возврат с выплатой отправляет сумму причину и кассу и обновляет продажу", async () => {
    let sale = SaleDetailResponseSchema.parse({
      ...testSale,
      paid: testSale.total,
      debt: { minorUnits: 0, currency: "RUB" },
      paymentState: "PAID",
    });
    const api = financeServer({
      "sales/detail": () => json(sale),
      "sales/return": ({ body }) => {
        const request = ReturnSaleRequestSchema.parse(body);
        sale = { ...sale, returned: request.amount };
        return empty();
      },
    });
    const { user, dialog } = await openSale(api);
    await user.click(within(dialog).getByRole("button", { name: ru["finance.return"] }));
    const form = await screen.findByRole("dialog", { name: ru["finance.return"] });
    await user.type(within(form).getByLabelText(ru["finance.amount"]), "1000");
    await user.type(within(form).getByLabelText(ru["finance.reason"]), "Возврат услуги");
    await user.click(within(form).getByLabelText(ru["finance.payOutReturn"]));
    await within(form).findByRole("option", { name: "Наличные" });
    await user.click(within(form).getByRole("button", { name: ru["action.save"] }));
    await waitFor(() => {
      expect(form).not.toBeInTheDocument();
    });
    expect(ReturnSaleRequestSchema.parse(api.to("sales/return")[0]?.body)).toMatchObject({
      amount: { minorUnits: 100000, currency: "RUB" },
      reason: "Возврат услуги",
    });
    expect(
      ReturnSaleRequestSchema.parse(api.to("sales/return")[0]?.body).cashAccountId,
    ).not.toBeNull();
    expect(await within(dialog).findByText("1 000,00 ₽")).toBeInTheDocument();
  });

  it("отказ в отмене сохраняет панель и причину после подтверждения", async () => {
    const api = financeServer({
      "sales/cancel": () =>
        json({ code: "MEMBERSHIP_HAS_DEBITS", message: "Есть списания", fields: null }, 409),
    });
    const { user, dialog } = await openSale(api);
    await user.click(within(dialog).getByRole("button", { name: ru["finance.cancelSale"] }));
    const form = await screen.findByRole("dialog", { name: ru["finance.cancelSale"] });
    await user.type(within(form).getByLabelText(ru["finance.reason"]), "Ошибка выдачи");
    await user.click(within(form).getByRole("button", { name: ru["finance.cancelOperation"] }));
    await user.click(await screen.findByRole("button", { name: ru["finance.confirm"] }));
    expect(await within(form).findByText("Есть списания")).toBeInTheDocument();
    expect(within(form).getByLabelText(ru["finance.reason"])).toHaveValue("Ошибка выдачи");
  });
});
