import type { Currency, Money } from "@/api/generated/contracts";
import { fractionDigits } from "./currency";

/**
 * Сумма из текста [text] в валюте [currency] без перевода в число с плавающей точкой:
 * `"1 200,5"` → 120050 минорных единиц. Разделитель дроби — точка или запятая, пробелы
 * игнорируются. Отрицательные суммы, лишние знаки после запятой и суммы вне безопасного
 * диапазона целых — `null`.
 */
export function parseMoney(text: string, currency: Currency): Money | null {
  const digits = fractionDigits(currency);
  const raw = text.replace(/\s/g, "").replace(",", ".");
  const match = /^(\d+)(?:\.(\d*))?$/.exec(raw);
  if (match === null) {
    return null;
  }
  const whole = match[1] ?? "";
  const fraction = match[2] ?? "";
  if (fraction.length > digits) {
    return null;
  }
  const minorUnits = Number(`${whole}${fraction.padEnd(digits, "0")}`);
  return Number.isSafeInteger(minorUnits) ? { minorUnits, currency } : null;
}

/**
 * Сумма [money] для поля ввода: без символа валюты и группировки разрядов,
 * нулевая дробная часть опускается — `120000` копеек → `"1200"`, `120050` → `"1200.50"`.
 */
export function moneyEditText(money: Money): string {
  const digits = fractionDigits(money.currency);
  const sign = money.minorUnits < 0 ? "-" : "";
  const abs = Math.abs(money.minorUnits)
    .toString()
    .padStart(digits + 1, "0");
  const whole = abs.slice(0, abs.length - digits);
  const fraction = abs.slice(abs.length - digits);
  return digits === 0 || /^0*$/.test(fraction) ? `${sign}${whole}` : `${sign}${whole}.${fraction}`;
}
