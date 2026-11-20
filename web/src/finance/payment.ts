import type {
  CashAccountSchema,
  CashPaymentInput,
  Money,
  PaymentInput,
} from "@/api/generated/contracts";
import { parseMoney } from "@/lib/money";
import { uuidv7 } from "@/lib/uuid";

/** null сохраняет автоматическую сумму, следующую за стоимостью продажи и списанием аванса. */
export interface PaymentDraft {
  readonly cash: readonly {
    readonly key: string;
    readonly cashAccountId: string;
    readonly amount: string | null;
  }[];
  readonly fromAdvance: string;
}
export function initialPayment(): PaymentDraft {
  return { cash: [{ key: uuidv7(), cashAccountId: "", amount: null }], fromAdvance: "" };
}

/** Разбирает денежные поля без float-арифметики; нулевая кассовая часть не отправляется. */
export function paymentInput(
  draft: PaymentDraft,
  accounts: readonly CashAccountSchema[],
  debt: Money,
  advance: Money,
): PaymentInput | null {
  const fromAdvance = parseMoney(draft.fromAdvance === "" ? "0" : draft.fromAdvance, debt.currency);
  if (
    fromAdvance === null ||
    fromAdvance.minorUnits > Math.max(0, advance.minorUnits) ||
    fromAdvance.minorUnits > debt.minorUnits
  )
    return null;
  const cash: CashPaymentInput[] = [];
  for (const row of draft.cash) {
    const amount =
      row.amount === null
        ? { ...debt, minorUnits: Math.max(0, debt.minorUnits - fromAdvance.minorUnits) }
        : parseMoney(row.amount, debt.currency);
    if (amount === null) return null;
    if (amount.minorUnits === 0) continue;
    const account = accounts.find(
      (a) =>
        a.id ===
        (row.cashAccountId === "" ? accounts.find((a) => !a.archived)?.id : row.cashAccountId),
    );
    if (account === undefined || account.archived) return null;
    cash.push({ cashAccountId: account.id, amount });
  }
  const total = cash.reduce((sum, row) => sum + row.amount.minorUnits, fromAdvance.minorUnits);
  return total > 0 && Number.isSafeInteger(total) ? { cash, fromAdvance } : null;
}
