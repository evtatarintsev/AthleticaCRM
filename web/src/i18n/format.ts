import type { Instant, LocalDate, LocalTime, Money } from "@/api/generated/contracts";
import { fractionDigits } from "@/lib/currency";

/** Форматирование значений по правилам языка интерфейса. */
export interface Formatters {
  /** Число [value]. */
  number: (value: number) => string;
  /** Сумма [money] в её валюте, без потери точности минорных единиц. */
  money: (money: Money) => string;
  /** Календарная дата [date] без сдвига по часовому поясу. */
  date: (date: LocalDate) => string;
  /** Время суток [time]. */
  time: (time: LocalTime) => string;
  /** Момент [instant] в часовом поясе браузера. */
  dateTime: (instant: Instant) => string;
}

/** Форматтеры для языка [locale]. */
export function createFormatters(locale: string): Formatters {
  const numbers = new Intl.NumberFormat(locale);
  const dates = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  const times = new Intl.DateTimeFormat(locale, { timeStyle: "short", timeZone: "UTC" });
  const dateTimes = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" });
  return {
    number: (value) => numbers.format(value),
    money: (money) => {
      const digits = fractionDigits(money.currency);
      return new Intl.NumberFormat(locale, {
        style: "currency",
        currency: money.currency,
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
      }).format(toNumericLiteral(decimalString(money.minorUnits, digits)));
    },
    date: (date) => dates.format(Date.UTC(...dateParts(date))),
    time: (time) => times.format(Date.UTC(1970, 0, 1, ...timeParts(time))),
    dateTime: (instant) => dateTimes.format(new Date(instant)),
  };
}

/**
 * Десятичная запись суммы [minorUnits] с [digits] знаками после точки: `120050, 2` → `"1200.50"`.
 * `Intl.NumberFormat` принимает строку и форматирует её без перевода в `number`.
 */
function decimalString(minorUnits: number, digits: number): string {
  const sign = minorUnits < 0 ? "-" : "";
  const abs = Math.abs(minorUnits)
    .toString()
    .padStart(digits + 1, "0");
  const whole = abs.slice(0, abs.length - digits);
  const fraction = abs.slice(abs.length - digits);
  return digits === 0 ? `${sign}${whole}` : `${sign}${whole}.${fraction}`;
}

/** Десятичная строка [value] как числовой литерал для `Intl.NumberFormat`; не число — `NaN`. */
function toNumericLiteral(value: string): Intl.StringNumericLiteral | number {
  return isNumericLiteral(value) ? value : Number.NaN;
}

/** Истина, если [value] — десятичная запись числа, например `-1200.50`. */
function isNumericLiteral(value: string): value is `${number}` {
  return /^-?\d+(\.\d+)?$/.test(value);
}

/** Год, месяц (с нуля, как в `Date.UTC`) и день даты [date] вида `2025-01-31`. */
function dateParts(date: string): [number, number, number] {
  const [year = 1970, month = 1, day = 1] = date.split("-").map(Number);
  return [year, month - 1, day];
}

/** Часы и минуты времени [time] вида `18:30` или `18:30:00`. */
function timeParts(time: string): [number, number] {
  const [hours = 0, minutes = 0] = time.split(":").map(Number);
  return [hours, minutes];
}
