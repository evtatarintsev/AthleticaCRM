import type { Money } from "../api/generated/contracts";
import type { Locale } from "../i18n/locale";

/**
 * Сумма [money] для показа на языке [locale] с символом валюты; [signed] — со знаком «+» у
 * положительной суммы. Сервер отдаёт суммы в сотых долях валюты.
 */
export function formatMoney(money: Money, locale: Locale, signed = false): string {
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: money.currency,
    signDisplay: signed ? "exceptZero" : "auto",
  }).format(money.minorUnits / 100);
}
