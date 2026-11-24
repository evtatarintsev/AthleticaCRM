import { z } from "zod";
import type { I18n } from "@/i18n/context";

/** Схема обязательного текстового поля: пустая строка или одни пробелы — ошибка «обязательно». */
export function requiredText(t: I18n["t"]) {
  return z.string().trim().min(1, t("error.required"));
}
