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

/** Число дробных разрядов валюты — как `Currency.fractionDigits` в `shared`. */
const FRACTION_DIGITS: Readonly<Record<Currency, number>> = {
  RUB: 2,
  USD: 2,
  EUR: 2,
  KZT: 2,
  BYN: 2,
  UAH: 2,
};

/** Сколько минорных единиц в основной единице валюты [currency], в десятичных разрядах. */
export function fractionDigits(currency: Currency): number {
  return FRACTION_DIGITS[currency];
}
