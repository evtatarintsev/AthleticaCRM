import type { TariffPlanSchema } from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";

/** Условия тарифа [tariff] на языке [t]: число занятий или безлимит и срок действия. */
export function tariffTermsText(t: I18n["t"], tariff: TariffPlanSchema): string {
  const sessions =
    tariff.sessions === null
      ? t("tariffs.sessionsUnlimited")
      : t("tariffs.sessionsCount", { count: tariff.sessions });
  return `${sessions} · ${durationText(t, tariff)}`;
}

/** Срок действия тарифа [tariff] на языке [t]. */
function durationText(t: I18n["t"], tariff: TariffPlanSchema): string {
  switch (tariff.durationUnit) {
    case "DAYS":
      return t("tariffs.durationDays", { count: tariff.durationValue });
    case "MONTHS":
      return t("tariffs.durationMonths", { count: tariff.durationValue });
  }
}
