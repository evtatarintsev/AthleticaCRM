import type { Currency } from "../api/generated/contracts";
import { CurrencySchema } from "../api/generated/contracts";

/** Символы валют; `Record` не даёт пропустить валюту, появившуюся в контракте. */
const SYMBOLS: Readonly<Record<Currency, string>> = {
  RUB: "₽",
  USD: "$",
  EUR: "€",
  KZT: "₸",
  BYN: "Br",
  UAH: "₴",
};

/** Валюты в порядке показа и их символы. */
export const CURRENCIES: readonly { readonly code: Currency; readonly symbol: string }[] =
  CurrencySchema.options.map((code) => ({ code, symbol: SYMBOLS[code] }));

/** Валюта по коду [code] или `undefined`, если код неизвестен. */
export function parseCurrency(code: string): Currency | undefined {
  const parsed = CurrencySchema.safeParse(code);
  return parsed.success ? parsed.data : undefined;
}
