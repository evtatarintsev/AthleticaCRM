import type { TariffPlanSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { tariffTermsText } from "./tariffTerms";

/**
 * Строка тарифа [tariff]: название, условия и стоимость; архивный тариф отмечен пометкой.
 * Поведения не имеет — кнопку вокруг строки делает владелец списка (изменение или выбор).
 */
export function TariffRow({ tariff }: { tariff: TariffPlanSchema }) {
  const { t, format } = useI18n();
  return (
    <span className="flex items-center gap-3">
      <span className="min-w-0 flex-1 space-y-0.5">
        <span className="flex flex-wrap items-center gap-2">
          <span className="font-medium break-words">{tariff.name}</span>
          {tariff.archived && (
            <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
              {t("tariffs.archived")}
            </span>
          )}
        </span>
        <span className="block text-sm text-muted-foreground">{tariffTermsText(t, tariff)}</span>
      </span>
      <span className="shrink-0 font-semibold tabular-nums">{format.money(tariff.price)}</span>
    </span>
  );
}
