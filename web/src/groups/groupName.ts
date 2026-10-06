import { z } from "zod";
import type { I18n } from "@/i18n/context";

/** Схема названия группы с сообщением на языке [t]; обрезается по краям, пустое запрещено. */
export function groupNameSchema(t: I18n["t"]) {
  return z.string().trim().min(1, t("error.required"));
}
