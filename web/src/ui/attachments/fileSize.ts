import type { Translate } from "@/i18n/messages";
import type { ru } from "@/i18n/ru";

/** Единицы размера по возрастанию: каждая следующая в 1024 раза больше. */
const UNITS = [
  "attachments.size.b",
  "attachments.size.kb",
  "attachments.size.mb",
  "attachments.size.gb",
] as const;

/**
 * Размер [bytes] в самых крупных единицах, где число не меньше единицы, с одним знаком
 * после запятой: `245760` → «240 КБ». Подписи единиц и разделитель — из словаря языка [t].
 */
export function fileSize(t: Translate<typeof ru>, bytes: number): string {
  let value = Math.max(0, bytes);
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return t(UNITS[unit] ?? "attachments.size.b", { size: Math.round(value * 10) / 10 });
}
